package de.fluchitconsulting.rechnung

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
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
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name
import kotlin.system.exitProcess

// Einstiegspunkt. Eine Top-Level-Funktion `main` genügt in Kotlin – keine Klasse
// nötig. Die JVM sieht sie als statische Methode der generierten Klasse "MainKt"
// (daher der Eintrag mainClass in build.gradle.kts).
//
// Die eigentliche Arbeit steht in execute(): Sie bekommt die beiden Kanäle
// gereicht und liefert den Exitcode, statt den Prozess selbst zu beenden. So
// lässt sich der ganze Weg testen, ohne ein Programm zu starten.
fun main(args: Array<String>) {
    exitProcess(execute(args, System.out, System.err))
}

/**
 * Führt das Programm aus und liefert den Exitcode.
 *
 * Bewusst parse() statt des bequemeren main() von Clikt: Dessen main() fängt die
 * Fehler zwar ab und gibt sie aus, beendet den Prozess aber mit Code 0.
 * Nachgemessen: Ein Tippfehler in der YAML meldete "Exitcode: 0", ein Skript
 * ringsherum hätte den Fehlschlag also nicht bemerkt. Hier bestimmt der
 * statusCode des CliktError den Exitcode – 0 nur bei --help und Erfolg.
 *
 * Der Kanal folgt dem Exitcode: Was mit einem Exitcode ungleich 0 endet, steht
 * auf stderr, stdout trägt nur das Ergebnis. Dafür braucht es zwei Stellen:
 *
 *  - einen eigenen Ausgeber. Der von `clikt-core` wirft den Parameter `err`
 *    weg und schreibt alles auf stdout; erst die Vollvariante mit Mordant
 *    beachtet ihn.
 *  - die Wahl des Kanals für die Fehlermeldung hier unten, statt Clikts
 *    `printError` zu folgen. Das hielte die Hilfe ohne Unterkommando auf
 *    stdout, obwohl der Aufruf mit 1 endet.
 */
fun execute(
    args: Array<String>,
    out: PrintStream,
    err: PrintStream,
    typesetter: Typesetter = LuaLatexTypesetter,
): Int {
    val command = RechnungsgeneratorCommand(typesetter)
    command.context {
        // Unterkommandos erben den Ausgeber, er gilt also für das ganze Programm.
        echoMessage = { _, message, trailingNewline, toErr ->
            val stream = if (toErr) err else out
            if (trailingNewline) stream.println(message) else stream.print(message)
        }
    }
    return try {
        command.parse(args)
        0
    } catch (e: CliktError) {
        command.getFormattedHelp(e)?.let { command.echo(it, err = e.statusCode != 0) }
        e.statusCode
    }
}

/**
 * Das Programm selbst. Es verteilt nur an seine Unterkommandos.
 *
 * Fehlt das Unterkommando, zeigt Clikt von sich aus die Hilfe – und meldet
 * dabei Erfolg: `PrintHelpMessage` trägt den Exitcode 0, auch dort, wo Clikt
 * sie selbst wegen des fehlenden Unterkommandos wirft. Dieselbe Falle wie bei
 * `main()` oben, nur eine Ebene tiefer. Deshalb lässt sich das Kommando auch
 * ohne Unterkommando aufrufen und wirft die Hilfe selbst, mit Exitcode 1.
 *
 * Das Flag `error` der `PrintHelpMessage` bleibt bewusst weg: Es klingt, als
 * lenke es die Hilfe auf stderr, doch in `clikt-core` liest es niemand aus.
 */
class RechnungsgeneratorCommand(typesetter: Typesetter = LuaLatexTypesetter) :
    CoreCliktCommand(name = "rechnungsgenerator") {
    override val invokeWithoutSubcommand = true

    init {
        subcommands(GenerateCommand(typesetter))
    }

    override fun help(context: Context) = "Erzeugt deutsche Rechnungen als geprüftes PDF/A-1b."

    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            throw PrintHelpMessage(currentContext, statusCode = 1)
        }
    }
}

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
        } catch (e: SerializationException) {
            throw CliktError("Die Eingabedateien konnten nicht gelesen werden:\n${e.message}")
        } catch (e: IOException) {
            // Etwa ein Zielpfad, der nicht beschreibbar ist. Ohne diesen Zweig
            // beendet sich das Programm mit einem Stacktrace, was wie ein
            // Programmfehler aussieht statt wie ein falsches Argument.
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
            val totals = InvoiceCalculator.calculate(invoice, config)
            val gesetzt =
                typesetter.typeset(
                    workingDir = workDir,
                    jobName = jobNameFor(invoice),
                    texSource = InvoiceRenderer.render(invoice, config, totals),
                    xmpMetadata = InvoiceRenderer.renderMetadata(invoice, config),
                )
            val target = outputFile ?: Path.of(gesetzt.pdf.name)
            gesetzt.pdf.copyTo(target, overwrite = true)

            echo("$target")
            echo(gesetzt.validation.describe())
            // Hinweise nach dem Ergebnis, damit sie nicht wie ein Fehler wirken –
            // die Rechnung ist ja entstanden. Sie betreffen die eigene Ablage.
            totals.hints.forEach { echo("Hinweis: $it") }
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
