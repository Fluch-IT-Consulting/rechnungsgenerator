package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.model.testSellerConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** Die Freitextangaben zum Kunden (#196, #190). */
class CustomerRulesTest : FunSpec({

    fun mitAnsprechpartner(ansprechpartner: String?) =
        CalculatedInvoice(testInvoice(contactPoint = ansprechpartner), testSellerConfig())

    fun mitKundennummer(kundennummer: String?) =
        CalculatedInvoice(testInvoice(buyerIdentifier = kundennummer), testSellerConfig())

    context("Ansprechpartner") {
        test("ohne Ansprechpartner gibt es nichts zu prüfen") {
            mitAnsprechpartner(null).aborts().shouldBeEmpty()
        }

        test("eine Person oder eine Abteilung geht durch, samt Anrede, wenn jemand eine schreibt") {
            mitAnsprechpartner("Herrn Max Beispiel").aborts().shouldBeEmpty()
            mitAnsprechpartner("Kreditorenbuchhaltung").aborts().shouldBeEmpty()
        }

        test("ein leerer Ansprechpartner wird abgelehnt") {
            // ansprechpartner: "" nennt niemanden; gemeint war vermutlich jemand.
            mitAnsprechpartner("").messageOf(Rule.ANSPRECHPARTNER_LEER) shouldContain "kunde: ansprechpartner"
        }

        test("einer aus lauter Leerzeichen ist leer, er trägt nicht bloß Leerraum") {
            // Genau diese eine Regel zum Ansprechpartner: Wer leer ist, wird
            // nicht zusätzlich für seinen Leerraum gerügt.
            mitAnsprechpartner("   ").rules().filter { it.name.startsWith("ANSPRECHPARTNER_") } shouldBe
                listOf(Rule.ANSPRECHPARTNER_LEER)
        }

        test("Leerraum am Rand wird abgelehnt und die gemeinte Fassung genannt") {
            val message = mitAnsprechpartner(" Einkauf").messageOf(Rule.ANSPRECHPARTNER_MIT_LEERRAUM)

            message shouldContain "Leerraum"
            message shouldContain "'Einkauf'"
        }
    }

    context("Kundennummer") {
        test("ohne Kundennummer gibt es nichts zu prüfen") {
            mitKundennummer(null).aborts().shouldBeEmpty()
        }

        test("jede Form geht durch, ein Muster gibt es nicht") {
            mitKundennummer("K-17").aborts().shouldBeEmpty()
            mitKundennummer("0017").aborts().shouldBeEmpty()
        }

        test("eine leere Kundennummer wird abgelehnt") {
            mitKundennummer("").messageOf(Rule.KUNDENNUMMER_LEER) shouldContain "kunde: kundennummer"
        }

        test("eine aus lauter Leerzeichen ist leer, sie trägt nicht bloß Leerraum") {
            mitKundennummer("  ").rules().filter { it.name.startsWith("KUNDENNUMMER_") } shouldBe
                listOf(Rule.KUNDENNUMMER_LEER)
        }

        test("Leerraum am Rand wird abgelehnt und die gemeinte Fassung genannt") {
            val message = mitKundennummer("K-17 ").messageOf(Rule.KUNDENNUMMER_MIT_LEERRAUM)

            message shouldContain "Leerraum"
            message shouldContain "'K-17'"
        }

        test("Ansprechpartner und Kundennummer werden beide gerügt, nicht nur der erste") {
            val beide = CalculatedInvoice(testInvoice(contactPoint = "", buyerIdentifier = ""), testSellerConfig())

            beide.rules().filter { it.name.startsWith("ANSPRECHPARTNER_") || it.name.startsWith("KUNDENNUMMER_") } shouldBe
                listOf(Rule.ANSPRECHPARTNER_LEER, Rule.KUNDENNUMMER_LEER)
        }
    }
})
