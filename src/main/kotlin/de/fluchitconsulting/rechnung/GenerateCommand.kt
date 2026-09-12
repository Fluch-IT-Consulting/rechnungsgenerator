package de.fluchitconsulting.rechnung

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.render.InvoiceRenderer
import de.fluchitconsulting.rechnung.toolchain.LuaLatexTypesetter
import de.fluchitconsulting.rechnung.toolchain.ToolchainException
import de.fluchitconsulting.rechnung.toolchain.Typesetter
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name

/**
 * `rechnungsgenerator erzeuge`: aus einer Rechnungsdatei ein geprüftes PDF.
 *
 * Das Kommando bekommt den [Typesetter] gereicht, statt ihn sich zu nehmen –
 * so lässt sich alles prüfen, was danach kommt: das Kopieren der Datei, die
 * Reihenfolge der Ausgaben, der Exitcode bei ungültigem PDF/A und das
 * Aufräumen des Arbeitsverzeichnisses. Im Betrieb steht dort LuaLaTeX und
 * veraPDF, im Test ein vorbereitetes Ergebnis.
 */
class GenerateCommand(private val typesetter: Typesetter = LuaLatexTypesetter) : CoreCliktCommand(name = "erzeuge") {
    override fun help(context: Context) =
        "Erzeugt aus einer YAML-Rechnung ein geprüftes PDF/A-1b."

    private val invoiceFile by argument("rechnung.yaml", help = "Die Rechnungsdatei").path(mustExist = true, canBeDir = false)

    private val configFile by option("-s", "--stammdaten", help = "Stammdaten des Rechnungsstellers")
        .path(mustExist = true, canBeDir = false)
        .default(Path.of("stammdaten.yaml"))

    private val outputFile by option("-o", "--ausgabe", help = "Zieldatei; Vorgabe ist der Name aus der Rechnungsnummer")
        .path(canBeDir = false)

    private val keepWorkFiles by option("--arbeitsdateien-behalten", help = "Das .tex und die LaTeX-Hilfsdateien nicht löschen")
        .flag()

    override fun run() {
        // CliktError statt einer durchgereichten Exception: Clikt gibt die
        // Meldung aus und beendet mit Exitcode 1, ohne Stacktrace. Ein
        // Tippfehler in der YAML soll nicht wie ein Programmabsturz aussehen.
        try {
            erzeuge()
        } catch (e: ToolchainException) {
            throw CliktError(e.message)
        } catch (e: InvoiceValidationException) {
            throw CliktError(e.message)
        } catch (e: InputFileException) {
            throw CliktError(e.message)
        } catch (e: IOException) {
            // Etwa ein Zielpfad, der nicht beschreibbar ist. Ohne diesen Zweig
            // beendet sich das Programm mit einem Stacktrace, was wie ein
            // Programmfehler aussieht statt wie ein falsches Argument. Das
            // Lesen der Eingabedateien ist hier nicht mehr dabei: Es meldet
            // sich selbst, mit Datei und Grund (#52).
            throw CliktError("Die Datei konnte nicht geschrieben werden: ${e.message}")
        }
    }

    private fun erzeuge() {
        // Erst die Werkzeuge prüfen: Ein Abbruch mitten im Satz wäre schwerer zu deuten.
        typesetter.checkAvailable()

        val config = YamlLoader.loadSellerConfig(configFile)
        val invoice = YamlLoader.loadInvoice(invoiceFile)

        val workDir = createTempDirectory("rechnungsgenerator")
        try {
            val calculated = InvoiceCalculator.calculate(invoice, config)
            val gesetzt =
                typesetter.typeset(
                    workingDir = workDir,
                    jobName = jobNameFor(invoice),
                    texSource = InvoiceRenderer.render(calculated),
                    xmpMetadata = InvoiceRenderer.renderMetadata(calculated),
                )
            val target = outputFile ?: Path.of(gesetzt.pdf.name)
            gesetzt.pdf.copyTo(target, overwrite = true)

            echo("$target")
            echo(gesetzt.validation.describe())
            // Hinweise nach dem Ergebnis, damit sie nicht wie ein Fehler wirken –
            // die Rechnung ist ja entstanden. Sie betreffen die eigene Ablage.
            calculated.hints.forEach { echo("Hinweis: $it") }
            if (keepWorkFiles) echo("Arbeitsdateien: $workDir")
            // Ein ungültiges PDF/A ist kein Erfolg: Exitcode 1, damit ein Skript
            // ringsherum es merkt.
            if (!gesetzt.validation.valid) throw ToolchainException("Das erzeugte PDF ist kein gültiges PDF/A-1b.")
        } finally {
            if (!keepWorkFiles) workDir.toFile().deleteRecursively()
        }
    }

    /**
     * Rechnungsart und -nummer als Dateiname – "Abschlagsrechnung-R26-009".
     *
     * Im Ordner liegen die Dokumente zu einem Vorgang nebeneinander; welches
     * davon der Abschlag ist, soll man am Namen sehen und nicht erst nach dem
     * Öffnen. LuaLaTeX stolpert über Leerzeichen und Sonderzeichen im Jobnamen,
     * deshalb bleibt von der Nummer nur Unverfängliches übrig.
     *
     * Eine Nummer, von der nichts übrig bleibt, ist ein Fehler in der
     * Rechnungsdatei und keiner der Werkzeuge – daher
     * [InvoiceValidationException] und nicht ToolchainException.
     */
    private fun jobNameFor(invoice: Invoice): String {
        val safe = invoice.number.replace(Regex("[^A-Za-z0-9._-]"), "-").trim('-')
        if (safe.isEmpty()) {
            throw InvoiceValidationException("Die Rechnungsnummer '${invoice.number}' ergibt keinen Dateinamen.")
        }
        return "${invoice.type.documentName}-$safe"
    }
}
