package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.values.Money
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * Positionen und Beträge (#47): Eine Rechnung hat mindestens eine Position,
 * kein Einzelpreis ist negativ und der Gesamtbetrag auch nicht.
 */
class ItemRulesTest : FunSpec({

    val config = testSellerConfig()

    fun errorMessageOf(invoice: Invoice): String =
        shouldThrow<InvoiceValidationException> { InvoiceCalculator.calculate(invoice, config) }.message!!

    test("eine Rechnung ohne Position wird abgelehnt (BR-16)") {
        errorMessageOf(testInvoice(items = emptyList())) shouldContain "mindestens eine Position"
    }

    test("ein negativer Einzelpreis wird abgelehnt (BR-27)") {
        val message = errorMessageOf(testInvoice(items = listOf(testItem(unitPrice = "-95.00"))))

        message shouldContain "Position 1"
        message shouldContain "-95.00"
    }

    test("ein Einzelpreis von null bleibt zulässig") {
        // Eine Leistung, die ohne Berechnung auf der Rechnung stehen soll.
        InvoiceCalculator.calculate(testInvoice(items = listOf(testItem(), testItem(unitPrice = "0.00"))), config)
    }

    test("ein negativer Gesamtbetrag wird abgelehnt") {
        // Mit dem Einzelpreis ist nur noch eine negative Menge übrig, die dahin
        // führt. Eine Rechnung über einen negativen Betrag wäre eine
        // Gutschrift, und die gibt es noch nicht.
        val message = errorMessageOf(testInvoice(items = listOf(testItem(quantity = "-1"))))

        message shouldContain "Gesamtbetrag"
        message shouldContain "Gutschrift"
        // Bis #47 schlug hier Regel 8 der Absetzung zu. Ihre Meldung spricht von
        // abgesetzten Abschlägen – bei einer Rechnung, die gar keine hat.
        message shouldNotContain "Abschläge"
    }

    test("eine negative Korrekturzeile bleibt zulässig, solange der Gesamtbetrag es ist") {
        // Negative Mengen sind in #47 bewusst ausgeklammert: Die Norm erlaubt
        // sie, und ob das Projekt sie will, hängt an Gutschrift und Storno.
        val invoice =
            testInvoice(items = listOf(testItem(unitPrice = "100.00"), testItem(quantity = "-1", unitPrice = "30.00")))

        InvoiceCalculator.calculate(invoice, config).grossTotal shouldBe Money("83.30")
    }
})
