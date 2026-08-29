package de.fluchitconsulting.rechnung

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class InvoiceCalculatorTest {
    private val config = testSellerConfig()

    @Test
    fun `line net is rounded half-up to cents`() {
        // 1.25 × 98.99 = 123.7375 → kaufmännisch 123.74
        val invoice = testInvoice(items = listOf(testItem(quantity = "1.25", unitPrice = "98.99")))

        val totals = InvoiceCalculator.calculate(invoice, config)

        assertEquals(BigDecimal("123.74"), totals.lines.single().net)
    }

    @Test
    fun `vat is calculated per rate group on the summed base`() {
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

        // Gruppen absteigend nach Satz sortiert: erst 19 %, dann 7 %.
        assertEquals(2, totals.vatGroups.size)
        val (vat19, vat7) = totals.vatGroups
        assertEquals(BigDecimal("1100.00"), vat19.base)
        assertEquals(BigDecimal("209.00"), vat19.amount)
        assertEquals(BigDecimal("80.00"), vat7.base)
        assertEquals(BigDecimal("5.60"), vat7.amount)
        assertEquals(BigDecimal("1180.00"), totals.netTotal)
        assertEquals(BigDecimal("1394.60"), totals.grossTotal)
    }

    @Test
    fun `due date uses default payment term from config`() {
        val totals = InvoiceCalculator.calculate(testInvoice(), testSellerConfig(paymentTermDays = 14))

        assertEquals(LocalDate.of(2026, 8, 28), totals.dueDate)
    }

    @Test
    fun `due date override in invoice wins over config`() {
        val totals = InvoiceCalculator.calculate(testInvoice(paymentTermDaysOverride = 30), config)

        assertEquals(LocalDate.of(2026, 9, 13), totals.dueDate)
    }
}
