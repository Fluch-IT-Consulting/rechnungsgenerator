package de.fluchitconsulting.rechnungsgenerator

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption
import de.fluchitconsulting.rechnungsgenerator.core.model.InputFileException
import de.fluchitconsulting.rechnungsgenerator.core.rules.RejectedException
import de.fluchitconsulting.rechnungsgenerator.generate.GenerateCommand
import de.fluchitconsulting.rechnungsgenerator.generate.PdfViewer
import de.fluchitconsulting.rechnungsgenerator.generate.SystemPdfViewer
import de.fluchitconsulting.rechnungsgenerator.generate.typesetting.LuaLatexTypesetter
import de.fluchitconsulting.rechnungsgenerator.generate.typesetting.TypesetterFactory
import de.fluchitconsulting.rechnungsgenerator.prepayments.PrepaymentsCommand
import de.fluchitconsulting.rechnungsgenerator.setup.SetupCommand
import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import de.fluchitconsulting.rechnungsgenerator.xml.XmlCommand
import java.io.OutputStream
import java.io.PrintStream
import kotlin.system.exitProcess

// execute() bekommt die Kanäle gereicht und liefert den Exitcode, damit sich der ganze Weg ohne Prozess testet.
fun main(args: Array<String>) {
    exitProcess(execute(args, System.out, System.err))
}

/**
 * parse() statt Clikts main(), das auch im Fehlerfall mit 0 endet. Was nicht mit 0 endet, steht auf stderr; dafür
 * ein eigener Ausgeber, weil `clikt-core` den Parameter `err` ignoriert, und die Wahl des Kanals hier statt
 * `printError`, der die Hilfe ohne Unterkommando auf stdout ließe.
 */
fun execute(
    args: Array<String>,
    out: PrintStream,
    err: PrintStream,
    typesetter: TypesetterFactory = ::LuaLatexTypesetter,
    viewer: PdfViewer = SystemPdfViewer,
): Int {
    val command = RechnungsgeneratorCommand(typesetter, viewer, out)
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

/** Die Fachfehler aller Kommandos an einer Stelle zu einem CliktError, ohne Stacktrace (#119). */
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
 * Aus `version` in build.gradle.kts, als Ressource; nicht aus dem Manifest, das außerhalb des JARs `null` liefert
 * (#264). Fehlt sie, ist das Programm falsch gebaut und bricht laut ab. Der Name trägt das Programm, weil sie in
 * der Wurzel des Klassenpfads liegt.
 */
private fun programVersion(): String {
    val path = "/rechnungsgenerator.version"
    return checkNotNull(RechnungsgeneratorCommand::class.java.getResource(path)) {
        "$path fehlt – das Programm wurde ohne generateVersionResource gebaut"
    }.readText().trim()
}

/**
 * Fehlt das Unterkommando, wirft das Kommando die Hilfe selbst mit Exitcode 1: Clikts eigene `PrintHelpMessage`
 * endet mit 0. Deren Flag `error` bleibt weg, `clikt-core` liest es nicht aus.
 */
class RechnungsgeneratorCommand(
    typesetter: TypesetterFactory = ::LuaLatexTypesetter,
    viewer: PdfViewer = SystemPdfViewer,
    // Für xml, das Bytes schreibt und nicht Text über echo.
    standardOutput: OutputStream = System.out,
) : CoreCliktCommand(name = "rechnungsgenerator") {
    override val invokeWithoutSubcommand = true

    init {
        subcommands(GenerateCommand(typesetter, viewer), PrepaymentsCommand(), SetupCommand(), XmlCommand(standardOutput))
        // Eager: Clikt gibt die Fassung beim Parsen aus, und --version endet nicht als „kein Unterkommando“.
        versionOption(
            programVersion(),
            help = "Die Fassung des Programms ausgeben und beenden",
            message = { "rechnungsgenerator $it" },
        )
    }

    override fun help(context: Context) =
        "Erzeugt deutsche Rechnungen als geprüftes PDF/A-1b oder als E-Rechnung nach ZUGFeRD und liest das XML einer E-Rechnung aus."

    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            throw PrintHelpMessage(currentContext, statusCode = 1)
        }
    }
}
