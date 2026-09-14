package de.fluchitconsulting.rechnungsgenerator.prepayments

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import de.fluchitconsulting.rechnungsgenerator.model.InputFileException
import de.fluchitconsulting.rechnungsgenerator.model.YamlLoader
import java.nio.file.Path
import kotlin.io.path.name

/**
 * `rechnungsgenerator abschlaege`: aus Abschlagsrechnungen den `abschlaege`-Block
 * einer Schlussrechnung (ADR-0003).
 *
 * Das Kommando liest nur und schreibt auf stdout; eingefügt wird von Hand oder
 * per `>>`. Die Regeln stehen in [prepaymentsOf], die Schreibweise in
 * [PrepaymentBlock] – hier kommen beide mit echten Dateien zusammen.
 */
class PrepaymentsCommand : CoreCliktCommand(name = "abschlaege") {
    override fun help(context: Context) = "Bereitet aus Abschlagsrechnungen den abschlaege-Block einer Schlussrechnung vor."

    private val invoiceFiles by argument(
        "abschlagsrechnung.yaml",
        help = "Die Abschlagsrechnungen, deren Abschläge abgesetzt werden",
    ).path(mustExist = true, canBeDir = false)
        .multiple(required = true)

    private val configFile by option("-s", "--stammdaten", help = "Stammdaten des Rechnungsstellers")
        .path(mustExist = true, canBeDir = false)
        .default(Path.of("stammdaten.yaml"))

    override fun run() {
        try {
            val config = YamlLoader.loadSellerConfig(configFile)
            // Einzeln geladen, weil jede Datei ihre eigene Meldung verdient:
            // kaml sagt nur, welcher Schlüssel falsch ist, nicht wo. Den
            // Dateinamen setzt das Lesen selbst davor (#52). Der Name reist
            // von hier an mit der Rechnung: Er soll am Ende über ihrem
            // Abschlag stehen, aber nie der Pfad – der wechselt, sobald die
            // Datei in einen Jahresordner wandert.
            val abschlagsrechnungen = invoiceFiles.map { NamedInvoice(it.name, YamlLoader.loadInvoice(it)) }

            // Alles oder nichts (ADR-0003), und dann alle Gründe auf einmal: Wer
            // fünf Dateien übergibt, soll nicht fünfmal starten müssen. Ob es
            // alles ist, sagt der Typ (#118): Einträge gibt es nur, wenn kein
            // Befund entstand. Und erst ganz am Ende und in einem Stück auf
            // stdout – bis hierher kann jede Datei noch scheitern, und dann soll
            // dort gar nichts stehen. Ein halber Block fiele keiner Regel der
            // Schlussrechnung auf.
            when (val collected = prepaymentsOf(abschlagsrechnungen, config)) {
                is CollectedPrepayments.Complete -> echo(PrepaymentBlock.render(collected.entries))
                is CollectedPrepayments.Rejected -> throw CliktError(collected.aborts.joinToString("\n") { it.describe() })
            }
        } catch (e: InputFileException) {
            throw CliktError(e.message)
        }
    }
}
