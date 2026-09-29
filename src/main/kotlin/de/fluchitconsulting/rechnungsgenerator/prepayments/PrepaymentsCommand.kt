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

/** `rechnungsgenerator abschlaege` (ADR-0003): liest nur und schreibt den Block auf stdout. */
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
        val config = YamlLoader.loadSellerConfig(configFile ?: Root.of(invoiceFiles).sellerConfig)
        // Der Name reist mit der Rechnung, nie der Pfad: Der wechselt, sobald die Datei in einen Jahresordner wandert.
        val prepaymentInvoices = invoiceFiles.map { NamedInvoice(it.name, YamlLoader.loadInvoice(it)) }

        // Erst ganz am Ende und in einem Stück auf stdout: Ein halber Block fiele keiner Regel der Schlussrechnung auf.
        when (val collected = prepaymentsOf(prepaymentInvoices, config)) {
            is CollectedPrepayments.Complete -> {
                echo(PrepaymentBlock.render(collected.entries))
                // Die Währung auf stderr, damit `>>` nur den Block anhängt: Fehlt sie in der Schlussrechnung, gilt still EUR.
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
