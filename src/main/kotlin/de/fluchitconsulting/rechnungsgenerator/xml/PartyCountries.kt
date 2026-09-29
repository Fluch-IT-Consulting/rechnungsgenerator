package de.fluchitconsulting.rechnungsgenerator.xml

import de.fluchitconsulting.rechnungsgenerator.toolchain.XmlParser
import org.w3c.dom.Element
import org.xml.sax.SAXException

/** BT-40 und BT-55, wie sie dastehen; ob sie gültig sind, urteilt die XML-Prüfung. */
data class PartyCountries(
    val seller: String?,
    val buyer: String?,
) {
    /** Wörtlich Deutschland und nicht das Inland des Verkäufers, wie BR-FX-DE-03 es verlangt. */
    val withinGermany: Boolean get() = seller == GERMANY && buyer == GERMANY

    companion object {
        private const val GERMANY = "DE"

        /** Nur die Anschrift direkt an der Partei: Darunter kann etwa die Rechtsform eine eigene tragen. */
        fun of(xml: ByteArray): PartyCountries {
            val document =
                try {
                    XmlParser.parse(xml, namespaceAware = true)
                } catch (_: SAXException) {
                    return PartyCountries(null, null)
                }
            val agreement = document.getElementsByTagNameNS("*", "ApplicableHeaderTradeAgreement").item(0) as Element?

            fun country(party: String) =
                agreement
                    ?.child(party)
                    ?.child("PostalTradeAddress")
                    ?.child("CountryID")
                    ?.textContent
                    ?.trim()
                    ?.ifEmpty { null }
            return PartyCountries(country("SellerTradeParty"), country("BuyerTradeParty"))
        }

        private fun Element.child(localName: String): Element? =
            generateSequence(firstChild) { it.nextSibling }
                .filterIsInstance<Element>()
                .firstOrNull { it.localName == localName }
    }
}
