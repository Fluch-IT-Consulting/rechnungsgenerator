package de.fluchitconsulting.rechnungsgenerator.xml

import de.fluchitconsulting.rechnungsgenerator.toolchain.XmlParser
import de.fluchitconsulting.rechnungsgenerator.toolchain.validation.EInvoiceValidator
import org.xml.sax.SAXException

/** BT-24, hier gelesen statt aus Mustangs Bericht: Das Feld sagt die Norm zu, das Format des Berichts niemand. */
data class SpecificationIdentifier(
    val value: String,
) {
    /**
     * Nur dann eine E-Rechnung nach §14 Abs. 1 UStG: MINIMUM, BASIC-WL und ZUGFeRD 1 sind es nicht (BMF, 15.10.2024).
     * Gefragt wird nach der Norm und nicht nach einer Liste, damit ein unbekanntes Profil abgelehnt wird.
     */
    val isEn16931: Boolean get() = value.startsWith(EN_16931)

    companion object {
        const val EN_16931 = "urn:cen.eu:en16931:2017"

        /** Wo Mustang sie sucht, in CII wie in UBL. */
        fun of(xml: ByteArray): SpecificationIdentifier? {
            val document =
                try {
                    XmlParser.parse(xml, namespaceAware = true)
                } catch (_: SAXException) {
                    return null
                }
            return EInvoiceValidator
                .specificationOf(document)
                ?.trim()
                ?.ifEmpty { null }
                ?.let { SpecificationIdentifier(it) }
        }
    }
}
