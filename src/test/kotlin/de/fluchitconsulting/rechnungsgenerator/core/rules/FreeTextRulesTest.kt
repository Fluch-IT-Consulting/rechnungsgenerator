package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** Die Freitextangaben der Rechnung (#196, #190, #191, #209). */
class FreeTextRulesTest : FunSpec({

    fun withContactPoint(contactPoint: String?) =
        CalculatedInvoice(testInvoice(contactPoint = contactPoint), testSellerConfig())

    fun withBuyerIdentifier(buyerIdentifier: String?) =
        CalculatedInvoice(testInvoice(buyerIdentifier = buyerIdentifier), testSellerConfig())

    fun withSalesOrderReference(salesOrderReference: String?) =
        CalculatedInvoice(testInvoice(salesOrderReference = salesOrderReference), testSellerConfig())

    fun withIntroduction(introduction: String?) =
        CalculatedInvoice(testInvoice(introduction = introduction), testSellerConfig())

    context("Ansprechpartner") {
        test("ohne Ansprechpartner gibt es nichts zu prüfen") {
            withContactPoint(null).aborts().shouldBeEmpty()
        }

        test("eine Person oder eine Abteilung geht durch, samt Anrede, wenn jemand eine schreibt") {
            withContactPoint("Herrn Max Beispiel").aborts().shouldBeEmpty()
            withContactPoint("Kreditorenbuchhaltung").aborts().shouldBeEmpty()
        }

        test("ein leerer Ansprechpartner wird abgelehnt") {
            withContactPoint("").messageOf(Rule.ANSPRECHPARTNER_LEER) shouldContain "kunde: ansprechpartner"
        }

        test("einer aus lauter Leerzeichen ist leer, er trägt nicht bloß Leerraum") {
            withContactPoint("   ").rules().filter { it.name.startsWith("ANSPRECHPARTNER_") } shouldBe
                listOf(Rule.ANSPRECHPARTNER_LEER)
        }

        test("Leerraum am Rand wird abgelehnt und die gemeinte Fassung genannt") {
            val message = withContactPoint(" Einkauf").messageOf(Rule.ANSPRECHPARTNER_MIT_LEERRAUM)

            message shouldContain "Leerraum"
            message shouldContain "'Einkauf'"
        }
    }

    context("Kundennummer") {
        test("ohne Kundennummer gibt es nichts zu prüfen") {
            withBuyerIdentifier(null).aborts().shouldBeEmpty()
        }

        test("jede Form geht durch, ein Muster gibt es nicht") {
            withBuyerIdentifier("K-17").aborts().shouldBeEmpty()
            withBuyerIdentifier("0017").aborts().shouldBeEmpty()
        }

        test("eine leere Kundennummer wird abgelehnt") {
            withBuyerIdentifier("").messageOf(Rule.KUNDENNUMMER_LEER) shouldContain "kunde: kundennummer"
        }

        test("eine aus lauter Leerzeichen ist leer, sie trägt nicht bloß Leerraum") {
            withBuyerIdentifier("  ").rules().filter { it.name.startsWith("KUNDENNUMMER_") } shouldBe
                listOf(Rule.KUNDENNUMMER_LEER)
        }

        test("Leerraum am Rand wird abgelehnt und die gemeinte Fassung genannt") {
            val message = withBuyerIdentifier("K-17 ").messageOf(Rule.KUNDENNUMMER_MIT_LEERRAUM)

            message shouldContain "Leerraum"
            message shouldContain "'K-17'"
        }

        test("Ansprechpartner und Kundennummer werden beide gerügt, nicht nur der erste") {
            val both = CalculatedInvoice(testInvoice(contactPoint = "", buyerIdentifier = ""), testSellerConfig())

            both.rules().filter { it.name.startsWith("ANSPRECHPARTNER_") || it.name.startsWith("KUNDENNUMMER_") } shouldBe
                listOf(Rule.ANSPRECHPARTNER_LEER, Rule.KUNDENNUMMER_LEER)
        }
    }

    context("Auftragsnummer") {
        test("ohne Auftragsnummer gibt es nichts zu prüfen") {
            withSalesOrderReference(null).aborts().shouldBeEmpty()
        }

        test("jede Form geht durch, ein Muster gibt es nicht") {
            withSalesOrderReference("A-2026-014").aborts().shouldBeEmpty()
        }

        test("eine leere Auftragsnummer wird abgelehnt und nennt den Schlüssel") {
            withSalesOrderReference("").messageOf(Rule.AUFTRAGSNUMMER_LEER) shouldContain "'auftragsnummer'"
        }

        test("Leerraum am Rand wird abgelehnt und die gemeinte Fassung genannt") {
            val message = withSalesOrderReference(" A-2026-014").messageOf(Rule.AUFTRAGSNUMMER_MIT_LEERRAUM)

            message shouldContain "Leerraum"
            message shouldContain "'A-2026-014'"
        }
    }

    context("Einleitung") {
        test("ohne Einleitung gibt es nichts zu prüfen") {
            withIntroduction(null).aborts().shouldBeEmpty()
        }

        test("ein Satz mit Satzzeichen geht durch") {
            withIntroduction("Für den Workshop am 04.08.2026 in Würzburg berechnen wir Folgendes:")
                .aborts()
                .shouldBeEmpty()
        }

        test("eine leere Einleitung wird abgelehnt und nennt den Schlüssel") {
            withIntroduction("").messageOf(Rule.EINLEITUNG_LEER) shouldContain "'einleitung'"
        }

        test("der Zeilenumbruch am Ende eines Blocks mit | wird abgelehnt, die Meldung nennt >-") {
            val message = withIntroduction("Für Projekt A berechnen wir:\n").messageOf(Rule.EINLEITUNG_MIT_LEERRAUM)

            message shouldContain "'>-'"
            message shouldContain "'Für Projekt A berechnen wir:'"
        }
    }
})
