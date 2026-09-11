package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.values.InvoiceType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
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

        val hint = InvoiceCalculator.calculate(invoice, config).hints.single()

        hint shouldContain "Position 2"
        hint shouldContain "Menge 0"
    }

    context("Rechnung mehr als sechs Monate nach der Leistung (§14 Abs. 2 UStG)") {
        fun hintsAm(datum: LocalDate) = InvoiceCalculator.calculate(testInvoice().copy(issueDate = datum), config).hints

        test("danach gibt es einen Hinweis") {
            val hint = hintsAm(LocalDate.of(2027, 2, 1)).single()

            hint shouldContain "sechs Monate"
            hint shouldContain "2026-07-31"
        }

        test("genau sechs Monate danach noch nicht") {
            hintsAm(LocalDate.of(2027, 1, 31)).shouldBeEmpty()
        }

        test("er fängt den Tippfehler im Jahr") {
            // 2062 statt 2026: Die Rechnung entstünde 36 Jahre nach der Leistung.
            // Der Tippfehler in die andere Richtung, 2016, bricht schon ab: Dann
            // läge das Rechnungsdatum vor dem Ende der Leistung.
            hintsAm(LocalDate.of(2062, 8, 14)).single() shouldContain "sechs Monate"
        }

        test("bei einer Abschlagsrechnung nicht – ihr Zeitraum ist eine Prognose") {
            val abschlagsrechnung = testInvoice(type = InvoiceType.PREPAYMENT).copy(issueDate = LocalDate.of(2027, 2, 1))

            InvoiceCalculator.calculate(abschlagsrechnung, config).hints.shouldBeEmpty()
        }
    }
})
