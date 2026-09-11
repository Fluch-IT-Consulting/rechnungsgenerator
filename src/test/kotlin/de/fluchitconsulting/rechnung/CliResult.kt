package de.fluchitconsulting.rechnung

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.text.Charsets.UTF_8

/** Was ein Aufruf des Programms hinterlässt: den Exitcode und die beiden Kanäle. */
data class CliResult(val exitCode: Int, val out: String, val err: String)

/**
 * Ruft das Programm über [execute] auf – derselbe Weg wie `main()`, nur ohne
 * einen Prozess zu starten. Statt LuaLaTeX und veraPDF steht der
 * [FakeTypesetter] dahinter.
 */
fun runCli(vararg args: String): CliResult {
    val out = ByteArrayOutputStream()
    val err = ByteArrayOutputStream()
    val exitCode =
        execute(arrayOf(*args), PrintStream(out, true, UTF_8), PrintStream(err, true, UTF_8), FakeTypesetter())
    return CliResult(exitCode, out.toString(UTF_8), err.toString(UTF_8))
}
