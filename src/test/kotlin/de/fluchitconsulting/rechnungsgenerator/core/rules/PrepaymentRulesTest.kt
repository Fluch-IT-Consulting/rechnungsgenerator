package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testItem
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate

class PrepaymentRulesTest : FunSpec({

    val config = testSellerConfig()

    test("eine Abschlagsrechnung rechnet wie jede andere Rechnung") {
        val invoice =
            testInvoice(
                type = InvoiceType.PREPAYMENT,
                items = listOf(testItem(quantity = "1", unitPrice = "4000.00")),
                receivedOn = LocalDate.of(2026, 8, 10),
            )

        val totals = CalculatedInvoice(invoice, config)

        totals.netTotal shouldBe Money("4000.00")
        totals.grossTotal shouldBe Money("4760.00")
    }

    test("eine Vereinnahmung nach dem Rechnungsdatum belegt für diese Rechnung nichts") {
        val invoice =
            testInvoice(
                type = InvoiceType.PREPAYMENT,
                receivedOn = LocalDate.of(2026, 8, 15),
            )

        val message = CalculatedInvoice(invoice, config).messageOf(Rule.VEREINNAHMUNG_NACH_RECHNUNGSDATUM)

        message shouldContain "2026-08-15"
    }

    test("auf einer anderen Rechnungsart schweigen diese Regeln") {
        // #97. Gegen dieselbe Rechnung ohne das Feld darf nur der eine Befund hinzukommen.
        val absent = CalculatedInvoice(testInvoice(), config)
        val present = CalculatedInvoice(testInvoice(receivedOn = LocalDate.of(2026, 8, 15)), config)

        present.rules() shouldContainExactlyInAnyOrder absent.rules() + Rule.VEREINNAHMUNG_FALSCHE_ART
    }
})
