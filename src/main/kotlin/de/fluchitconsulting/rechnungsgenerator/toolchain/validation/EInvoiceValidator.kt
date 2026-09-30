package de.fluchitconsulting.rechnungsgenerator.toolchain.validation

import de.fluchitconsulting.rechnungsgenerator.toolchain.XmlParser
import org.mustangproject.validator.ZUGFeRDValidator
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.SAXParseException
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

data class EInvoiceValidationResult(
    val errors: List<String>,
    val warnings: List<String>,
) {
    val valid: Boolean get() = errors.isEmpty()
}

/**
 * Gegen Schema und Schematron des Profils im XML (ADR-0023). Mustang nimmt ein XML ohne PDF an, auch wenn seine
 * Dokumentation das nicht sagt. Ein ungültiges XML ist ein Ergebnis; was es bedeutet, entscheidet das Kommando.
 */
object EInvoiceValidator {
    fun validate(xml: ByteArray): EInvoiceValidationResult {
        unfitForMustang(xml)?.let { return EInvoiceValidationResult(errors = listOf(it), warnings = emptyList()) }
        val validator = ZUGFeRDValidator()
        val report = validator.validate(xml, "factur-x.xml")
        // Bei `&amp;` in BT-24 besteht der Bericht nur aus der XML-Deklaration (#488).
        val document =
            try {
                parse(report)
            } catch (e: SAXParseException) {
                return EInvoiceValidationResult(
                    errors = listOf("Mustang lieferte keinen lesbaren Bericht (${e.message}). Der Bericht lautet:\n$report"),
                    warnings = emptyList(),
                )
            }
        // Was Mustang nicht prüfen konnte, steht als exception da und macht ebenso ungültig.
        val errors = messages(document, "error") + messages(document, "exception")
        return EInvoiceValidationResult(
            // Ungültig ohne genannten Fehler: Dann steht der Bericht selbst da.
            errors = if (!validator.wasCompletelyValid() && errors.isEmpty()) listOf(report) else errors,
            warnings = messages(document, "warning"),
        )
    }

    /**
     * Was Mustang ohne brauchbaren Grund ablehnte. Nicht wohlgeformt druckte sein Parser `[Fatal Error]` auf stderr
     * (#395), deshalb mit Namensräumen wie bei ihm. Ohne BT-24 bräche er mit einer NullPointerException ab (#426),
     * deshalb mit seinem eigenen Ausdruck, auch für ein leeres Element.
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
