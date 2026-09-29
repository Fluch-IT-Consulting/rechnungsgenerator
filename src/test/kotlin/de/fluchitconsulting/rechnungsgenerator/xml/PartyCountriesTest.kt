package de.fluchitconsulting.rechnungsgenerator.xml

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.readBytes

/** BT-40, BT-55 */
class PartyCountriesTest : FunSpec({

    fun cii(
        seller: String?,
        buyer: String?,
    ): ByteArray {
        fun party(
            name: String,
            country: String?,
        ) = "<ram:$name><ram:PostalTradeAddress>${country?.let { "<ram:CountryID>$it</ram:CountryID>" }.orEmpty()}" +
            "</ram:PostalTradeAddress></ram:$name>"
        return """
            <rsm:CrossIndustryInvoice xmlns:rsm="urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100"
                xmlns:ram="urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100">
              <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                  ${party("SellerTradeParty", seller)}
                  ${party("BuyerTradeParty", buyer)}
                </ram:ApplicableHeaderTradeAgreement>
              </rsm:SupplyChainTradeTransaction>
            </rsm:CrossIndustryInvoice>
            """.trimIndent().toByteArray()
    }

    test("die E-Rechnung des Generators geht von Würzburg nach Nürnberg") {
        val countries = PartyCountries.of(Path.of("src/test/resources/rechnung-erwartet.xml").readBytes())

        countries shouldBe PartyCountries("DE", "DE")
        countries.withinGermany shouldBe true
    }

    test("sitzt eine Seite anderswo, ist es keine Rechnung innerhalb Deutschlands") {
        PartyCountries.of(cii("DE", "AT")).withinGermany shouldBe false
        PartyCountries.of(cii("AT", "DE")).withinGermany shouldBe false
    }

    test("gelesen wird nur die Anschrift der Partei selbst, nicht die ihrer Rechtsform") {
        val xml =
            cii("AT", "DE")
                .decodeToString()
                .replace(
                    "<ram:SellerTradeParty>",
                    "<ram:SellerTradeParty><ram:SpecifiedLegalOrganization><ram:PostalTradeAddress>" +
                        "<ram:CountryID>DE</ram:CountryID></ram:PostalTradeAddress></ram:SpecifiedLegalOrganization>",
                ).toByteArray()

        PartyCountries.of(xml) shouldBe PartyCountries("AT", "DE")
    }

    // Ein fehlendes Land lehnt die XML-Prüfung ohnehin ab (BR-09, BR-11).
    test("ohne lesbares Land gibt es keines, und innerhalb Deutschlands ist sie dann nicht") {
        PartyCountries.of(cii(null, "DE")) shouldBe PartyCountries(null, "DE")
        PartyCountries.of(cii("DE", "  ")).buyer.shouldBeNull()
        PartyCountries.of(cii(null, "DE")).withinGermany shouldBe false
        PartyCountries.of("<kein xml".toByteArray()) shouldBe PartyCountries(null, null)
    }

    test("verlangt ist DE, wie die Codeliste es schreibt") {
        PartyCountries.of(cii(" DE ", "DE")).withinGermany shouldBe true
        PartyCountries.of(cii("de", "DE")).withinGermany shouldBe false
    }
})
