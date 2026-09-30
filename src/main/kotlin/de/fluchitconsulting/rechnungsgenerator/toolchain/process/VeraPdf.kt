package de.fluchitconsulting.rechnungsgenerator.toolchain.process

import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/** Das Urteil von `verapdf` und der XML-Bericht, der es begründet. */
data class VeraPdfReport(
    val compliant: Boolean,
    val xml: String,
)

/** Das Programm `verapdf`: Exitcode 0 heißt konform, 1 nicht konform, alles andere kam nicht klar. */
class VeraPdf(private val runner: ProcessRunner) {
    fun checkAvailable() = runner.requireAll(PROGRAM to "brew install verapdf")

    /** [flavour], wie `-f` es verlangt, etwa `1b`; ein `PdfAProfile` kennt dieses Paket nicht (ADR-0029). */
    fun validate(
        pdf: Path,
        flavour: String,
    ): VeraPdfReport {
        val result =
            runner.run(
                PROGRAM,
                listOf("--format", "xml", "-f", flavour, pdf.absolutePathString()),
                pdf.parent,
            )
        if (result.exitCode !in listOf(EXIT_COMPLIANT, EXIT_NOT_COMPLIANT)) {
            throw ToolchainException("$PROGRAM brach mit Exitcode ${result.exitCode} ab:\n${result.output.take(800)}")
        }
        return VeraPdfReport(compliant = result.exitCode == EXIT_COMPLIANT, xml = extractXml(result.standardOutput))
    }

    /** Nur eine Sicherung, das Protokoll steht auf stderr. Was mitten im Bericht stünde, fängt es nicht (#227). */
    private fun extractXml(output: String): String {
        val start = output.indexOf("<?xml")
        if (start < 0) throw ToolchainException("$PROGRAM lieferte keinen XML-Bericht:\n${output.take(800)}")
        return output.substring(start)
    }

    private companion object {
        private const val PROGRAM = "verapdf"
        private const val EXIT_COMPLIANT = 0
        private const val EXIT_NOT_COMPLIANT = 1
    }
}
