package de.fluchitconsulting.rechnungsgenerator

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption
import de.fluchitconsulting.rechnungsgenerator.generate.GenerateCommand
import de.fluchitconsulting.rechnungsgenerator.generate.PdfViewer
import de.fluchitconsulting.rechnungsgenerator.generate.SystemPdfViewer
import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.LuaLatexTypesetter
import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.ToolchainException
import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.TypesetterFactory
import de.fluchitconsulting.rechnungsgenerator.model.InputFileException
import de.fluchitconsulting.rechnungsgenerator.prepayments.PrepaymentsCommand
import de.fluchitconsulting.rechnungsgenerator.rules.RejectedException
import java.io.PrintStream
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
 * auf stderr, stdout trägt nur das Ergebnis. Dafür braucht es drei Stellen:
 *
 *  - einen eigenen Ausgeber. Der von `clikt-core` wirft den Parameter `err`
 *    weg und schreibt alles auf stdout; erst die Vollvariante mit Mordant
 *    beachtet ihn.
 *  - die Übersetzung der Fachfehler in [parseReportingFailures], einmal für
 *    alle Kommandos.
 *  - die Wahl des Kanals für die Fehlermeldung hier unten, statt Clikts
 *    `printError` zu folgen. Das hielte die Hilfe ohne Unterkommando auf
 *    stdout, obwohl der Aufruf mit 1 endet.
 */
fun execute(
    args: Array<String>,
    out: PrintStream,
    err: PrintStream,
    typesetter: TypesetterFactory = ::LuaLatexTypesetter,
    // Wie der Typesetter eine Naht für den Test (#153): Nur so lässt sich
    // prüfen, dass ein gescheiterter Viewer mit Exitcode 0 endet und sein
    // Hinweis auf stdout steht. Vorher nahm das Kommando ihn, execute nicht –
    // der Exitcode wurde aus "keine Ausnahme" abgeleitet statt gelesen.
    viewer: PdfViewer = SystemPdfViewer,
): Int {
    val command = RechnungsgeneratorCommand(typesetter, viewer)
    command.context {
        // Unterkommandos erben den Ausgeber, er gilt also für das ganze Programm.
        echoMessage = { _, message, trailingNewline, toErr ->
            val stream = if (toErr) err else out
            if (trailingNewline) stream.println(message) else stream.print(message)
        }
    }
    return try {
        parseReportingFailures(command, args)
        0
    } catch (e: CliktError) {
        command.getFormattedHelp(e)?.let { command.echo(it, err = e.statusCode != 0) }
        e.statusCode
    }
}

/**
 * Macht aus den Fachfehlern der Kommandos einen CliktError: Clikt gibt dann die
 * Meldung aus und beendet mit Exitcode 1, ohne Stacktrace. Ein Tippfehler in der
 * YAML soll nicht wie ein Programmabsturz aussehen.
 *
 * Hier und nicht in jedem Kommando (#119): Vorher übersetzte jedes für sich, und
 * in #52 liefen die Fassungen auseinander – `abschlaege` fing den Lesefehler gar
 * nicht. Jedes Kommando, das dazukommt, bekommt die Übersetzung jetzt umsonst.
 * Was nur ein Kommando betrifft, bleibt dort, etwa das Schreiben der Zieldatei
 * in `erzeuge`.
 */
private fun parseReportingFailures(command: CoreCliktCommand, args: Array<String>) {
    try {
        command.parse(args)
    } catch (e: InputFileException) {
        throw CliktError(e.message)
    } catch (e: ToolchainException) {
        throw CliktError(e.message)
    } catch (e: RejectedException) {
        throw CliktError(e.message)
    }
}

/**
 * Die Fassung des Programms. Sie entsteht beim Bauen aus `version` in
 * build.gradle.kts und liegt als Ressource neben den Klassen.
 *
 * Gelesen wie die Vorlagen der Designs (`Template.of`): mit `checkNotNull` und
 * einer Meldung, die sagt, was fehlt. Fehlt die Datei, ist das Programm falsch
 * gebaut und nicht falsch bedient – dann soll es laut abbrechen und nicht
 * "unbekannt" ausgeben.
 *
 * Der Name trägt das Programm, weil die Ressource im Wurzelverzeichnis des
 * Klassenpfads liegt: Dort teilt sie sich den Raum mit jedem mitgelieferten
 * JAR.
 */
private fun programVersion(): String {
    val path = "/rechnungsgenerator.version"
    return checkNotNull(RechnungsgeneratorCommand::class.java.getResource(path)) {
        "$path fehlt – das Programm wurde ohne generateVersionResource gebaut"
    }.readText().trim()
}

/**
 * Das Programm selbst. Es verteilt nur an seine Unterkommandos; jedes davon
 * steht in einer eigenen Datei, benannt nach seiner Klasse.
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
class RechnungsgeneratorCommand(
    typesetter: TypesetterFactory = ::LuaLatexTypesetter,
    viewer: PdfViewer = SystemPdfViewer,
) : CoreCliktCommand(name = "rechnungsgenerator") {
    override val invokeWithoutSubcommand = true

    init {
        subcommands(GenerateCommand(typesetter, viewer), PrepaymentsCommand())
        // Eager: Clikt gibt die Fassung schon beim Parsen aus und wirft
        // PrintMessage mit Exitcode 0. Damit greift run() unten gar nicht erst,
        // und --version endet nicht als "kein Unterkommando" mit Code 1.
        versionOption(
            programVersion(),
            help = "Die Fassung des Programms ausgeben und beenden",
            message = { "rechnungsgenerator $it" },
        )
    }

    override fun help(context: Context) = "Erzeugt deutsche Rechnungen als geprüftes PDF/A-1b."

    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            throw PrintHelpMessage(currentContext, statusCode = 1)
        }
    }
}
