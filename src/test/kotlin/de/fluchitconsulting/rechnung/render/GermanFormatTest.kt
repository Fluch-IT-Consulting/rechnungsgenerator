package de.fluchitconsulting.rechnung.render

import de.fluchitconsulting.rechnung.values.Money
import de.fluchitconsulting.rechnung.values.Percent
import de.fluchitconsulting.rechnung.values.Price
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

class GermanFormatTest : FunSpec({

    // Sicherstellen, dass die Formatierung nicht am Standard-Locale der JVM hängt:
    // Unter en_US wären Punkt und Komma vertauscht.
    beforeSpec { Locale.setDefault(Locale.US) }
    afterSpec { Locale.setDefault(Locale.getDefault()) }

    test("Beträge mit Tausenderpunkt und Dezimalkomma") {
        assertSoftly {
            GermanFormat.amount(Money("8337.50")) shouldBe "8.337,50"
            GermanFormat.amount(Money("0.5")) shouldBe "0,50"
            GermanFormat.amount(Money("-1234.05")) shouldBe "-1.234,05"
            GermanFormat.amountWithCurrency(Money("9921.63")) shouldBe "9.921,63\u00A0€"
        }
    }

    test("Einzelpreise zeigen bis zu vier Nachkommastellen") {
        assertSoftly {
            GermanFormat.price(Price("95.00")) shouldBe "95,00"
            // Der Sub-Cent-Preis bleibt sichtbar – das ist der Sinn von Price.
            GermanFormat.price(Price("0.335")) shouldBe "0,335"
            GermanFormat.price(Price("0.0425")) shouldBe "0,0425"
        }
    }

    test("Mengen ohne überflüssige Nullen") {
        assertSoftly {
            GermanFormat.quantity(BigDecimal("86.5")) shouldBe "86,5"
            GermanFormat.quantity(BigDecimal("1")) shouldBe "1"
            GermanFormat.quantity(BigDecimal("1000")) shouldBe "1.000"
        }
    }

    // \u00A0 ist ein geschütztes Leerzeichen: "19 %" und "9.921,63 €" sollen
    // nicht am Zeilenende auseinandergerissen werden. Im Test steht die
    // Escape-Sequenz statt des Zeichens, sonst wäre der Unterschied unsichtbar.
    test("Steuersätze in Prozentschreibweise, mit geschütztem Leerzeichen") {
        assertSoftly {
            GermanFormat.percent(Percent("19")) shouldBe "19\u00A0%"
            GermanFormat.percent(Percent("7")) shouldBe "7\u00A0%"
            GermanFormat.percent(Percent("2.5")) shouldBe "2,5\u00A0%"
        }
    }

    test("Datumsangaben") {
        assertSoftly {
            GermanFormat.date(LocalDate.of(2026, 8, 14)) shouldBe "14. August 2026"
            GermanFormat.date(LocalDate.of(2026, 3, 1)) shouldBe "1. März 2026"
            GermanFormat.shortDate(LocalDate.of(2026, 7, 1)) shouldBe "01.07.2026"
        }
    }
})
