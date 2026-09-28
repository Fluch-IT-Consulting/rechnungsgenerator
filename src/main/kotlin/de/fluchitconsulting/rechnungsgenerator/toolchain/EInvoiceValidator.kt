package de.fluchitconsulting.rechnungsgenerator.toolchain

import org.mustangproject.validator.ZUGFeRDValidator
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.SAXParseException
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

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
        unfitForMustang(xml)?.let { return EInvoiceValidationResult(errors = listOf(it), warnings = emptyList()) }
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
     * Was Mustang ablehnte, ohne einen brauchbaren Grund zu nennen. Er bekommt
     * es gar nicht erst zu sehen, und den Grund nennt diese Prüfung.
     *
     * Mustang parst das XML zuerst selbst, ohne `ErrorHandler`: Ist es nicht
     * wohlgeformt, druckt der Parser des JDK `[Fatal Error]` auf stderr, und
     * Mustang meldet statt des Grundes, die Datei sehe weder nach PDF noch
     * nach XML aus (#395). Mit Namensräumen wie bei Mustang, sonst käme ein
     * unbekanntes Präfix hier durch und dort wieder auf stderr.
     *
     * Fehlt die Kennung der Spezifikation (BT-24), wählt Mustang kein Profil
     * und bricht mit einer `NullPointerException` ab, die samt Stacktrace im
     * Bericht stünde (#426). Gesucht wird mit Mustangs eigenem Ausdruck, damit
     * die Vorprüfung genau dann anschlägt, wenn er kein Profil fände – auch
     * bei einem leeren Element, das keinen Text trägt.
     */
    private fun unfitForMustang(xml: ByteArray): String? {
        val document =
            try {
                XmlParser.parse(xml, namespaceAware = true)
            } catch (e: SAXParseException) {
                return "Kein wohlgeformtes XML (Zeile ${e.lineNumber}, Spalte ${e.columnNumber}): ${e.message}"
            }
        val specification = XPathFactory.newInstance().newXPath().evaluate(SPECIFICATION, document, XPathConstants.NODESET) as NodeList
        return "Keine Kennung der Spezifikation (BT-24), gegen die sich das XML prüfen ließe.".takeIf { specification.length == 0 }
    }

    /** Wo Mustang das Profil sucht, aus `XMLValidator`: in CII unter BT-24, in UBL unter `CustomizationID`. */
    private const val SPECIFICATION =
        "(//*[local-name()=\"GuidelineSpecifiedDocumentContextParameter\"]/*[local-name()=\"ID\"])/text()" +
            "|//*[local-name()=\"CustomizationID\"]/text()"

    private fun parse(report: String): Document = XmlParser.parse(report.toByteArray())

    private fun messages(
        document: Document,
        tag: String,
    ): List<String> {
        val elements = document.getElementsByTagName(tag)
        return (0 until elements.length).map { (elements.item(it) as Element).textContent.trim() }
    }
}
