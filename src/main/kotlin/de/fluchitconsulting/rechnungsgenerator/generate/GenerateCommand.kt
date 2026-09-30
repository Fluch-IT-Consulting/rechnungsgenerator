package de.fluchitconsulting.rechnungsgenerator.generate

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import de.fluchitconsulting.rechnungsgenerator.core.accepted.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.accepted.Verdict
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Root
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.core.rules.RejectedException
import de.fluchitconsulting.rechnungsgenerator.generate.einvoice.CiiWriter
import de.fluchitconsulting.rechnungsgenerator.generate.render.InvoiceRenderer
import de.fluchitconsulting.rechnungsgenerator.generate.typesetting.InvalidEInvoiceException
import de.fluchitconsulting.rechnungsgenerator.generate.typesetting.LuaLatexTypesetter
import de.fluchitconsulting.rechnungsgenerator.generate.typesetting.TypesetterFactory
import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import de.fluchitconsulting.rechnungsgenerator.toolchain.process.ExternalTool
import de.fluchitconsulting.rechnungsgenerator.toolchain.validation.BuiltInPdfAValidator
import de.fluchitconsulting.rechnungsgenerator.toolchain.validation.ExternalPdfAValidator
import java.io.IOException
import java.nio.file.FileSystemException
import kotlin.io.path.copyTo
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.isSameFileAs
import kotlin.io.path.nameWithoutExtension

