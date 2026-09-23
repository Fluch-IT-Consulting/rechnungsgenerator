package de.fluchitconsulting.rechnungsgenerator.accepted

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.model.testCancelled
import de.fluchitconsulting.rechnungsgenerator.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.model.testItem
import de.fluchitconsulting.rechnungsgenerator.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceVariable
import de.fluchitconsulting.rechnungsgenerator.values.TextValue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class InvoiceVariableResolverTest : FunSpec({

    val config = testSellerConfig()

    val storno =
        InvoiceVariableResolver(
            CalculatedInvoice(
                testInvoice(
                    type = InvoiceType.CANCELLATION,
                    items = listOf(testItem(quantity = "-1")),
                    cancelled = testCancelled(invoiceNumber = "R26-042", issueDate = LocalDate.of(2026, 7, 15)),
                ),
                config,
            ).accepted(),
        )

    val invoice = InvoiceVariableResolver(CalculatedInvoice(testInvoice(), config).accepted())

    test("auf einer Stornorechnung: Nummer und Datum der Ursprungsrechnung, noch ohne Format") {
        storno.resolve(InvoiceVariable.URSPRUNGSNUMMER) shouldBe TextValue.Text("R26-042")
        storno.resolve(InvoiceVariable.URSPRUNGSDATUM) shouldBe TextValue.Date(LocalDate.of(2026, 7, 15))
    }

    test("auf einer anderen Rechnungsart gibt es die Ursprungsrechnung nicht") {
        shouldThrow<IllegalStateException> {
            invoice.resolve(InvoiceVariable.URSPRUNGSDATUM)
        }.message shouldBe "<<URSPRUNGSDATUM>> gibt es nur auf einer Stornorechnung, nicht auf einer Rechnung."
    }
})
