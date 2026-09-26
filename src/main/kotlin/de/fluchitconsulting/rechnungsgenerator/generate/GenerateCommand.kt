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
import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.LuaLatexTypesetter
import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.TypesetterFactory
import de.fluchitconsulting.rechnungsgenerator.toolchain.BuiltInPdfAValidator
import de.fluchitconsulting.rechnungsgenerator.toolchain.ExternalPdfAValidator
import de.fluchitconsulting.rechnungsgenerator.toolchain.ExternalTool
import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import java.io.IOException
import java.nio.file.FileSystemException
import kotlin.io.path.copyTo
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.isSameFileAs
import kotlin.io.path.nameWithoutExtension

/**
 * `rechnungsgenerator erzeuge`: aus einer Rechnungsdatei ein geprüftes PDF.
 *
 * Das Kommando bekommt den Typesetter gereicht, statt ihn sich zu nehmen –
 * so lässt sich alles prüfen, was danach kommt: das Kopieren der Datei, die
 * Reihenfolge der Ausgaben, der Exitcode bei ungültigem PDF/A und das
 * Aufräumen des Arbeitsverzeichnisses. Im Betrieb steht dort LuaLaTeX, im Test
 * ein vorbereitetes Ergebnis. Ebenso der [PdfViewer] für `--oeffnen`: Im Test
 * öffnet sich kein Fenster.
 *
 * Gereicht wird eine [TypesetterFactory] und kein fertiger Typesetter, weil
 * der Prüfer erst mit dem Schalter feststeht. Dadurch lässt sich auch die Wahl
 * selbst prüfen, ohne dass ein PDF entsteht: Der Test sieht, welchen Prüfer
 * das Kommando übergibt.
 */
