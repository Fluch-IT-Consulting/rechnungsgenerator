package de.fluchitconsulting.rechnungsgenerator.toolchain

import org.mustangproject.validator.ZUGFeRDValidator
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.SAXParseException

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
        notWellFormed(xml)?.let { return EInvoiceValidationResult(errors = listOf(it), warnings = emptyList()) }
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

    /**
     * Mustang parst das XML zuerst selbst, ohne `ErrorHandler`: Ist es nicht
     * wohlgeformt, druckt der Parser des JDK `[Fatal Error]` auf stderr, und
     * Mustang meldet statt des Grundes, die Datei sehe weder nach PDF noch
     * nach XML aus (#395). Deshalb bekommt Mustang nur wohlgeformtes XML, und
     * den Grund nennt diese Prüfung. Mit Namensräumen wie bei Mustang, sonst
     * käme ein unbekanntes Präfix hier durch und dort wieder auf stderr.
     */
    private fun notWellFormed(xml: ByteArray): String? =
        try {
            XmlParser.parse(xml, namespaceAware = true)
            null
        } catch (e: SAXParseException) {
            "Kein wohlgeformtes XML (Zeile ${e.lineNumber}, Spalte ${e.columnNumber}): ${e.message}"
        }

    private fun parse(report: String): Document = XmlParser.parse(report.toByteArray())

    private fun messages(
        document: Document,
        tag: String,
    ): List<String> {
        val elements = document.getElementsByTagName(tag)
        return (0 until elements.length).map { (elements.item(it) as Element).textContent.trim() }
    }
}
