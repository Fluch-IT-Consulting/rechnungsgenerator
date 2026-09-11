package de.fluchitconsulting.rechnung

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import de.fluchitconsulting.rechnung.render.PrepaymentBlock
import kotlinx.serialization.SerializationException
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
            // Einzeln geladen, damit ein YAML-Fehler seine Datei nennt: kaml sagt
            // nur, welcher Schlüssel falsch ist, nicht wo.
            val abschlagsrechnungen =
                invoiceFiles.map { file ->
                    try {
                        YamlLoader.loadInvoice(file)
                    } catch (e: SerializationException) {
                        throw CliktError("Die Datei $file konnte nicht gelesen werden:\n${e.message}")
                    }
                }
            val abschlaege = prepaymentsOf(abschlagsrechnungen, config)
            // zip legt zwei Listen paarweise nebeneinander: die n-te Datei zum
            // n-ten Abschlag. Die Reihenfolge ist dieselbe, prepaymentsOf behält sie.
            val entries = invoiceFiles.zip(abschlaege) { file, abschlag -> PrepaymentBlock.Entry(file.name, abschlag) }

            // Erst ganz am Ende und in einem Stück: Bis hierher kann jede Datei
            // noch scheitern, und dann soll auf stdout gar nichts stehen. Ein
            // halber Block fiele keiner Regel der Schlussrechnung auf.
            echo(PrepaymentBlock.render(entries))
        } catch (e: InvoiceValidationException) {
            throw CliktError(e.message)
        } catch (e: SerializationException) {
            throw CliktError("Die Eingabedateien konnten nicht gelesen werden:\n${e.message}")
        }
    }
}
