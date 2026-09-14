package de.fluchitconsulting.rechnungsgenerator

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Die Prüfung, hinter der gesetzt wird (ADR-0007). Dass Renderer und
 * `prepaymentOf` nur eine [AcceptedInvoice] annehmen, prüft der Compiler; hier
 * steht, wann es eine gibt.
 */
class AcceptedInvoiceTest : FunSpec({

    val config = testSellerConfig()

    test("eine Rechnung ohne Abbruch wird angenommen und trägt ihre Hinweise") {
        // Menge 0 ist zulässig, aber ein Hinweis (MENGE_NULL).
        val calculated = InvoiceCalculator.calculate(testInvoice(items = listOf(testItem(quantity = "0"))), config)

        val accepted = AcceptedInvoice.check(calculated).shouldBeInstanceOf<Checked.Accepted>().invoice

        accepted.calculated shouldBe calculated
        accepted.hints.rules() shouldBe listOf(Rule.MENGE_NULL)
    }

    test("eine Rechnung mit Abbruch wird abgelehnt, mit allen Abbrüchen – und trägt auch ihre Hinweise") {
        val invoice =
            testInvoice(
                items = listOf(testItem(quantity = "0", unitPrice = "-1.00")),
                paymentTermDaysOverride = -5,
            )

        val rejected = AcceptedInvoice.check(InvoiceCalculator.calculate(invoice, config)).shouldBeInstanceOf<Checked.Rejected>()

        rejected.aborts.rules() shouldBe listOf(Rule.EINZELPREIS_NEGATIV, Rule.ZAHLUNGSZIEL_NEGATIV)
        // Die Regeltests lesen ihre Befunde auch von abgelehnten Rechnungen (#105).
        rejected.findings.rules() shouldBe listOf(Rule.EINZELPREIS_NEGATIV, Rule.ZAHLUNGSZIEL_NEGATIV, Rule.MENGE_NULL)
    }
})
