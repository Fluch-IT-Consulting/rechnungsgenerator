package de.fluchitconsulting.rechnung.toolchain

import java.nio.file.Path
import kotlin.io.path.absolutePathString

/** Ein von veraPDF gemeldeter Regelverstoß. */
data class PdfAViolation(
    val clause: String,
    val testNumber: String,
    val description: String,
    val errorMessage: String,
) {
    override fun toString(): String = "ISO 19005-1 $clause Test $testNumber: $description\n    $errorMessage"
}

data class ValidationResult(val valid: Boolean, val violations: List<PdfAViolation>) {
    fun describe(): String =
        if (valid) "PDF/A-1b: gültig" else "PDF/A-1b verletzt:\n" + violations.joinToString("\n") { "  $it" }
}

/**
 * Prüft ein PDF mit veraPDF gegen PDF/A-1b.
 *
 * veraPDF unterscheidet über den Exitcode: 0 = konform, 1 = nicht konform,
 * alles andere = das Werkzeug selbst kam nicht klar (etwa Datei nicht lesbar).
 * Nur der dritte Fall ist ein Fehler unsererseits; ein nicht konformes PDF ist
 * ein gültiges Prüfergebnis und wird als solches zurückgegeben.
 */
class PdfAValidator(private val runner: ProcessRunner) {
    private companion object {
        private const val PROGRAM = "verapdf"
        private const val EXIT_COMPLIANT = 0
        private const val EXIT_NOT_COMPLIANT = 1
    }

    fun validate(pdf: Path): ValidationResult {
        val result =
            runner.run(
                listOf(PROGRAM, "--format", "xml", "-f", "1b", pdf.absolutePathString()),
                pdf.parent,
            )
        if (result.exitCode !in listOf(EXIT_COMPLIANT, EXIT_NOT_COMPLIANT)) {
            throw ToolchainException("$PROGRAM brach mit Exitcode ${result.exitCode} ab:\n${result.output.take(800)}")
        }
        return ValidationResult(
            valid = result.exitCode == EXIT_COMPLIANT,
            violations = VeraPdfReport.parseViolations(VeraPdfReport.extractXml(result.output)),
        )
    }
}
