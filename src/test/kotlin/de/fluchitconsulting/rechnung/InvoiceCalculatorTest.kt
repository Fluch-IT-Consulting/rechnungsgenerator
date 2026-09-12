package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.values.Money
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate

class InvoiceCalculatorTest : FunSpec({

    val config = testSellerConfig()

    test("Positionsnetto wird kaufmännisch auf Cent gerundet") {
        // 1.25 × 98.99 = 123.7375 -> 123.74
        val invoice = testInvoice(items = listOf(testItem(quantity = "1.25", unitPrice = "98.99")))

        val totals = InvoiceCalculator.calculate(invoice, config)

        totals.lines.single().net shouldBe Money("123.74")
    }

    // Einzelpreise unterhalb der Cent-Grenze sind nach EN 16931 (BT-146) zulässig.
    // Würde der Einzelpreis beim Einlesen auf Cent normalisiert (0,335 -> 0,34),
    // stünden hier 340,00 € statt 335,00 € – 5 € Fehler auf einer einzigen Position.
    test("Einzelpreis unter einem Cent wird erst nach der Multiplikation gerundet") {
        val invoice = testInvoice(items = listOf(testItem(quantity = "1000", unitPrice = "0.335")))

        val totals = InvoiceCalculator.calculate(invoice, config)

        totals.lines.single().net shouldBe Money("335.00")
        totals.grossTotal shouldBe Money("398.65")
    }

    context("USt.-Gruppen") {
        test("je Satz auf die summierte Bemessungsgrundlage, absteigend sortiert") {
            val invoice =
                testInvoice(
                    items =
                        listOf(
                            testItem(quantity = "10", unitPrice = "100.00", vatRate = "19"),
                            testItem(quantity = "2", unitPrice = "50.00", vatRate = "19"),
                            testItem(quantity = "1", unitPrice = "80.00", vatRate = "7"),
                        ),
                )

            val totals = InvoiceCalculator.calculate(invoice, config)

            totals.vatGroups.size shouldBe 2
            val (vat19, vat7) = totals.vatGroups
            vat19.base shouldBe Money("1100.00")
            vat19.amount shouldBe Money("209.00")
            vat7.base shouldBe Money("80.00")
            vat7.amount shouldBe Money("5.60")
            totals.netTotal shouldBe Money("1180.00")
            totals.grossTotal shouldBe Money("1394.60")
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

        // Ein negatives Zahlungsziel setzte die Fälligkeit vor das
        // Rechnungsdatum: "zahlbar bis 9. August" auf einer Rechnung vom
        // 14. August (#47). Die Meldung nennt, woher der Wert kommt.
        test("ein negatives Zahlungsziel in der Rechnung wird abgelehnt") {
            val message =
                InvoiceCalculator
                    .calculate(testInvoice(paymentTermDaysOverride = -5), config)
                    .messageOf(Rule.ZAHLUNGSZIEL_NEGATIV)

            message shouldContain "-5"
            message shouldContain "Rechnung"
        }

        test("ebenso eines aus den Stammdaten") {
            val message =
                InvoiceCalculator
                    .calculate(testInvoice(), testSellerConfig(paymentTermDays = -5))
                    .messageOf(Rule.ZAHLUNGSZIEL_NEGATIV)

            message shouldContain "-5"
            message shouldContain "Stammdaten"
        }

        test("null Tage heißen sofort") {
            InvoiceCalculator.calculate(testInvoice(paymentTermDaysOverride = 0), config).dueDate shouldBe
                LocalDate.of(2026, 8, 14)
        }
    }
})
