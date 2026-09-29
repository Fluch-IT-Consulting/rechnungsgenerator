package de.fluchitconsulting.rechnungsgenerator.core.values

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class CountryCodeTest : FunSpec({

    test("die Schreibweise spielt keine Rolle") {
        assertSoftly {
            CountryCode("de") shouldBe CountryCode("DE")
            CountryCode(" DE ") shouldBe CountryCode("DE")
            CountryCode("De") shouldBe CountryCode("DE")
            CountryCode("de").hashCode() shouldBe CountryCode("DE").hashCode()
        }
    }

    test("gedruckt wird die einheitliche Schreibweise") {
        CountryCode("at").value shouldBe "AT"
        CountryCode("at").toString() shouldBe "AT"
    }

    test("was kein Ländercode ist, wird abgewiesen") {
        assertSoftly {
            listOf("Deutschland", "D", "GER", "de-DE", "D1", "", "  ").forEach { invalid ->
                val error = shouldThrow<InvalidCountryCodeException> { CountryCode(invalid) }
                error.message!! shouldContain "'$invalid'"
            }
        }
    }

    test("EL bleibt zulässig") {
        // Griechenland in der Umsatzsteuer der EU, in ISO 3166 GR.
        CountryCode("EL").value shouldBe "EL"
    }
})
