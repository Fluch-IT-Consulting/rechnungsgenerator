package de.fluchitconsulting.rechnung.toolchain

import org.w3c.dom.Element
import java.nio.file.Path
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
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
            violations = parseViolations(extractXml(result.output)),
        )
    }

    /**
     * Schneidet weg, was vor dem XML steht.
     *
     * veraPDF ist selbst eine Java-Anwendung und schreibt JVM-Warnungen auf
     * stderr ("has been mutated reflectively ..."). Da der Aufruf stderr und
     * stdout zusammenführt, stehen die vor dem Bericht – der Parser bricht dann
     * mit "Content is not allowed in prolog" ab. Deshalb wird ab dem Prolog
     * gelesen.
     */
    private fun extractXml(output: String): String {
        val start = output.indexOf("<?xml")
        if (start < 0) throw ToolchainException("verapdf lieferte keinen XML-Bericht:\n${output.take(800)}")
        return output.substring(start)
    }

    /** Die verletzten Regeln aus dem Bericht; bestandene Regeln stehen dort auch drin. */
    private fun parseViolations(xml: String): List<PdfAViolation> {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                // Externe Entitäten abschalten: Standard-Hygiene beim XML-Parsen,
                // auch wenn die Eingabe hier aus dem eigenen Prozess stammt.
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }
        val document = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val rules = document.getElementsByTagName("rule")
        return (0 until rules.length)
            .map { rules.item(it) as Element }
            .filter { it.getAttribute("status") == "failed" }
            .map { rule ->
                PdfAViolation(
                    clause = rule.getAttribute("clause"),
                    testNumber = rule.getAttribute("testNumber"),
                    description = rule.childText("description"),
                    errorMessage = rule.childText("errorMessage"),
                )
            }
    }

    // Kleine private Extension: holt den Text des ersten Kindelements mit diesem
    // Namen. Erspart an vier Stellen dieselbe Null- und Längenprüfung.
    private fun Element.childText(tag: String): String =
        getElementsByTagName(tag)
            .item(0)
            ?.textContent
            ?.trim()
            .orEmpty()
}
