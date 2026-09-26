package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.mustangproject.validator.ZUGFeRDValidator
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Was Mustangs Validator über ein XML sagt, das in keinem PDF steckt.
 *
 * ADR-0023 lässt ihn nur das XML prüfen, und zwar bevor es eingebettet wird:
 * Das PDF/A urteilt die Naht `PdfAValidator`. Dass er ein bloßes XML annimmt,
 * sagt seine Dokumentation nicht, deshalb steht es hier.
 *
 * Die Gegenprobe zeigt, dass dabei wirklich das Schematron des Profils läuft
 * und nicht nur das Schema: Eine fehlende Länderkennung des Verkäufers verletzt
 * die Geschäftsregel BR-09.
 */
class MustangValidatorTest : FunSpec({

    val xml = Path.of("src/test/resources/rechnung-erwartet.xml").readText()

    test("ein gültiges XML ohne PDF gilt als gültig") {
        val validator = ZUGFeRDValidator()
        val report = validator.validate(xml.toByteArray(), "rechnung-erwartet.xml")

        validator.wasCompletelyValid() shouldBe true
        report shouldContain "urn:factur-x.eu:1p0:extended"
    }

    test("ohne Länderkennung des Verkäufers verletzt es BR-09") {
        val withoutCountry = xml.replaceFirst("<ram:CountryID>DE</ram:CountryID>", "")
        val validator = ZUGFeRDValidator()
        val report = validator.validate(withoutCountry.toByteArray(), "rechnung-erwartet.xml")

        validator.wasCompletelyValid() shouldBe false
        report shouldContain "[BR-09]"
    }
})
