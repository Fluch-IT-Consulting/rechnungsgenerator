package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import org.mustangproject.validator.ZUGFeRDValidator
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Prüft das XML der E-Rechnung gegen Schema und Schematron seines Profils,
 * bevor es eingebettet wird (ADR-0023).
 *
 * Mustangs Validator nimmt dafür ein XML ohne PDF an, auch wenn seine
 * Dokumentation das nicht sagt; das PDF/A urteilt der [PdfAValidator].
 *
 * Ein Verstoß ist kein Urteil über die Rechnung – die hat ihre Prüfung schon
 * bestanden –, sondern eine Lücke im Generator, der das XML geschrieben hat.
 * Deshalb eine [ToolchainException] und kein Ergebnis wie beim PDF/A, und es
 * entsteht gar kein PDF. Warnungen brechen nicht ab: Welche das XML tragen
 * darf, hält `CiiWriterTest` fest.
 */
object EInvoiceValidator {
    fun check(xml: ByteArray) {
        val validator = ZUGFeRDValidator()
        val report = validator.validate(xml, "factur-x.xml")
        if (!validator.wasCompletelyValid()) {
            throw ToolchainException(
                "Das XML der E-Rechnung ist ungültig. Das ist ein Fehler des Generators, nicht der Rechnung:\n" +
                    errors(report).joinToString("\n") { "  $it" },
            )
        }
    }

    /**
     * Die Fehler aus Mustangs Bericht. Findet sich keiner, obwohl das XML
     * ungültig ist, steht der Bericht selbst da – eine Meldung ohne Grund
     * hülfe niemandem.
     */
    private fun errors(report: String): List<String> {
        val document =
            DocumentBuilderFactory
                .newInstance()
                .apply {
                    setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                    setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                    setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                }.newDocumentBuilder()
                .parse(ByteArrayInputStream(report.toByteArray()))
        val errors = document.getElementsByTagName("error")
        return (0 until errors.length)
            .map { (errors.item(it) as Element).textContent.trim() }
            .ifEmpty { listOf(report) }
    }
}
