package de.fluchitconsulting.rechnungsgenerator.toolchain

import org.mustangproject.validator.ZUGFeRDValidator
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Was Mustang über das XML einer E-Rechnung sagt: die Fehler, die es ungültig
 * machen, und die Warnungen, die das nicht tun.
 *
 * Gültig heißt: kein Fehler. Eine eigene Angabe daneben gibt es nicht, damit
 * der Wert sich nicht selbst widersprechen kann.
 */
data class EInvoiceValidationResult(
    val errors: List<String>,
    val warnings: List<String>,
) {
    val valid: Boolean get() = errors.isEmpty()
}

/**
 * Prüft das XML einer E-Rechnung gegen Schema und Schematron seines Profils
 * (ADR-0023). Welches Profil es ist, erkennt Mustang am XML selbst.
 *
 * Mustangs Validator nimmt dafür ein XML ohne PDF an, auch wenn seine
 * Dokumentation das nicht sagt; das PDF/A urteilt der [PdfAValidator].
 *
 * Ein ungültiges XML ist ein Ergebnis und keine Ausnahme, wie beim PDF/A:
 * Was daraus folgt, entscheidet das Kommando. Beim Erzeugen ist ein Fehler
 * eine Lücke im Generator, der das XML geschrieben hat, beim Auslesen sagt er
 * etwas über die Datei, die jemand übergeben hat.
 */
object EInvoiceValidator {
    fun validate(xml: ByteArray): EInvoiceValidationResult {
        val validator = ZUGFeRDValidator()
        val report = validator.validate(xml, "factur-x.xml")
        val document = parse(report)
        // Einen fatalen Fehler schreibt Mustang als error, was es gar nicht
        // prüfen konnte, als exception. Beides macht das XML ungültig.
        val errors = messages(document, "error") + messages(document, "exception")
        return EInvoiceValidationResult(
            // Findet sich kein Fehler, obwohl das XML ungültig ist, steht der
            // Bericht selbst da – eine Meldung ohne Grund hülfe niemandem.
            errors = if (!validator.wasCompletelyValid() && errors.isEmpty()) listOf(report) else errors,
            warnings = messages(document, "warning"),
        )
    }

    private fun parse(report: String): Document =
        DocumentBuilderFactory
            .newInstance()
            .apply {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }.newDocumentBuilder()
            .parse(ByteArrayInputStream(report.toByteArray()))

    private fun messages(
        document: Document,
        tag: String,
    ): List<String> {
        val elements = document.getElementsByTagName(tag)
        return (0 until elements.length).map { (elements.item(it) as Element).textContent.trim() }
    }
}
