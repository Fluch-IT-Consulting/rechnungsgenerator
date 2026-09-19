package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.model.testSellerConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** Die Freitextangaben zum Kunden (#196). */
class CustomerRulesTest : FunSpec({

    fun mitAnsprechpartner(ansprechpartner: String?) =
        CalculatedInvoice(testInvoice(contactPoint = ansprechpartner), testSellerConfig())

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
})
