package de.fluchitconsulting.rechnungsgenerator.xml

import de.fluchitconsulting.rechnungsgenerator.toolchain.XmlParser
import org.w3c.dom.Element
import org.xml.sax.SAXException

/**
 * Nach welcher Spezifikation sich das XML einer E-Rechnung ausgibt: die
 * Kennung aus `GuidelineSpecifiedDocumentContextParameter` (BT-24).
 *
 * Mustang prüft das XML gegen genau dieses Profil und nennt es auch in seinem
 * Bericht; gelesen wird es trotzdem hier, weil das Feld die Norm zusagt und
 * das Format des Berichts niemand.
 */
data class SpecificationIdentifier(
    val value: String,
) {
    /**
     * Ob das XML der EN 16931 genügt oder sie erweitert. Nur dann ist es eine
     * E-Rechnung nach §14 Abs. 1 UStG: MINIMUM und BASIC-WL tragen nicht alle
     * Pflichtangaben, ZUGFeRD 1 ist älter als die Norm (BMF-Schreiben vom
     * 15.10.2024). Gefragt wird nach der Norm und nicht nach einer Liste
     * dieser Profile, damit ein unbekanntes abgelehnt wird statt durchgelassen.
     */
    val isEn16931: Boolean get() = value.startsWith(EN_16931)

    companion object {
        const val EN_16931 = "urn:cen.eu:en16931:2017"

        /**
         * Die Kennung aus [xml], oder null, wenn es keine lesbare gibt. Nach
         * dem lokalen Namen gesucht, weil ZUGFeRD 1 andere Namensräume hat;
         * ein XML, das nicht wohlgeformt ist oder die Kennung nicht trägt,
         * lehnt ohnehin die XML-Prüfung ab.
         */
        fun of(xml: ByteArray): SpecificationIdentifier? {
            val document =
                try {
                    XmlParser.parse(xml, namespaceAware = true)
                } catch (_: SAXException) {
                    return null
                }
            val parameter = document.getElementsByTagNameNS("*", "GuidelineSpecifiedDocumentContextParameter").item(0) as Element?
            return parameter
                ?.getElementsByTagNameNS("*", "ID")
                ?.item(0)
                ?.textContent
                ?.trim()
                ?.ifEmpty { null }
                ?.let { SpecificationIdentifier(it) }
        }
    }
}
