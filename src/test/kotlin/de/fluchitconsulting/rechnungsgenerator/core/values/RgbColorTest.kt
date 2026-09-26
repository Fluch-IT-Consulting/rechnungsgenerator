package de.fluchitconsulting.rechnungsgenerator.core.values

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class RgbColorTest : FunSpec({

    test("die Schreibweise spielt keine Rolle") {
        assertSoftly {
            RgbColor("31ae34") shouldBe RgbColor("31AE34")
            RgbColor(" 31AE34 ") shouldBe RgbColor("31AE34")
            RgbColor("31ae34").hashCode() shouldBe RgbColor("31AE34").hashCode()
        }
    }

    test("gedruckt wird die einheitliche Schreibweise") {
        RgbColor("8d313a").value shouldBe "8D313A"
        RgbColor("8d313a").toString() shouldBe "8D313A"
    }

    test("ein # davor wird abgewiesen, mit dem Hinweis, es wegzulassen") {
        // In YAML beginnt ohne Anführungszeichen ein Kommentar; die Form mit #
        // soll deshalb auch mit ihnen nicht durchgehen.
        val error = shouldThrow<InvalidRgbColorException> { RgbColor("#31AE34") }

        error.message!! shouldContain "'#31AE34'"
        error.message!! shouldContain "ohne #"
    }

    test("was keine sechs Hexadezimalziffern sind, wird abgewiesen") {
        assertSoftly {
            listOf("31AE3", "31AE345", "grün", "green", "31AG34", "0x31AE34", "", "  ").forEach { invalid ->
                val error = shouldThrow<InvalidRgbColorException> { RgbColor(invalid) }
                error.message!! shouldContain "'$invalid'"
            }
        }
    }
})
