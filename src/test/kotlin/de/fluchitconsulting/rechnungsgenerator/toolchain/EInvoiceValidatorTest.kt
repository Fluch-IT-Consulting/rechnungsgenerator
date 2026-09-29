package de.fluchitconsulting.rechnungsgenerator.toolchain

import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Was der Prüfer über das XML einer E-Rechnung sagt, das in keinem PDF steckt.
 *
 * Die Gegenprobe zeigt, dass dabei wirklich das Schematron läuft und nicht
 * nur das Schema: Eine fehlende Länderkennung des Verkäufers verletzt die
 * Geschäftsregel BR-09.
 */
class EInvoiceValidatorTest : FunSpec({

    val xml = Path.of("src/test/resources/rechnung-erwartet.xml").readText()

    test("ein gültiges XML ohne PDF geht durch, ohne Fehler und ohne Warnung") {
        val result = EInvoiceValidator.validate(xml.toByteArray())

        result.valid shouldBe true
        result.errors.shouldBeEmpty()
        result.warnings.shouldBeEmpty()
    }

    // Die eigenen Rechnungen tragen keine Warnung (ADR-0023). Ein leerer
    // Lieferblock steht für eine fremde, die eine trägt: PEPPOL-EN16931-R008.
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

    // Mustang sagte dazu "File does not look like PDF nor XML", und vorher
    // stand die Zeile [Fatal Error] seines Parsers auf stderr (#395).
    test("ein Zeichen, das XML verbietet, nennt Ort und Grund und schreibt nichts auf stderr") {
        val withControlCharacter = xml.replaceFirst("IT-Beratungsleistungen", "IT-Beratung\u000Bleistungen")

        val (result, stderr) = capturingStandardError { EInvoiceValidator.validate(withControlCharacter.toByteArray()) }

        result.valid shouldBe false
        result.errors.single() shouldContain "Kein wohlgeformtes XML (Zeile 21, Spalte 30)"
        result.errors.single() shouldContain "0xb"
        stderr shouldBe ""
    }

    // Mustang fände kein Profil und bräche mit einer NullPointerException ab,
    // deren Stacktrace sonst als Grund dastünde (#426). Auch ein leeres
    // Element trägt keine Kennung.
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

    // Mustang parst mit Namensräumen. Prüfte die Vorprüfung ohne, käme ein
    // unbekanntes Präfix an ihr vorbei und bei Mustang wieder auf stderr.
    test("ein unbekanntes Präfix ist nicht wohlgeformt") {
        val withUnknownPrefix = xml.replaceFirst("<ram:CountryID>", "<unbekannt:CountryID>")

        val (result, stderr) = capturingStandardError { EInvoiceValidator.validate(withUnknownPrefix.toByteArray()) }

        result.errors.single() shouldContain "Kein wohlgeformtes XML"
        stderr shouldBe ""
    }
})
