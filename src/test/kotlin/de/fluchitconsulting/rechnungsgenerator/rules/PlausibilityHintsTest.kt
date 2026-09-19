package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.model.testItem
import de.fluchitconsulting.rechnungsgenerator.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
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

        val hint = CalculatedInvoice(invoice, config).messageOf(Rule.MENGE_NULL)

        hint shouldContain "Position 2"
        hint shouldContain "Menge 0"
    }

    context("GESAMTBETRAG_NULL (#214)") {
        fun ueberNull(type: InvoiceType) =
            CalculatedInvoice(testInvoice(type = type, items = listOf(testItem(unitPrice = "0.00"))), config)

        test("eine Rechnung über null bekommt einen Hinweis") {
            val hint = ueberNull(InvoiceType.INVOICE).messageOf(Rule.GESAMTBETRAG_NULL)

            hint shouldContain "0,00"
            hint shouldContain "gewollt"
        }

        test("eine Teilrechnung ebenso") {
            ueberNull(InvoiceType.PARTIAL).rules() shouldContain Rule.GESAMTBETRAG_NULL
        }

        test("die Abschlagsrechnung bricht stattdessen ab") {
            // ABSCHLAGSRECHNUNG_OHNE_BETRAG sagt es schon, und strenger.
            ueberNull(InvoiceType.PREPAYMENT).rules() shouldNotContain Rule.GESAMTBETRAG_NULL
        }

        test("eine Rechnung mit Betrag bekommt keinen") {
            CalculatedInvoice(testInvoice(), config).rules() shouldNotContain Rule.GESAMTBETRAG_NULL
        }

        test("neben MENGE_NULL bleibt er stehen") {
            // Der eine fragt nach der Position, der andere nach dem Dokument.
            val rules = CalculatedInvoice(testInvoice(items = listOf(testItem(quantity = "0"))), config).rules()

            rules shouldContain Rule.MENGE_NULL
            rules shouldContain Rule.GESAMTBETRAG_NULL
        }
    }

    context("Rechnung mehr als sechs Monate nach der Leistung (§14 Abs. 2 UStG)") {
        fun rechnungAm(datum: LocalDate) = CalculatedInvoice(testInvoice().copy(issueDate = datum), config)

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

            CalculatedInvoice(abschlagsrechnung, config).hints().shouldBeEmpty()
        }
    }
})
