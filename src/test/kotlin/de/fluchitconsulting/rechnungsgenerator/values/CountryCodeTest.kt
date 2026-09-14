package de.fluchitconsulting.rechnungsgenerator.values

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Der Fall, der diesen Typ ausgelöst hat: `land: de` galt im Renderer als
 * Ausland (`!= "DE"` zeichengenau), im Calculator als Inland (`ignoreCase`).
 * Beide Stellen taten, was dastand – die Schreibweise war einfach nirgends
 * entschieden.
 *
 * Hier ist sie entschieden, ein einziges Mal.
 */
class CountryCodeTest : FunSpec({

    test("die Schreibweise spielt keine Rolle mehr") {
        assertSoftly {
            CountryCode("de") shouldBe CountryCode("DE")
            CountryCode(" DE ") shouldBe CountryCode("DE")
            CountryCode("De") shouldBe CountryCode("DE")
            // Auch die Streuung im Set muss stimmen, sonst findet eine Map den
            // Eintrag nicht wieder – genau der Fehler, den CodeLookup schon
            // einmal hatte.
            CountryCode("de").hashCode() shouldBe CountryCode("DE").hashCode()
        }
    }

    test("gedruckt wird die einheitliche Schreibweise") {
        CountryCode("at").value shouldBe "AT"
        CountryCode("at").toString() shouldBe "AT"
    }

    test("was kein Ländercode ist, wird abgewiesen") {
        // Nicht die exotischen Fälle, sondern die, die ein Mensch tippt, wenn
        // er das Feld für ein Freitextfeld hält.
        assertSoftly {
            listOf("Deutschland", "D", "GER", "de-DE", "D1", "", "  ").forEach { falsch ->
                val fehler = shouldThrow<InvalidCountryCodeException> { CountryCode(falsch) }
                fehler.message!! shouldContain "'$falsch'"
            }
        }
    }

    test("EL bleibt zulässig") {
        // Griechenland heißt in den Umsatzsteuer-Systemen der EU EL, in
        // ISO 3166 dagegen GR. Weil nur die Form geprüft wird und nicht gegen
        // ein Verzeichnis, braucht dieser Sonderfall keine Ausnahme.
        CountryCode("EL").value shouldBe "EL"
    }
})
