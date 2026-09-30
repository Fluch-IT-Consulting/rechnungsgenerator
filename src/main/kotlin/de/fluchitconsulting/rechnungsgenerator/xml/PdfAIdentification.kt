package de.fluchitconsulting.rechnungsgenerator.xml

import de.fluchitconsulting.rechnungsgenerator.toolchain.XmlParser
import de.fluchitconsulting.rechnungsgenerator.toolchain.validation.PdfAProfile
import org.apache.pdfbox.pdmodel.PDDocument
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.SAXException

/**
 * Als welches PDF/A sich ein Dokument im XMP ausgibt, nicht, was es ist: Das urteilt der `PdfAValidator` gegen
 * genau dieses Profil. Gelesen wird auch ein unbekannter Teil, damit die Meldung ihn nennen kann.
 */
data class PdfAIdentification(
    val part: String,
    val conformance: String?,
) {
    val label: String get() = "PDF/A-$part${conformance.orEmpty().lowercase()}"

    /** Die Stufe ohne Rücksicht auf die Schreibweise: Ob sie der Norm genügt, urteilt der Prüfer. */
    val profile: PdfAProfile?
        get() = PdfAProfile.entries.find { it.part.toString() == part && it.conformance.equals(conformance, ignoreCase = true) }

    companion object {
        private const val RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
        private const val PDFAID = "http://www.aiim.org/pdfa/ns/id/"

        /** `null` ohne XMP, ohne lesbares XMP oder ohne `pdfaid:part`. */
        fun of(document: PDDocument): PdfAIdentification? {
            val xmp = document.documentCatalog.metadata?.toByteArray() ?: return null
            val descriptions = parse(xmp)?.getElementsByTagNameNS(RDF, "Description") ?: return null
            val elements = (0 until descriptions.length).map { descriptions.item(it) as Element }
            val part = elements.firstNotNullOfOrNull { it.pdfaid("part") } ?: return null
            return PdfAIdentification(part, elements.firstNotNullOfOrNull { it.pdfaid("conformance") })
        }

        /** Als Element oder als Attribut der `rdf:Description`, beides gültig; LuaLaTeX schreibt Elemente. */
        private fun Element.pdfaid(name: String): String? =
            (getAttributeNodeNS(PDFAID, name)?.value ?: getElementsByTagNameNS(PDFAID, name).item(0)?.textContent)
                ?.trim()
                ?.ifEmpty { null }

        // Dass ein XMP kein XML ist, meldet der Prüfer.
        private fun parse(xmp: ByteArray): Document? =
            try {
                XmlParser.parse(xmp, namespaceAware = true)
            } catch (_: SAXException) {
                null
            }
    }
}
