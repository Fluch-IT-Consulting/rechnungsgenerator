package de.fluchitconsulting.rechnungsgenerator.toolchain.validation

import de.fluchitconsulting.rechnungsgenerator.toolchain.capturingStandardError
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** BR-09 zeigt, dass das Schematron läuft und nicht nur das Schema. */
class EInvoiceValidatorTest : FunSpec({

    val xml = Path.of("src/test/resources/rechnung-erwartet.xml").readText()

    test("ein gültiges XML ohne PDF geht durch, ohne Fehler und ohne Warnung") {
        val result = EInvoiceValidator.validate(xml.toByteArray())

        result.valid shouldBe true
        result.errors.shouldBeEmpty()
        result.warnings.shouldBeEmpty()
    }

    // Die eigenen Rechnungen tragen keine Warnung (ADR-0023), deshalb ein leerer
    // Lieferblock: PEPPOL-EN16931-R008.
    test("eine Warnung macht das XML nicht ungültig und steht im Ergebnis") {
        val withoutDelivery =
            xml.replace(
                Regex("(?s)<ram:ApplicableHeaderTradeDelivery>.*?</ram:ApplicableHeaderTradeDelivery>"),
                "<ram:ApplicableHeaderTradeDelivery/>",
            )

        val result = EInvoiceValidator.validate(withoutDelivery.toByteArray())

        result.valid shouldBe true
        result.errors.shouldBeEmpty()
        result.warnings shouldHaveSize 1
        result.warnings.single() shouldContain "[ID PEPPOL-EN16931-R008]"
    }

    test("ohne Länderkennung des Verkäufers ist es ungültig, mit BR-09") {
        val withoutCountry = xml.replaceFirst("<ram:CountryID>DE</ram:CountryID>", "")

        val result = EInvoiceValidator.validate(withoutCountry.toByteArray())

        result.valid shouldBe false
        result.errors.any { "[BR-09]" in it } shouldBe true
    }

    test("was gar kein XML ist, ist ungültig, und der Grund steht allein da") {
        val result = EInvoiceValidator.validate("Das ist Text und kein XML, auch wenn er lang genug ist.".toByteArray())

        result.valid shouldBe false
        result.errors shouldBe listOf("Kein wohlgeformtes XML (Zeile 1, Spalte 1): Content ist nicht zulässig in Prolog.")
    }

    // #395
    test("ein Zeichen, das XML verbietet, nennt Ort und Grund und schreibt nichts auf stderr") {
        val withControlCharacter = xml.replaceFirst("IT-Beratungsleistungen", "IT-Beratung\u000Bleistungen")

        val (result, stderr) = capturingStandardError { EInvoiceValidator.validate(withControlCharacter.toByteArray()) }

        result.valid shouldBe false
        result.errors.single() shouldContain "Kein wohlgeformtes XML (Zeile 21, Spalte 30)"
        result.errors.single() shouldContain "0xb"
        stderr shouldBe ""
    }

    // #426. Auch ein leeres Element trägt keine Kennung.
    test("ohne Kennung der Spezifikation ist es ungültig, und der Grund steht allein da") {
        val specification =
            Regex(
                "<ram:GuidelineSpecifiedDocumentContextParameter>.*?</ram:GuidelineSpecifiedDocumentContextParameter>",
                RegexOption.DOT_MATCHES_ALL,
            )

        listOf(
            xml.replace(specification, ""),
            xml.replace(
                specification,
                "<ram:GuidelineSpecifiedDocumentContextParameter><ram:ID/></ram:GuidelineSpecifiedDocumentContextParameter>",
            ),
        ).forAll {
            EInvoiceValidator.validate(it.toByteArray()).errors shouldBe
                listOf("Keine Kennung der Spezifikation (BT-24), gegen die sich das XML prüfen ließe.")
        }
    }

    // #488: Mustang liefert dann einen Bericht, der nur aus der XML-Deklaration besteht.
    test("ein Bericht, den Mustang nicht lesbar liefert, ist ein Fehler und keine Ausnahme") {
        val withAmpersand = xml.replaceFirst("#conformant#", "#conformant&amp;#")

        val result = EInvoiceValidator.validate(withAmpersand.toByteArray())

        result.valid shouldBe false
        result.errors.single() shouldContain "Mustang lieferte keinen lesbaren Bericht"
    }

    // Mustang parst mit Namensräumen, also muss es die Vorprüfung auch.
    test("ein unbekanntes Präfix ist nicht wohlgeformt") {
        val withUnknownPrefix = xml.replaceFirst("<ram:CountryID>", "<unbekannt:CountryID>")

        val (result, stderr) = capturingStandardError { EInvoiceValidator.validate(withUnknownPrefix.toByteArray()) }

        result.errors.single() shouldContain "Kein wohlgeformtes XML"
        stderr shouldBe ""
    }

    // #494: Das XML kann aus einem fremden PDF stammen. „Kein wohlgeformtes
    // XML“ meldet allein die eigene Vorprüfung.
    test("eine externe Entität weist schon die Vorprüfung ab, bevor Mustang das XML sieht") {
        val secret = createTempFile("einvoice-validator", ".txt").also { it.writeText("GEHEIM-494") }
        val withEntity =
            xml
                .replaceFirst("?>", "?>\n<!DOCTYPE rsm:CrossIndustryInvoice [<!ENTITY x SYSTEM \"${secret.toUri()}\">]>")
                .replaceFirst("IT-Beratungsleistungen", "&x;")

        val result = EInvoiceValidator.validate(withEntity.toByteArray())

        result.valid shouldBe false
        result.errors.single() shouldStartWith "Kein wohlgeformtes XML"
        result.errors.single() shouldNotContain "GEHEIM-494"
    }
})
