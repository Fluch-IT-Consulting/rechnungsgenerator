package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.values.ExchangeRate
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent
import de.fluchitconsulting.rechnungsgenerator.core.values.Price
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

/** Nur die Ziffern; ihre Schreibweise prüft das Pflichtangaben-Netz (ADR-0021). */
class NumbersTest : FunSpec({

    test("Beträge mit genau zwei Nachkommastellen") {
        assertSoftly {
            Numbers.amount(Money("8337.50")).tex shouldBe "\\betrag{8337.50}"
            Numbers.amount(Money("0.5")).tex shouldBe "\\betrag{0.50}"
            Numbers.amount(Money("-1234.05")).tex shouldBe "\\betrag{-1234.05}"
        }
    }

    test("Einzelpreise nehmen das Makro der Beträge, mit mindestens zwei Nachkommastellen") {
        assertSoftly {
            Numbers.price(Price("95.00")).tex shouldBe "\\betrag{95.00}"
            Numbers.price(Price("95")).tex shouldBe "\\betrag{95.00}"
            Numbers.price(Price("0.3350")).tex shouldBe "\\betrag{0.335}"
        }
    }

    test("Mengen und Sätze ohne überflüssige Nullen") {
        assertSoftly {
            Numbers.quantity(BigDecimal("86.50")).tex shouldBe "\\menge{86.5}"
            Numbers.quantity(BigDecimal("-86.5")).tex shouldBe "\\menge{-86.5}"
            Numbers.quantity(BigDecimal("1")).tex shouldBe "\\menge{1}"
            Numbers.percent(Percent("19")).tex shouldBe "\\prozent{19}"
            Numbers.percent(Percent("2.5")).tex shouldBe "\\prozent{2.5}"
        }
    }

    test("Beträge in Euro, gleich in welcher Währung die Rechnung steht (#418)") {
        assertSoftly {
            Numbers.euroAmount(Money("1355.7")).tex shouldBe "\\eurobetrag{1355.70}"
            Numbers.euroAmount(Money("-16.26")).tex shouldBe "\\eurobetrag{-16.26}"
        }
    }

    test("ein Kurs mit allen Stellen, die er trägt, und ohne aufgefüllte Nullen (#418)") {
        assertSoftly {
            Numbers.exchangeRate(ExchangeRate("1.1685")).tex shouldBe "\\eurobetrag{1} = \\betrag{1.1685}"
            Numbers.exchangeRate(ExchangeRate("1.16853")).tex shouldBe "\\eurobetrag{1} = \\betrag{1.16853}"
            Numbers.exchangeRate(ExchangeRate("162.40")).tex shouldBe "\\eurobetrag{1} = \\betrag{162.4}"
            Numbers.exchangeRate(ExchangeRate("1000")).tex shouldBe "\\eurobetrag{1} = \\betrag{1000}"
        }
    }

    // stripTrailingZeros macht aus 1000 ein 1E+3, und siunitx setzte 1 × 10³.
    test("große runde Zahlen ohne Exponent") {
        assertSoftly {
            Numbers.quantity(BigDecimal("1000")).tex shouldBe "\\menge{1000}"
            Numbers.quantity(BigDecimal("12500.000")).tex shouldBe "\\menge{12500}"
            Numbers.price(Price("1000")).tex shouldBe "\\betrag{1000.00}"
            Numbers.amount(Money("20000")).tex shouldBe "\\betrag{20000.00}"
            Numbers.percent(Percent("100")).tex shouldBe "\\prozent{100}"
        }
    }

    // #14
    test("nichts wird stillschweigend gerundet") {
        assertSoftly {
            Numbers.price(Price("0.00001")).tex shouldBe "\\betrag{0.00001}"
            Numbers.price(Price("0.12345")).tex shouldBe "\\betrag{0.12345}"
            Numbers.quantity(BigDecimal("86.5678")).tex shouldBe "\\menge{86.5678}"
            Numbers.quantity(BigDecimal("0.0004")).tex shouldBe "\\menge{0.0004}"
            // "0 %" wäre nicht ungenau, sondern eine andere Steuerkategorie.
            Numbers.percent(Percent("0.0004")).tex shouldBe "\\prozent{0.0004}"
            Numbers.percent(Percent("19.1234")).tex shouldBe "\\prozent{19.1234}"
        }
    }
})
