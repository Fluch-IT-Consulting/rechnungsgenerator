package de.fluchitconsulting.rechnungsgenerator.prepayments

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import de.fluchitconsulting.rechnungsgenerator.core.model.Root
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.core.rules.RejectedException
import de.fluchitconsulting.rechnungsgenerator.core.values.Currency
import kotlin.io.path.name

/**
 * `rechnungsgenerator abschlaege`: aus Abschlagsrechnungen den `abschlaege`-Block
 * einer Schlussrechnung (ADR-0003).
 *
 * Das Kommando liest nur und schreibt auf stdout; eingefügt wird von Hand oder
 * per `>>`. Die Regeln stehen in [prepaymentsOf], die Schreibweise in
 * [PrepaymentBlock] – hier kommen beide mit echten Dateien zusammen.
 *
 * Fehler beim Lesen und die Ablehnung wirft es nur durch; zur Meldung auf
 * stderr macht sie `execute()`, für alle Kommandos an einer Stelle (#119).
 */
class PrepaymentsCommand : CoreCliktCommand(name = "abschlaege") {
    override fun help(context: Context) = "Bereitet aus Abschlagsrechnungen den abschlaege-Block einer Schlussrechnung vor."

    private val invoiceFiles by argument(
        "abschlagsrechnung.yaml",
        help = "Die Abschlagsrechnungen, deren Abschläge abgesetzt werden",
    ).path(mustExist = true, canBeDir = false)
        .multiple(required = true)

    private val configFile by option(
        "-s",
        "--stammdaten",
        help = "Stammdaten des Rechnungsstellers; ohne Angabe aus der gemeinsamen Wurzel über den Rechnungsdateien",
    ).path(mustExist = true, canBeDir = false)

    override fun run() {
        // Gesucht wird je Datei (ADR-0015), und verschiedene Wurzeln brechen
        // ab: Abschläge zweier Rechnungssteller in einem Block sind fachlich
        // sinnlos, und "die erste gewinnt" verschleierte das nur.
        val config = YamlLoader.loadSellerConfig(configFile ?: Root.of(invoiceFiles).sellerConfig)
        // Einzeln geladen, weil jede Datei ihre eigene Meldung verdient:
        // kaml sagt nur, welcher Schlüssel falsch ist, nicht wo. Den
        // Dateinamen setzt das Lesen selbst davor (#52). Der Name reist
        // von hier an mit der Rechnung: Er soll am Ende über ihrem
        // Abschlag stehen, aber nie der Pfad – der wechselt, sobald die
        // Datei in einen Jahresordner wandert.
        val prepaymentInvoices = invoiceFiles.map { NamedInvoice(it.name, YamlLoader.loadInvoice(it)) }

        // Alles oder nichts (ADR-0003), und dann alle Gründe auf einmal: Wer
        // fünf Dateien übergibt, soll nicht fünfmal starten müssen. Ob es
        // alles ist, sagt der Typ (#118): Einträge gibt es nur, wenn kein
        // Befund entstand. Und erst ganz am Ende und in einem Stück auf
        // stdout – bis hierher kann jede Datei noch scheitern, und dann soll
        // dort gar nichts stehen. Ein halber Block fiele keiner Regel der
        // Schlussrechnung auf.
        when (val collected = prepaymentsOf(prepaymentInvoices, config)) {
            is CollectedPrepayments.Complete -> {
                echo(PrepaymentBlock.render(collected.entries))
                // Der Block nennt die Währung nicht, die Schlussrechnung trägt
                // sie selbst (#409). Fehlt dort der Eintrag, gilt EUR, und kein
                // Befund sieht es: Die Abschlagsrechnungen liest nur dieses
                // Kommando (ADR-0001). Ein Hinweis nach dem Ergebnis und auf
                // stderr, damit `>>` nur den Block anhängt; bei EUR keiner,
                // denn das ist schon die Vorgabe.
                if (collected.currency != Currency.EUR) {
                    echo(
                        "Hinweis: Die Abschläge stehen in ${collected.currency}. Die Schlussrechnung braucht " +
                            "'waehrung: ${collected.currency}', sonst stünde sie in EUR.",
                        err = true,
                    )
                }
            }
            is CollectedPrepayments.Rejected -> {
                throw RejectedException(collected.aborts)
            }
        }
    }
}
