package de.fluchitconsulting.rechnung

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
import de.fluchitconsulting.rechnung.toolchain.LuaLatexTypesetter
import de.fluchitconsulting.rechnung.toolchain.Typesetter
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
class RechnungsgeneratorCommand(typesetter: Typesetter = LuaLatexTypesetter) :
    CoreCliktCommand(name = "rechnungsgenerator") {
    override val invokeWithoutSubcommand = true

    init {
        subcommands(GenerateCommand(typesetter), PrepaymentsCommand())
    }

    override fun help(context: Context) = "Erzeugt deutsche Rechnungen als geprüftes PDF/A-1b."

    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            throw PrintHelpMessage(currentContext, statusCode = 1)
        }
    }
}
