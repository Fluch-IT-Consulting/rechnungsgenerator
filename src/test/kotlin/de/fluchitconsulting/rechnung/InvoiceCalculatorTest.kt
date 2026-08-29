package de.fluchitconsulting.rechnung

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.LocalDate

class InvoiceCalculatorTest : FunSpec({

    val config = testSellerConfig()

    test("Positionsnetto wird kaufmännisch auf Cent gerundet") {
        // 1.25 × 98.99 = 123.7375 -> 123.74
        val invoice = testInvoice(items = listOf(testItem(quantity = "1.25", unitPrice = "98.99")))

        val totals = InvoiceCalculator.calculate(invoice, config)

        totals.lines.single().net shouldBe BigDecimal("123.74")
    }

    context("USt.-Gruppen") {
        test("je Satz auf die summierte Bemessungsgrundlage, absteigend sortiert") {
            val invoice =
                testInvoice(
                    items =
                        listOf(
                            testItem(quantity = "10", unitPrice = "100.00", vatRatePercent = "19"),
                            testItem(quantity = "2", unitPrice = "50.00", vatRatePercent = "19"),
                            testItem(quantity = "1", unitPrice = "80.00", vatRatePercent = "7"),
                        ),
                )

            val totals = InvoiceCalculator.calculate(invoice, config)

            totals.vatGroups.size shouldBe 2
            val (vat19, vat7) = totals.vatGroups
            vat19.base shouldBe BigDecimal("1100.00")
            vat19.amount shouldBe BigDecimal("209.00")
            vat7.base shouldBe BigDecimal("80.00")
            vat7.amount shouldBe BigDecimal("5.60")
            totals.netTotal shouldBe BigDecimal("1180.00")
            totals.grossTotal shouldBe BigDecimal("1394.60")
        }
    }

    context("Fälligkeit") {
        test("Standard-Zahlungsziel kommt aus den Stammdaten") {
            val totals = InvoiceCalculator.calculate(testInvoice(), testSellerConfig(paymentTermDays = 14))

            totals.dueDate shouldBe LocalDate.of(2026, 8, 28)
        }

        test("Override in der Rechnung gewinnt") {
            val totals = InvoiceCalculator.calculate(testInvoice(paymentTermDaysOverride = 30), config)

            totals.dueDate shouldBe LocalDate.of(2026, 9, 13)
        }
    }
})
