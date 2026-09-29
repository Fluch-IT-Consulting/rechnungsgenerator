package de.fluchitconsulting.rechnungsgenerator

import de.fluchitconsulting.rechnungsgenerator.generate.FakePdfViewer
import de.fluchitconsulting.rechnungsgenerator.generate.PdfViewer
import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.FakeTypesetter
import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.Typesetter
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.text.Charsets.UTF_8

data class CliResult(val exitCode: Int, val out: String, val err: String)

/** Derselbe Weg wie `main()`, aber mit dem [FakeTypesetter] statt LuaLaTeX. */
fun runCli(
    vararg args: String,
    typesetter: Typesetter = FakeTypesetter(),
    viewer: PdfViewer = FakePdfViewer(),
): CliResult {
    val out = ByteArrayOutputStream()
    val err = ByteArrayOutputStream()
    val exitCode =
        execute(arrayOf(*args), PrintStream(out, true, UTF_8), PrintStream(err, true, UTF_8), { typesetter }, viewer)
    return CliResult(exitCode, out.toString(UTF_8), err.toString(UTF_8))
}
