package de.fluchitconsulting.rechnungsgenerator.xml

import de.fluchitconsulting.rechnungsgenerator.toolchain.XmlParser
import org.w3c.dom.Element
import org.xml.sax.SAXException

/**
 * Wo Verkäufer und Käufer einer E-Rechnung sitzen: der Ländercode aus der
 * Anschrift des Verkäufers (BT-40) und der des Käufers (BT-55), so wie er
 * dasteht.
 *
 * Gelesen wie [SpecificationIdentifier] und nicht über Mustang, weil das Feld
 * die Norm zusagt. Ob die Codes gültig sind, urteilt die XML-Prüfung.
 */
data class PartyCountries(
    val seller: String?,
    val buyer: String?,
) {
    /**
     * Ob beide in Deutschland sitzen. Nur dann gilt BR-FX-DE-03, und die Frage
     * ist wörtlich Deutschland und nicht das Inland des Verkäufers: Eine
     * Rechnung innerhalb Frankreichs ist eine Inlandsrechnung, die Regel gilt
     * für sie aber nicht.
     */
    val withinGermany: Boolean get() = seller == GERMANY && buyer == GERMANY

    companion object {
        private const val GERMANY = "DE"

        /**
         * Die Länder aus [xml]; fehlt eines oder ist das XML nicht lesbar,
         * steht dort null. Gesucht wird nur die Anschrift direkt an der Partei:
         * Darunter kann etwa die Rechtsform eine eigene tragen.
         */
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
