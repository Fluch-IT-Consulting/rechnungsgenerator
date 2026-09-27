package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.util.Locale

class DateFormatTest : FunSpec({

    val german = DateFormat(Language.GERMAN)
    val english = DateFormat(Language.BRITISH_ENGLISH)

    // Sicherstellen, dass die Formatierung nicht am Standard-Locale der JVM hängt:
    // Unter en_US wären Punkt und Komma vertauscht.
    beforeSpec { Locale.setDefault(Locale.US) }
    afterSpec { Locale.setDefault(Locale.getDefault()) }

    test("Leistungszeitraum: ganzer Monat wird zum Monatsnamen") {
        assertSoftly {
            german.servicePeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)).tex shouldBe "Juli 2026"
            // Februar hat 28 Tage – die Monatslänge muss berechnet, nicht geraten werden.
            german.servicePeriod(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)).tex shouldBe "Februar 2026"
            // Teilmonat und monatsübergreifend bleiben ein Datumsbereich.
            german.servicePeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15)).tex shouldBe
                "01.07.2026\\,–\\,15.07.2026"
            german.servicePeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31)).tex shouldBe
                "01.07.2026\\,–\\,31.08.2026"
        }
    }

    test("Datumsangaben") {
        assertSoftly {
            // ~ hinter dem Tag: Der Punkt ist kein Satzende (#143).
            german.date(LocalDate.of(2026, 8, 14)).tex shouldBe "14.~August 2026"
            german.date(LocalDate.of(2026, 3, 1)).tex shouldBe "1.~März 2026"
            // In den Metadaten ist ~ ein Zeichen und kein Abstand.
            german.dateInMetadata(LocalDate.of(2026, 8, 14)).tex shouldBe "14. August 2026"
            german.shortDate(LocalDate.of(2026, 7, 1)).tex shouldBe "01.07.2026"
        }
    }
    test("ein voraussichtlicher Zeitraum wird in Monaten geschrieben") {
        assertSoftly {
            // Volle Monate: Der Kalendermonat genügt (UStAE 14.8 Abs. 1 und 4).
            german.monthPeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 10, 31)).tex shouldBe
                "Juni bis Oktober 2026"
            german.monthPeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)).tex shouldBe "Juni 2026"
            german.monthPeriod(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 3, 31)).tex shouldBe
                "Dezember 2026 bis März 2027"
            // Angebrochene Randmonate bleiben datiert – sonst wäre die Angabe
            // nicht gröber, sondern eine andere.
            german.monthPeriod(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 10, 20)).tex shouldBe
                "15.06.2026\\,–\\,20.10.2026"
        }
    }

    context("Englisch (#377)") {
        test("das lange Datum britisch, ohne Punkt und ohne Umbruch nach dem Tag") {
            english.date(LocalDate.of(2026, 8, 14)).tex shouldBe "14~August 2026"
            english.dateInMetadata(LocalDate.of(2026, 8, 14)).tex shouldBe "14 August 2026"
        }

        test("das kurze Datum nach ISO 8601, das niemand als 7. Januar liest") {
            english.shortDate(LocalDate.of(2026, 7, 1)).tex shouldBe "2026-07-01"
        }

        test("ein ganzer Monat als Monatsname, sonst zwei Daten") {
            english.servicePeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)).tex shouldBe "July 2026"
            english.servicePeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15)).tex shouldBe
                "2026-07-01\\,–\\,2026-07-15"
        }

        test("ein voraussichtlicher Zeitraum in Monaten, mit to (#405)") {
            assertSoftly {
                english.monthPeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 10, 31)).tex shouldBe
                    "June to October 2026"
                english.monthPeriod(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 3, 31)).tex shouldBe
                    "December 2026 to March 2027"
            }
        }
    }
})
