package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.InvoiceCalculator
import de.fluchitconsulting.rechnungsgenerator.Rule
import de.fluchitconsulting.rechnungsgenerator.hints
import de.fluchitconsulting.rechnungsgenerator.messageOf
import de.fluchitconsulting.rechnungsgenerator.rules
import de.fluchitconsulting.rechnungsgenerator.testInvoice
import de.fluchitconsulting.rechnungsgenerator.testItem
import de.fluchitconsulting.rechnungsgenerator.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate

/**
 * Hinweise auf Angaben, die zulässig, aber verdächtig sind (#47): Die Rechnung
 * entsteht, und der Hinweis steht nach dem Ergebnis.
 *
 * testInvoice trägt den Leistungszeitraum 01.–31.07.2026.
 */
class PlausibilityHintsTest : FunSpec({

    val config = testSellerConfig()

    test("eine Position mit Menge 0 bekommt einen Hinweis") {
        val invoice = testInvoice(items = listOf(testItem(), testItem(quantity = "0")))

        val hint = InvoiceCalculator.calculate(invoice, config).messageOf(Rule.MENGE_NULL)

        hint shouldContain "Position 2"
        hint shouldContain "Menge 0"
    }

    context("Rechnung mehr als sechs Monate nach der Leistung (§14 Abs. 2 UStG)") {
        fun rechnungAm(datum: LocalDate) = InvoiceCalculator.calculate(testInvoice().copy(issueDate = datum), config)

        test("danach gibt es einen Hinweis") {
            val hint = rechnungAm(LocalDate.of(2027, 2, 1)).messageOf(Rule.RECHNUNG_SPAET)

            hint shouldContain "sechs Monate"
            hint shouldContain "2026-07-31"
        }

        test("genau sechs Monate danach noch nicht") {
            rechnungAm(LocalDate.of(2027, 1, 31)).rules() shouldNotContain Rule.RECHNUNG_SPAET
        }

        test("er fängt den Tippfehler im Jahr") {
            // 2062 statt 2026: Die Rechnung entstünde 36 Jahre nach der Leistung.
            // Der Tippfehler in die andere Richtung, 2016, bricht schon ab: Dann
            // läge das Rechnungsdatum vor dem Ende der Leistung.
            rechnungAm(LocalDate.of(2062, 8, 14)).messageOf(Rule.RECHNUNG_SPAET) shouldContain "sechs Monate"
        }

        test("bei einer Abschlagsrechnung nicht – ihr Zeitraum ist eine Prognose") {
            val abschlagsrechnung = testInvoice(type = InvoiceType.PREPAYMENT).copy(issueDate = LocalDate.of(2027, 2, 1))

            InvoiceCalculator.calculate(abschlagsrechnung, config).hints().shouldBeEmpty()
        }
    }
})
