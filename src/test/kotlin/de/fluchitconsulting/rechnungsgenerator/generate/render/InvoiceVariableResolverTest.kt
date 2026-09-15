package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.calculation.InvoiceCalculator
import de.fluchitconsulting.rechnungsgenerator.model.testCancelled
import de.fluchitconsulting.rechnungsgenerator.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.model.testItem
import de.fluchitconsulting.rechnungsgenerator.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.rules.accepted
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class InvoiceVariableResolverTest : FunSpec({

    val config = testSellerConfig()

    val storno =
        InvoiceVariableResolver(
            InvoiceCalculator
                .calculate(
                    testInvoice(
                        type = InvoiceType.CANCELLATION,
                        items = listOf(testItem(quantity = "-1")),
                        cancelled = testCancelled(invoiceNumber = "R26-042", issueDate = LocalDate.of(2026, 7, 15)),
                    ),
                    config,
                ).accepted(),
        )

    val invoice = InvoiceVariableResolver(InvoiceCalculator.calculate(testInvoice(), config).accepted())

    test("auf einer Stornorechnung: Nummer und Datum der Ursprungsrechnung, je zusammengehalten") {
        storno.resolve("URSPRUNGSNUMMER").tex shouldBe "\\zusammen{R26-042}"
        storno.resolve("URSPRUNGSDATUM").tex shouldBe "\\zusammen{15.~Juli 2026}"
    }

    test("auf einer anderen Rechnungsart gibt es die Ursprungsrechnung nicht") {
        shouldThrow<IllegalStateException> {
            invoice.resolve("URSPRUNGSDATUM")
        }.message shouldBe "<<URSPRUNGSDATUM>> gibt es nur auf einer Stornorechnung, nicht auf einer Rechnung."
    }

    test("ein unbekannter Name wirft, auch auf der passenden Rechnungsart") {
        shouldThrow<IllegalStateException> {
            storno.resolve("URSPRUNGSNUMER")
        }.message shouldBe "Unbekannte Variable <<URSPRUNGSNUMER>>."
    }

    test("im Text eingesetzt: der feste Teil maskiert, die Werte zusammengehalten") {
        "Storno zu <<URSPRUNGSNUMMER>> vom <<URSPRUNGSDATUM>> & fertig".escapeLatex(storno).tex shouldBe
            "Storno zu \\zusammen{R26-042} vom \\zusammen{15.~Juli 2026} \\& fertig"
    }
})
