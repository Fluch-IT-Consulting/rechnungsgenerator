package de.fluchitconsulting.rechnungsgenerator.generate

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.generate.render.InvoiceRenderer
import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.LuaLatexTypesetter
import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.ToolchainException
import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.Typesetter
import de.fluchitconsulting.rechnungsgenerator.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.rules.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.rules.RejectedException
import de.fluchitconsulting.rechnungsgenerator.rules.Verdict
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists

/**
 * `rechnungsgenerator erzeuge`: aus einer Rechnungsdatei ein geprüftes PDF.
 *
 * Das Kommando bekommt den [Typesetter] gereicht, statt ihn sich zu nehmen –
 * so lässt sich alles prüfen, was danach kommt: das Kopieren der Datei, die
 * Reihenfolge der Ausgaben, der Exitcode bei ungültigem PDF/A und das
 * Aufräumen des Arbeitsverzeichnisses. Im Betrieb steht dort LuaLaTeX und
 * veraPDF, im Test ein vorbereitetes Ergebnis. Ebenso der [PdfViewer] für
 * `--oeffnen`: Im Test öffnet sich kein Fenster.
 */
class GenerateCommand(
    private val typesetter: Typesetter = LuaLatexTypesetter,
    private val viewer: PdfViewer = SystemPdfViewer,
) : CoreCliktCommand(name = "erzeuge") {
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

    private val overwrite by option("--ueberschreiben", help = "Eine vorhandene Zieldatei ersetzen – zum Nachbessern eines Entwurfs")
        .flag()

    private val openAfterwards by option("--oeffnen", help = "Die fertige Rechnung im PDF-Viewer des Systems öffnen")
        .flag()

    override fun run() {
        // Fehler beim Lesen, der Werkzeuge und die Ablehnung gehen durch: Zur
        // Meldung ohne Stacktrace macht sie execute(), für alle Kommandos an
        // einer Stelle (#119).
        try {
            erzeuge()
        } catch (e: IOException) {
            // Etwa ein Zielpfad, der nicht beschreibbar ist. Dieser Zweig
            // bleibt hier, weil nur dieses Kommando schreibt. Das Lesen der
            // Eingabedateien ist nicht dabei: YamlLoader wirft dafür
            // InputFileException, mit Datei und Grund (#52).
            throw CliktError("Die Datei konnte nicht geschrieben werden: ${e.message}")
        }
    }

    private fun erzeuge() {
        // Erst die Werkzeuge prüfen: Ein Abbruch mitten im Satz wäre schwerer zu deuten.
        typesetter.checkAvailable()

        val config = YamlLoader.loadSellerConfig(configFile)
        val invoice = YamlLoader.loadInvoice(invoiceFile)

        // Gesetzt wird nur eine angenommene Rechnung (ADR-0007) – der Renderer
        // nimmt keine andere. Alle Abbrüche auf einmal: Wer fünf Fehler in der
        // Datei hat, soll nicht fünfmal starten müssen, um sie zu finden (#49).
        val acceptedInvoice =
            when (val verdict = AcceptedInvoice.check(CalculatedInvoice(invoice, config))) {
                is Verdict.Accepted -> verdict.invoice
                is Verdict.Rejected -> throw RejectedException(verdict.aborts)
            }

        // Ob die Nummer einen Dateinamen ergibt, hat die Prüfung schon gesagt
        // (#150); eine angenommene Rechnung hat immer einen.
        val jobName = acceptedInvoice.fileBaseName
        val target = outputFile ?: Path.of("$jobName.pdf")
        // Eine vorhandene Datei kann die verschickte Rechnung sein. Ersetzt wird
        // sie nur auf ausdrücklichen Wunsch – und geprüft wird vor dem Satz,
        // denn ein LuaLaTeX-Lauf, dessen Ergebnis nirgends hin darf, wäre vergeudet.
        if (target.exists() && !overwrite) {
            throw CliktError(
                "Die Datei $target gibt es schon. Eine verschickte Rechnung wird nicht ersetzt; " +
                    "zum Nachbessern eines Entwurfs --ueberschreiben angeben.",
            )
        }

        val workDir = createTempDirectory("rechnungsgenerator")
        try {
            val typesetPdf =
                typesetter.typeset(
                    workingDir = workDir,
                    jobName = jobName,
                    texSource = InvoiceRenderer.render(acceptedInvoice),
                    xmpMetadata = InvoiceRenderer.renderMetadata(acceptedInvoice),
                    // Das Rechnungsdatum und nicht die Uhr: Eine neu erzeugte
                    // Rechnung soll nicht jünger aussehen als die verschickte.
                    creationDate = invoice.issueDate,
                )
            // Ein ungültiges PDF/A ist kein Erfolg: Exitcode 1, damit ein Skript
            // ringsherum es merkt. Und es kommt nicht ans Ziel – auch nicht mit
            // --ueberschreiben, das einer gültigen Rechnung gilt.
            if (!typesetPdf.validation.valid) {
                val inspectionNote =
                    if (keepWorkFiles) {
                        "Das PDF liegt zum Ansehen in $workDir."
                    } else {
                        "Mit --arbeitsdateien-behalten bleibt es zum Ansehen liegen."
                    }
                throw ToolchainException(
                    "Das erzeugte PDF ist kein gültiges PDF/A-1b und wurde nicht nach $target geschrieben.\n" +
                        "${typesetPdf.validation.describe()}\n$inspectionNote",
                )
            }
            typesetPdf.pdf.copyTo(target, overwrite = overwrite)

            echo("$target")
            echo(typesetPdf.validation.describe())
            // Hinweise nach dem Ergebnis, damit sie nicht wie ein Fehler wirken –
            // die Rechnung ist ja entstanden. Sie betreffen die eigene Ablage.
            acceptedInvoice.hints.forEach { echo("Hinweis: ${it.describe()}") }
            if (keepWorkFiles) echo("Arbeitsdateien: $workDir")
            // Zuletzt: Das Ergebnis steht vollständig da, bevor ein Fenster den
            // Blick wegzieht. Und nur hier, also nur für ein gültiges PDF am
            // Ziel. Ein Fehlschlag ist ein Hinweis und kein Abbruch – die
            // Rechnung ist entstanden, der Exitcode bleibt 0.
            if (openAfterwards) {
                when (val result = viewer.open(target)) {
                    ViewerResult.Opened -> Unit
                    is ViewerResult.Failed -> echo("Hinweis: Das PDF konnte nicht geöffnet werden: ${result.reason}")
                }
            }
        } finally {
            if (!keepWorkFiles) workDir.toFile().deleteRecursively()
        }
    }
}
