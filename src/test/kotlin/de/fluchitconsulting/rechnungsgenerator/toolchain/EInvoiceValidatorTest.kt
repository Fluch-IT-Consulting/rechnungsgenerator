package de.fluchitconsulting.rechnungsgenerator.toolchain

import io.kotest.core.spec.style.FunSpec
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

    // Die Warnung zum leeren ApplicableHeaderTradeDelivery trägt jede eigene
    // Rechnung ohne Lieferland (ADR-0023).
    test("ein gültiges XML ohne PDF geht durch, und seine Warnung steht im Ergebnis") {
        val result = EInvoiceValidator.validate(xml.toByteArray())

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

    // Mustang meldet das als exception und nicht als error. Gelesen würde
    // sonst nur der ganze Bericht, und der Grund stünde irgendwo darin.
    test("was gar kein XML ist, ist ungültig, und der Grund steht allein da") {
        val result = EInvoiceValidator.validate("Das ist Text und kein XML, auch wenn er lang genug ist.".toByteArray())

        result.valid shouldBe false
        result.errors shouldBe listOf("File does not look like PDF nor XML (contains neither %PDF nor <?xml)")
    }
})
