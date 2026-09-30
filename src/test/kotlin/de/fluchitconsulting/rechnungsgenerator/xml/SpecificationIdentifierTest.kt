package de.fluchitconsulting.rechnungsgenerator.xml

import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.readBytes

/** BT-24. Dazu die Kennungen, unter denen ZUGFeRD 2.0 MINIMUM und BASIC-WL führte. */
class SpecificationIdentifierTest : FunSpec({

    fun cii(id: String) =
        """
        <rsm:CrossIndustryInvoice xmlns:rsm="urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100"
            xmlns:ram="urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100">
          <rsm:ExchangedDocumentContext>
            <ram:GuidelineSpecifiedDocumentContextParameter><ram:ID>$id</ram:ID></ram:GuidelineSpecifiedDocumentContextParameter>
          </rsm:ExchangedDocumentContext>
        </rsm:CrossIndustryInvoice>
        """.trimIndent().toByteArray()

    test("die E-Rechnung des Generators folgt EXTENDED und damit der EN 16931") {
        val identifier = SpecificationIdentifier.of(Path.of("src/test/resources/rechnung-erwartet.xml").readBytes())!!

        identifier.value shouldBe "urn:cen.eu:en16931:2017#conformant#urn:factur-x.eu:1p0:extended"
        identifier.isEn16931 shouldBe true
    }

    test("BASIC, EN16931, EXTENDED und XRechnung genügen der EN 16931") {
        listOf(
            "urn:cen.eu:en16931:2017#compliant#urn:factur-x.eu:1p0:basic",
            "urn:cen.eu:en16931:2017",
            "urn:cen.eu:en16931:2017#conformant#urn:factur-x.eu:1p0:extended",
            "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0",
        ).forAll { SpecificationIdentifier.of(cii(it))!!.isEn16931 shouldBe true }
    }

    test("MINIMUM und BASIC-WL genügen ihr nicht, auch nicht unter dem Namen von ZUGFeRD 2.0") {
        listOf(
            "urn:factur-x.eu:1p0:minimum",
            "urn:factur-x.eu:1p0:basicwl",
            "urn:zugferd.de:2p0:minimum",
            "urn:zugferd.de:2p0:basicwl",
        ).forAll { SpecificationIdentifier.of(cii(it))!!.isEn16931 shouldBe false }
    }

    test("ZUGFeRD 1 wird trotz anderer Namensräume gelesen und genügt ihr nicht") {
        val xml =
            """
            <rsm:CrossIndustryDocument xmlns:rsm="urn:ferd:CrossIndustryDocument:invoice:1p0"
                xmlns:ram="urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:12">
              <rsm:SpecifiedExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                  <ram:ID>urn:ferd:CrossIndustryDocument:invoice:1p0:comfort</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
              </rsm:SpecifiedExchangedDocumentContext>
            </rsm:CrossIndustryDocument>
            """.trimIndent().toByteArray()

        val identifier = SpecificationIdentifier.of(xml)!!

        identifier.value shouldBe "urn:ferd:CrossIndustryDocument:invoice:1p0:comfort"
        identifier.isEn16931 shouldBe false
    }

    test("in UBL steht die Kennung unter CustomizationID, wie Mustang sie liest (#509)") {
        SpecificationIdentifier.of(ubl("urn:example:minimum"))!!.value shouldBe "urn:example:minimum"
        SpecificationIdentifier
            .of(ubl("urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0"))!!
            .isEn16931 shouldBe true
    }

    test("ohne lesbare Kennung gibt es keine") {
        SpecificationIdentifier.of("<kein xml".toByteArray()).shouldBeNull()
        SpecificationIdentifier.of("<rsm:CrossIndustryInvoice xmlns:rsm=\"urn:x\"/>".toByteArray()).shouldBeNull()
        SpecificationIdentifier.of(cii("  ")).shouldBeNull()
    }
})
