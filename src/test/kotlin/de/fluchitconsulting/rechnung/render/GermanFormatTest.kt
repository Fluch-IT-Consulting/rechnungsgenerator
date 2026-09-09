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
            GermanFormat.amount(Money("8337.50")).tex shouldBe "8.337,50"
            GermanFormat.amount(Money("0.5")).tex shouldBe "0,50"
            GermanFormat.amount(Money("-1234.05")).tex shouldBe "-1.234,05"
            GermanFormat.amountWithCurrency(Money("9921.63")).tex shouldBe "9.921,63~€"
        }
    }

    test("Einzelpreise zeigen bis zu vier Nachkommastellen") {
        assertSoftly {
            GermanFormat.price(Price("95.00")).tex shouldBe "95,00"
            // Der Sub-Cent-Preis bleibt sichtbar – das ist der Sinn von Price.
            GermanFormat.price(Price("0.335")).tex shouldBe "0,335"
            GermanFormat.price(Price("0.0425")).tex shouldBe "0,0425"
        }
    }

    test("Mengen ohne überflüssige Nullen") {
        assertSoftly {
            GermanFormat.quantity(BigDecimal("86.5")).tex shouldBe "86,5"
            GermanFormat.quantity(BigDecimal("1")).tex shouldBe "1"
            GermanFormat.quantity(BigDecimal("1000")).tex shouldBe "1.000"
        }
    }

    // Aus dem geschützten Leerzeichen, das GermanFormat setzt, wird beim
    // Maskieren eine Tilde – in LaTeX die Abkürzung für \nobreakspace: gleich
    // unbrechbar, aber dehnbar wie ein gewöhnlicher Zwischenraum. Das nackte
    // U+00A0 bliebe fest und risse im Blocksatz eine zu schmale Lücke.
    //
    // Das Prozentzeichen ist maskiert: Die Formatierung liefert ein fertiges
    // LaTeX-Fragment, damit keine Aufrufstelle daran denken muss.
    test("Steuersätze in Prozentschreibweise, mit maskiertem Prozentzeichen") {
        assertSoftly {
            GermanFormat.percent(Percent("19")).tex shouldBe "19~\\%"
            GermanFormat.percent(Percent("7")).tex shouldBe "7~\\%"
            GermanFormat.percent(Percent("2.5")).tex shouldBe "2,5~\\%"
        }
    }

    test("Leistungszeitraum: ganzer Monat wird zum Monatsnamen") {
        assertSoftly {
            GermanFormat.servicePeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)).tex shouldBe "Juli 2026"
            // Februar hat 28 Tage – die Monatslänge muss berechnet, nicht geraten werden.
            GermanFormat.servicePeriod(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)).tex shouldBe "Februar 2026"
            // Teilmonat und monatsübergreifend bleiben ein Datumsbereich.
            GermanFormat.servicePeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15)).tex shouldBe
                "01.07.2026\u2009–\u200915.07.2026"
            GermanFormat.servicePeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31)).tex shouldBe
                "01.07.2026\u2009–\u200931.08.2026"
        }
    }

    /**
     * Der Kern von #14: Feste Muster haben hier gerundet, ohne es zu sagen.
     * Ein Einzelpreis von 0,00001 € wurde zu `0,00` – und dann stand auf der
     * Rechnung "10.000 zu 0,00 € = 0,10 €".
     */
    test("nichts wird stillschweigend gerundet") {
        assertSoftly {
            // Preis: mindestens zwei Stellen, sonst so viele wie da sind.
            GermanFormat.price(Price("0.00001")).tex shouldBe "0,00001"
            GermanFormat.price(Price("0.12345")).tex shouldBe "0,12345"
            GermanFormat.price(Price("95")).tex shouldBe "95,00"
            // Menge: keine Untergrenze.
            GermanFormat.quantity(BigDecimal("86.5678")).tex shouldBe "86,5678"
            GermanFormat.quantity(BigDecimal("0.0004")).tex shouldBe "0,0004"
            GermanFormat.quantity(BigDecimal("1000")).tex shouldBe "1.000"
            // Satz: "0 %" wäre auf einer Rechnung nicht ungenau, sondern eine
            // andere Aussage – die Kategorie unterscheidet steuerfrei von 0 %.
            GermanFormat.percent(Percent("0.0004")).tex shouldBe "0,0004~\\%"
            GermanFormat.percent(Percent("19.1234")).tex shouldBe "19,1234~\\%"
        }
    }

    test("Datumsangaben") {
        assertSoftly {
            GermanFormat.date(LocalDate.of(2026, 8, 14)).tex shouldBe "14. August 2026"
            GermanFormat.date(LocalDate.of(2026, 3, 1)).tex shouldBe "1. März 2026"
            GermanFormat.shortDate(LocalDate.of(2026, 7, 1)).tex shouldBe "01.07.2026"
        }
    }
    test("ein voraussichtlicher Zeitraum wird in Monaten geschrieben") {
        assertSoftly {
            // Volle Monate: Der Kalendermonat genügt (UStAE 14.8 Abs. 1 und 4).
            GermanFormat.monthPeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 10, 31)).tex shouldBe
                "Juni bis Oktober 2026"
            GermanFormat.monthPeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)).tex shouldBe "Juni 2026"
            GermanFormat.monthPeriod(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 3, 31)).tex shouldBe
                "Dezember 2026 bis März 2027"
            // Angebrochene Randmonate bleiben datiert – sonst wäre die Angabe
            // nicht gröber, sondern eine andere.
            GermanFormat.monthPeriod(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 10, 20)).tex shouldBe
                "15.06.2026\u2009–\u200920.10.2026"
        }
    }
})
