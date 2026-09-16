package de.fluchitconsulting.rechnungsgenerator

import de.fluchitconsulting.rechnungsgenerator.generate.FakePdfViewer
import de.fluchitconsulting.rechnungsgenerator.generate.PdfViewer
import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.FakeTypesetter
import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.Typesetter
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.text.Charsets.UTF_8

/** Was ein Aufruf des Programms hinterlässt: den Exitcode und die beiden Kanäle. */
data class CliResult(val exitCode: Int, val out: String, val err: String)

/**
 * Ruft das Programm über [execute] auf – derselbe Weg wie `main()`, nur ohne
 * einen Prozess zu starten. Statt LuaLaTeX und veraPDF steht der
 * [FakeTypesetter] dahinter, auf Wunsch einer mit anderem Ergebnis.
 */
fun runCli(
    vararg args: String,
    typesetter: Typesetter = FakeTypesetter(),
    viewer: PdfViewer = FakePdfViewer(),
): CliResult {
    val out = ByteArrayOutputStream()
    val err = ByteArrayOutputStream()
    val exitCode =
        execute(arrayOf(*args), PrintStream(out, true, UTF_8), PrintStream(err, true, UTF_8), typesetter, viewer)
    return CliResult(exitCode, out.toString(UTF_8), err.toString(UTF_8))
}
