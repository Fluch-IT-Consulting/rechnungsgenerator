package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
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

    test("ein gültiges XML ohne PDF geht durch") {
        shouldNotThrowAny { EInvoiceValidator.check(xml.toByteArray()) }
    }

    test("ohne Länderkennung des Verkäufers bricht es ab, mit BR-09 und als Fehler des Generators") {
        val withoutCountry = xml.replaceFirst("<ram:CountryID>DE</ram:CountryID>", "")

        val error = shouldThrow<ToolchainException> { EInvoiceValidator.check(withoutCountry.toByteArray()) }

        error.message!! shouldContain "[BR-09]"
        error.message!! shouldContain "Fehler des Generators"
    }
})
