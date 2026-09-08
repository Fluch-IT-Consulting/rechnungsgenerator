package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.values.InvoiceType
import de.fluchitconsulting.rechnung.values.Money
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate

/**
 * Die Regeln der Abschlagsrechnung. Die Absetzung in der Schlussrechnung ist
 * hier noch nicht dabei – die Abschlagsrechnung steht für sich.
 */
class PrepaymentRulesTest : FunSpec({

    val config = testSellerConfig()

    test("eine Abschlagsrechnung rechnet wie jede andere Rechnung") {
        val invoice =
            testInvoice(
                type = InvoiceType.PREPAYMENT,
                items = listOf(testItem(quantity = "1", unitPrice = "4000.00")),
                receivedOn = LocalDate.of(2026, 8, 10),
            )

        val totals = InvoiceCalculator.calculate(invoice, config)

        totals.netTotal shouldBe Money("4000.00")
        totals.grossTotal shouldBe Money("4760.00")
    }

    test("vereinnahmt-am gehört nur auf eine Abschlagsrechnung") {
        val exception =
            shouldThrow<InvoiceValidationException> {
                InvoiceCalculator.calculate(testInvoice(receivedOn = LocalDate.of(2026, 8, 10)), config)
            }

        exception.message!! shouldContain "abschlagsrechnung"
        exception.message!! shouldContain "Rechnung"
    }

    test("die Meldung nennt das Dokument, auf dem die Angabe steht") {
        val exception =
            shouldThrow<InvoiceValidationException> {
                InvoiceCalculator.calculate(
                    testInvoice(type = InvoiceType.FINAL, receivedOn = LocalDate.of(2026, 8, 10)),
                    config,
                )
            }

        exception.message!! shouldContain "Schlussrechnung"
    }

    test("eine Vereinnahmung nach dem Rechnungsdatum belegt für diese Rechnung nichts") {
        // Das Feld ist für den Fall gedacht, dass das Geld schon vor der
        // Rechnungsstellung da war. Steht der Zeitpunkt noch nicht fest,
        // bleibt die Angabe weg – ein künftiges Datum wäre eine Behauptung.
        val invoice =
            testInvoice(
                type = InvoiceType.PREPAYMENT,
                receivedOn = LocalDate.of(2026, 8, 15),
            )

        val exception = shouldThrow<InvoiceValidationException> { InvoiceCalculator.calculate(invoice, config) }

        exception.message!! shouldContain "2026-08-15"
    }
})
