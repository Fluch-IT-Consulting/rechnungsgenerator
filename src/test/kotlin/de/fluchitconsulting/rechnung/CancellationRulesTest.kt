package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.model.CancelledInvoice
import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.values.InvoiceType
import de.fluchitconsulting.rechnung.values.Money
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate

/**
 * Die Stornorechnung (#63, ADR-0006): Sie nennt ihre Ursprungsrechnung, ihr
 * Gesamtbetrag ist negativ, und sie fordert nicht zur Zahlung auf.
 */
class CancellationRulesTest : FunSpec({

    val config = testSellerConfig()

    // Das Gegenstück zur Ursprungsrechnung: dieselbe Position, negative Menge.
    fun storno(
        cancelled: CancelledInvoice? = testCancelled(),
        quantity: String = "-1",
    ) = testInvoice(
        type = InvoiceType.CANCELLATION,
        items = listOf(testItem(quantity = quantity, unitPrice = "1000.00")),
        cancelled = cancelled,
    )

    fun messageFor(invoice: Invoice, rule: Rule): String = InvoiceCalculator.calculate(invoice, config).messageOf(rule)

    test("ein Storno mit Bezug und negativer Menge geht durch") {
        val calculated = InvoiceCalculator.calculate(storno(), config)

        calculated.aborts().shouldBeEmpty()
        calculated.grossTotal shouldBe Money("-1190.00")
    }

    test("ein Storno ohne Bezug wird abgelehnt") {
        val message = messageFor(storno(cancelled = null), Rule.STORNO_OHNE_BEZUG)

        message shouldContain "'storniert'"
        // Die Meldung sagt, warum es die Angabe braucht, nicht nur dass sie fehlt.
        message shouldContain "BG-3"
    }

    test("ein Bezug ohne Storno wird ebenso abgelehnt") {
        // Dieselbe Symmetrie wie bei 'vereinnahmt-am' und der Abschlagsrechnung.
        val message = messageFor(testInvoice(cancelled = testCancelled()), Rule.BEZUG_OHNE_STORNO)

        message shouldContain "'storniert'"
        message shouldContain "Rechnung"
    }

    test("ein Storno auf die eigene Nummer wird abgelehnt") {
        val message =
            messageFor(storno(cancelled = testCancelled(invoiceNumber = "R26-001")), Rule.STORNO_AUF_SICH_SELBST)

        message shouldContain "R26-001"
        message shouldContain "eigene Nummer"
    }

    test("eine Ursprungsrechnung nach dem Rechnungsdatum wird abgelehnt") {
        // Die Rechnung des Bausatzes ist auf den 14.08.2026 datiert.
        val message =
            messageFor(
                storno(cancelled = testCancelled(issueDate = LocalDate.of(2026, 9, 1))),
                Rule.URSPRUNGSRECHNUNG_SPAETER_DATIERT,
            )

        message shouldContain "2026-09-01"
        message shouldContain "was es schon gibt"
    }

    test("ein Storno über einen positiven Betrag ist ein Widerspruch") {
        val message = messageFor(storno(quantity = "1"), Rule.STORNO_NICHT_NEGATIV)

        message shouldContain "negativem Vorzeichen"
    }

    test("ein Storno über null ebenso") {
        // "Nicht negativ" und nicht "positiv": Ein Storno über 0,00 hebt nichts
        // auf und wäre trotzdem durchgegangen, stünde hier isPositive().
        InvoiceCalculator
            .calculate(storno(quantity = "0"), config)
            .rules() shouldContain Rule.STORNO_NICHT_NEGATIV
    }

    test("die Regel gegen negative Gesamtbeträge gilt im Storno nicht") {
        InvoiceCalculator.calculate(storno(), config).rules() shouldNotContain Rule.GESAMTBETRAG_NEGATIV
    }

    test("ein Storno hat keine Fälligkeit") {
        // Es fordert nicht zur Zahlung auf, sondern kündigt eine Erstattung an.
        InvoiceCalculator.calculate(storno(), config).dueDate shouldBe null
    }

    test("Abschläge gehören auch nicht in ein Storno") {
        // Ohne neue Regel: Die bestehende sagt "nur in einer Schlussrechnung".
        val message =
            messageFor(
                storno().copy(prepayments = listOf(testPrepayment())),
                Rule.ABSCHLAEGE_FALSCHE_ART,
            )

        message shouldContain "Stornorechnung"
    }
})