/** `rechnungsgenerator erzeuge`. Typesetter und Viewer kommen herein, damit ein Test alles danach ohne Werkzeuge prüft. */
class GenerateCommand(
    private val typesetterFor: TypesetterFactory = ::LuaLatexTypesetter,
    private val viewer: PdfViewer = SystemPdfViewer,
) : CoreCliktCommand(name = "erzeuge") {
    override fun help(context: Context) =
        "Erzeugt aus einer YAML-Rechnung ein geprüftes PDF/A-1b, mit --zugferd eine E-Rechnung als PDF/A-3u."

    private val invoiceFile by argument("rechnung.yaml", help = "Die Rechnungsdatei").path(mustExist = true, canBeDir = false)

    // Ohne Angabe von der Rechnungsdatei aus gesucht (ADR-0015), ohne Rückfall aufs Arbeitsverzeichnis.
    private val configFile by option(
        "-s",
        "--stammdaten",
        help = "Stammdaten des Rechnungsstellers; ohne Angabe aus der Wurzel über der Rechnungsdatei",
    ).path(mustExist = true, canBeDir = false)

    // Relativ zum Arbeitsverzeichnis wie -s; nur die Vorgabe hängt an der Rechnungsdatei (ADR-0018).
    private val outputFile by option(
        "-o",
        "--ausgabe",
        help = "Zieldatei; ohne Angabe neben der Rechnungsdatei, mit .pdf statt ihrer Endung",
    ).path(canBeDir = false)

    private val keepWorkFiles by option("--arbeitsdateien-behalten", help = "Das .tex und die LaTeX-Hilfsdateien nicht löschen")
        .flag()

    private val overwrite by option("--ueberschreiben", help = "Eine vorhandene Zieldatei ersetzen – zum Nachbessern eines Entwurfs")
        .flag()

    private val openAfterwards by option("--oeffnen", help = "Die fertige Rechnung im PDF-Viewer des Systems öffnen")
        .flag()

    private val zugferd by option(
        "--zugferd",
        help = "Eine E-Rechnung erzeugen: ZUGFeRD im Profil EXTENDED, das XML eingebettet in ein PDF/A-3u",
    ).flag()

    private val externalPdfACheck by option(
        "--externe-pdfa-pruefung",
        help = "Das installierte Programm verapdf prüfen lassen statt der eingebauten Bibliothek",
    ).flag()

    // Ziel, Überschreibschutz und das Urteil über das PDF/A sind Regeln der Rechnung und stehen trotzdem hier: Es
    // gibt nur einen Weg zu einer Rechnungsdatei. Mit einem zweiten müssen sie heraus (#359). Eine IOException
    // fängt nur, wer weiß, was geschrieben werden sollte (#300).
    override fun run() {
        val validator = if (externalPdfACheck) ExternalPdfAValidator(ExternalTool) else BuiltInPdfAValidator
        val typesetter = typesetterFor(validator)

        // Erst die Werkzeuge prüfen: Ein Abbruch mitten im Satz wäre schwerer zu deuten.
        typesetter.checkAvailable()

        val config = YamlLoader.loadSellerConfig(configFile ?: Root.of(invoiceFile).sellerConfig)
        val invoice = YamlLoader.loadInvoice(invoiceFile)

        // Alle Abbrüche auf einmal: Wer fünf Fehler hat, soll nicht fünfmal starten (#49).
        val acceptedInvoice =
            when (val verdict = AcceptedInvoice.check(CalculatedInvoice(invoice, config))) {
                is Verdict.Accepted -> verdict.invoice
                is Verdict.Rejected -> throw RejectedException(verdict.aborts)
            }
        val einvoice = if (zugferd) CiiWriter.write(acceptedInvoice).toByteArray() else null

        // Eine angenommene Rechnung hat immer einen Jobnamen (#150); aus ihm bildet pdfx die DocumentID (#297).
        val jobName = acceptedInvoice.fileBaseName
        // resolveSibling hält einen relativen Pfad relativ, so nennt die Ausgabe das Ziel wie die Eingabe (ADR-0018).
        val target = outputFile ?: invoiceFile.resolveSibling("${invoiceFile.nameWithoutExtension}.pdf")
        // Vor dem Überschreibschutz. isSameFileAs statt Pfadvergleich: ./R.yaml, ein Symlink und r.YAML auf macOS.
        if (target.exists() && target.isSameFileAs(invoiceFile)) {
            throw CliktError(
                "Die Zieldatei $target ist die Rechnungsdatei selbst und wird nie ersetzt, auch nicht mit " +
                    "--ueberschreiben. Mit -o ein anderes Ziel angeben.",
            )
        }
        // Geprüft vor dem Satz: Ein Lauf, dessen Ergebnis nirgends hin darf, wäre vergeudet.
        if (target.exists() && !overwrite) {
            throw CliktError(
                "Die Datei $target gibt es schon. Eine verschickte Rechnung wird nicht ersetzt; " +
                    "zum Nachbessern eines Entwurfs --ueberschreiben angeben.",
            )
        }

        val rendered = InvoiceRenderer.render(acceptedInvoice)
        // Im Protokoll heißt das Logo nur logo.tex; den eingetragenen Pfad kennt allein dieses Kommando (#198).
        val logoNote =
            config.logo
                ?.takeIf { rendered.includesLogo }
                ?.let { "\n\nDie Stammdaten nennen ein Logo (${it.source}); ein Fehler darin bricht den Satz ebenfalls ab." }
                .orEmpty()

        val workDir =
            try {
                createTempDirectory("rechnungsgenerator")
            } catch (e: IOException) {
                throw CliktError("Das Arbeitsverzeichnis für den Satz ließ sich nicht anlegen: ${e.message}")
            }
        try {
            val typesetPdf =
                try {
                    val sources = einvoice?.let(rendered.sources::withEInvoice) ?: rendered.sources
                    typesetter.typeset(workingDir = workDir, jobName = jobName, sources = sources)
                } catch (e: InvalidEInvoiceException) {
                    // Am XML kann das Logo nicht schuld sein (#386).
                    throw e
                } catch (e: ToolchainException) {
                    throw ToolchainException(e.message + logoNote, e)
                }
            // Ein ungültiges PDF/A kommt nicht ans Ziel, auch nicht mit --ueberschreiben, und endet mit Exitcode 1.
            if (!typesetPdf.validation.valid) {
                val inspectionNote =
                    if (keepWorkFiles) {
                        // Die Datei und nicht das Verzeichnis: Mit E-Rechnung liegt dort auch das gültige PDF/A-1b (#390).
                        "Das PDF liegt zum Ansehen unter ${typesetPdf.pdf}."
                    } else {
                        "Mit --arbeitsdateien-behalten bleibt es zum Ansehen liegen."
                    }
                val profile = typesetPdf.validation.profile.label
                throw ToolchainException(
                    "Das erzeugte PDF ist kein gültiges $profile und wurde nicht nach $target geschrieben.\n" +
                        "${typesetPdf.validation.describe()}\n$inspectionNote$logoNote",
                )
            }
            // Derselbe Wortlaut wie bei einrichten, gehalten von einem Test (#327). Den Grund trägt eine
            // FileSystemException in reason, message nennt nur den Pfad.
            try {
                typesetPdf.pdf.copyTo(target, overwrite = overwrite)
            } catch (e: IOException) {
                val reason = if (e is FileSystemException) e.reason else e.message
                throw CliktError("Die Datei $target konnte nicht geschrieben werden" + (reason?.let { ": $it" } ?: "."))
            }

            echo("$target")
            echo(typesetPdf.validation.describe())
            // Hinweise nach dem Ergebnis, damit sie nicht wie ein Fehler wirken.
            acceptedInvoice.hints.forEach { echo("Hinweis: ${it.describe()}") }
            if (keepWorkFiles) echo("Arbeitsdateien: $workDir")
            // Zuletzt und nur für ein gültiges PDF am Ziel. Ein Fehlschlag ist ein Hinweis, der Exitcode bleibt 0.
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
