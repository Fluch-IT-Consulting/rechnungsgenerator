package de.fluchitconsulting.rechnungsgenerator.xml

import de.fluchitconsulting.rechnungsgenerator.toolchain.PdfAProfile
import de.fluchitconsulting.rechnungsgenerator.toolchain.XmlParser
import org.apache.pdfbox.pdmodel.PDDocument
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.SAXException

/**
 * Als welches PDF/A sich ein Dokument ausgibt: Teil und Konformitätsstufe aus
 * dem Identifikationsschema seines XMP (`pdfaid:part`, `pdfaid:conformance`).
 *
 * Ausgibt, nicht ist. Ob die Angabe stimmt, urteilt erst der `PdfAValidator`,
 * und zwar gegen genau das Profil, das hier steht. Gelesen wird deshalb, was
 * dasteht, auch ein Teil, den der Generator nicht kennt: Für die Meldung
 * zählt, als was sich die Datei ausgibt, nicht nur, dass es nicht passt.
 */
data class PdfAIdentification(
    val part: String,
    val conformance: String?,
) {
    /** Wie es in einer Meldung steht, etwa "PDF/A-3u". */
    val label: String get() = "PDF/A-$part${conformance.orEmpty().lowercase()}"

    /**
     * Das Profil, gegen das sich prüfen lässt, oder null, wenn der Generator
     * keines dazu kennt. Die Stufe vergleicht es ohne Rücksicht auf die
     * Schreibweise: Verlangt sind Großbuchstaben, aber ob die Angabe der Norm
     * genügt, gehört zum Urteil des Prüfers und nicht zum Lesen.
     */
    val profile: PdfAProfile?
        get() = PdfAProfile.entries.find { it.part.toString() == part && it.conformance.equals(conformance, ignoreCase = true) }

    companion object {
        private const val RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
        private const val PDFAID = "http://www.aiim.org/pdfa/ns/id/"

        /**
         * Die Angabe aus dem XMP von [document], oder null, wenn es sich nicht
         * als PDF/A ausgibt: Es hat kein XMP, keines, das sich als XML lesen
         * lässt, oder keines mit `pdfaid:part`.
         */
        fun of(document: PDDocument): PdfAIdentification? {
            val xmp = document.documentCatalog.metadata?.toByteArray() ?: return null
            val descriptions = parse(xmp)?.getElementsByTagNameNS(RDF, "Description") ?: return null
            val elements = (0 until descriptions.length).map { descriptions.item(it) as Element }
            val part = elements.firstNotNullOfOrNull { it.pdfaid("part") } ?: return null
            return PdfAIdentification(part, elements.firstNotNullOfOrNull { it.pdfaid("conformance") })
        }

        /**
         * Eine Angabe steht im XMP als eigenes Element oder als Attribut der
         * `rdf:Description`. Beides ist gültig, und welche Form ein Programm
         * schreibt, ist seine Sache; LuaLaTeX schreibt Elemente.
         */
        private fun Element.pdfaid(name: String): String? =
            (getAttributeNodeNS(PDFAID, name)?.value ?: getElementsByTagNameNS(PDFAID, name).item(0)?.textContent)
                ?.trim()
                ?.ifEmpty { null }

        // Ein XMP, das kein XML ist, gibt nichts über das Dokument aus.
        // Dass es die Norm verletzt, meldet der Prüfer.
        private fun parse(xmp: ByteArray): Document? =
            try {
                XmlParser.parse(xmp, namespaceAware = true)
            } catch (_: SAXException) {
                null
            }
    }
}