class GenerateCommand(
    private val typesetterFor: TypesetterFactory = ::LuaLatexTypesetter,
    private val viewer: PdfViewer = SystemPdfViewer,
) : CoreCliktCommand(name = "erzeuge") {
    override fun help(context: Context) =
        "Erzeugt aus einer YAML-Rechnung ein geprüftes PDF/A-1b, mit --zugferd eine E-Rechnung als PDF/A-3u."

    private val invoiceFile by argument("rechnung.yaml", help = "Die Rechnungsdatei").path(mustExist = true, canBeDir = false)

    // Ohne Angabe wird gesucht, und zwar von der Rechnungsdatei aus (ADR-0015).
    // Eine Vorgabe wie `stammdaten.yaml` im Arbeitsverzeichnis gibt es nicht:
    // Sie trüge nur, solange man dort steht, wo die Datei liegt.
    private val configFile by option(
        "-s",
        "--stammdaten",
        help = "Stammdaten des Rechnungsstellers; ohne Angabe aus der Wurzel über der Rechnungsdatei",
    ).path(mustExist = true, canBeDir = false)

    // Relativ zum Arbeitsverzeichnis wie -s: Ein getippter Pfad meint, was die
    // Shell darunter versteht. Nur die Vorgabe hängt an der Rechnungsdatei.
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

    // Wohin die Rechnung geschrieben wird, dass eine vorhandene nicht still
    // ersetzt wird und dass nur ein gültiges PDF/A ans Ziel kommt, sind
    // Regeln der Rechnung und keine Bedienung. Trotzdem stehen sie hier: Es
    // gibt genau einen Weg zu einer Rechnungsdatei, ein Ablauf ohne Clikt
    // hätte keinen zweiten Aufrufer, und prüfbar sind sie über den gereichten
    // Typesetter auch so. Heraus müssen sie mit dem zweiten Weg, sonst
    // stünden sie zweimal da oder zwischen zwei Zweigen (#359); das Urteil
    // über das PDF/A zöge dann mit. --zugferd ist kein solcher Weg: Der
    // Schalter ändert nur die Quellen und mit ihnen das Profil, nach dem
    // geurteilt wird, und das Ziel bleibt dasselbe (ADR-0023).
    //
    // Fehler beim Lesen, der Werkzeuge und die Ablehnung gehen durch: Zur
    // Meldung ohne Stacktrace macht sie execute(), für alle Kommandos an einer
    // Stelle (#119). Eine IOException fängt nur, wer weiß, was dabei
    // geschrieben werden sollte – ein catch um alles hieße jede
    // "nicht geschrieben", auch den Start eines Werkzeugs (#300).
    override fun run() {
        // Der Prüfer steht mit dem Schalter fest; eingebaut ist die Vorgabe
        // (ADR-0014). Extern heißt: ein getrennt installiertes, getrennt
        // versioniertes Programm urteilt – fehlt es, bricht checkAvailable ab.
        val validator = if (externalPdfACheck) ExternalPdfAValidator(ExternalTool) else BuiltInPdfAValidator
        val typesetter = typesetterFor(validator)

        // Erst die Werkzeuge prüfen: Ein Abbruch mitten im Satz wäre schwerer zu deuten.
        typesetter.checkAvailable()

        // Mit -s wird nicht gesucht; ohne -s gibt es nur die Suche.
        val config = YamlLoader.loadSellerConfig(configFile ?: Root.of(invoiceFile).sellerConfig)
        val invoice = YamlLoader.loadInvoice(invoiceFile)

        // Gesetzt wird nur eine angenommene Rechnung (ADR-0007) – der Renderer
        // nimmt keine andere. Alle Abbrüche auf einmal: Wer fünf Fehler in der
        // Datei hat, soll nicht fünfmal starten müssen, um sie zu finden (#49).
        val acceptedInvoice =
            when (val verdict = AcceptedInvoice.check(CalculatedInvoice(invoice, config))) {
                is Verdict.Accepted -> verdict.invoice
                is Verdict.Rejected -> throw RejectedException(verdict.aborts)
            }
        val einvoice = if (zugferd) CiiWriter.write(acceptedInvoice).toByteArray() else null

        // Ob die Nummer einen Jobnamen ergibt, hat die Prüfung schon gesagt
        // (#150); eine angenommene Rechnung hat immer einen. Mit der Zieldatei
        // hat er nichts zu tun, beliebig ist er trotzdem nicht: Aus ihm bildet
        // pdfx die DocumentID des PDF (#297).
        val jobName = acceptedInvoice.fileBaseName
        // Neben der Rechnungsdatei und nach ihr benannt, nicht nach der Nummer
        // (ADR-0018): Wer die Datei benannt hat, hat den Namen schon
        // entschieden. resolveSibling hält einen relativen Pfad relativ, so
        // steht das Ziel in der Ausgabe, wie die Rechnungsdatei angegeben wurde.
        val target = outputFile ?: invoiceFile.resolveSibling("${invoiceFile.nameWithoutExtension}.pdf")
        // Vor dem Überschreibschutz, denn --ueberschreiben gilt einem Entwurf
        // und nicht der Eingabe. isSameFileAs statt eines Vergleichs der Pfade:
        // ./R.yaml, ein Symlink und auf macOS r.YAML sind dieselbe Datei.
        if (target.exists() && target.isSameFileAs(invoiceFile)) {
            throw CliktError(
                "Die Zieldatei $target ist die Rechnungsdatei selbst und wird nie ersetzt, auch nicht mit " +
                    "--ueberschreiben. Mit -o ein anderes Ziel angeben.",
            )
        }
        // Eine vorhandene Datei kann die verschickte Rechnung sein. Ersetzt wird
        // sie nur auf ausdrücklichen Wunsch – und geprüft wird vor dem Satz,
        // denn ein LuaLaTeX-Lauf, dessen Ergebnis nirgends hin darf, wäre vergeudet.
        if (target.exists() && !overwrite) {
            throw CliktError(
                "Die Datei $target gibt es schon. Eine verschickte Rechnung wird nicht ersetzt; " +
                    "zum Nachbessern eines Entwurfs --ueberschreiben angeben.",
            )
        }

        val rendered = InvoiceRenderer.render(acceptedInvoice)
        // Das Logo stammt aus einer Datei der Benutzerin oder des Benutzers, und
        // ein Fehler darin zeigt sich erst im Satz oder bei veraPDF (#198). Im
        // Protokoll heißt die Datei nur logo.tex – den eingetragenen Pfad
        // kennt allein dieses Kommando. Nur wenn die Vorlage das Logo auch
        // einbindet, sonst kann es an ihm nicht liegen.
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
                    // Die Quellen kommen vollständig vom Renderer (#202),
                    // statt dass das Kommando sie aus vier Stellen
                    // zusammensetzt; es sagt nur, wohin gesetzt wird.
                    val sources = einvoice?.let(rendered.sources::withEInvoice) ?: rendered.sources
                    typesetter.typeset(workingDir = workDir, jobName = jobName, sources = sources)
                } catch (e: ToolchainException) {
                    throw ToolchainException(e.message + logoNote, e)
                }
            // Ein ungültiges PDF/A ist kein Erfolg: Exitcode 1, damit ein Skript
            // ringsherum es merkt. Und es kommt nicht ans Ziel – auch nicht mit
            // --ueberschreiben, das einer gültigen Rechnung gilt.
            //
            // Es reist als ToolchainException, obwohl der Prüfer gearbeitet
            // hat: execute() übersetzt sie schon, und eine eigene Ausnahme
            // änderte weder Text noch Exitcode. Die passende Form, ein
            // Ergebnis, über das der Aufrufer mit when entscheidet, entsteht
            // erst mit dem Ablauf, den der Kommentar über run erwartet.
            if (!typesetPdf.validation.valid) {
                val inspectionNote =
                    if (keepWorkFiles) {
                        "Das PDF liegt zum Ansehen in $workDir."
                    } else {
                        "Mit --arbeitsdateien-behalten bleibt es zum Ansehen liegen."
                    }
                val profile = typesetPdf.validation.profile.label
                throw ToolchainException(
                    "Das erzeugte PDF ist kein gültiges $profile und wurde nicht nach $target geschrieben.\n" +
                        "${typesetPdf.validation.describe()}\n$inspectionNote$logoNote",
                )
            }
            // Derselbe Wortlaut wie bei einrichten, gehalten von einem Test, der
            // beide vergleicht (#327). Ein gemeinsamer Typ wie
            // InputFileException fände keinen Ort: generate und setup kennen
            // einander nicht, und im Kern wird nichts geschrieben. Für zwei
            // Zeilen ist die Abschrift billiger als eine Kante, die nur für
            // sie da wäre.
            //
            // Eine FileSystemException nennt in message nur den Pfad, was sie
            // darüber hinaus weiß, steht in reason – der Pfad stünde sonst
            // zweimal da.
            try {
                typesetPdf.pdf.copyTo(target, overwrite = overwrite)
            } catch (e: IOException) {
                val reason = if (e is FileSystemException) e.reason else e.message
                throw CliktError("Die Datei $target konnte nicht geschrieben werden" + (reason?.let { ": $it" } ?: "."))
            }

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
