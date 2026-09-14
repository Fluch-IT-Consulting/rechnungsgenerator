package de.fluchitconsulting.rechnungsgenerator.calculation

import de.fluchitconsulting.rechnungsgenerator.model.testCancelled
import de.fluchitconsulting.rechnungsgenerator.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.model.testItem
import de.fluchitconsulting.rechnungsgenerator.model.testPrepayment
import de.fluchitconsulting.rechnungsgenerator.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.values.CountryCode
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.values.Money
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
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

        // Ein negatives Zahlungsziel lehnt PaymentTermRules ab, siehe den Test dort.
        test("null Tage heißen sofort") {
            InvoiceCalculator.calculate(testInvoice(paymentTermDaysOverride = 0), config).dueDate shouldBe
                LocalDate.of(2026, 8, 14)
        }
    }

    context("Zahlungsaufforderung") {
        // Aus requestsPayment folgen Zahlungsziel und Bankverbindung (#94). Was
        // davon auf dem Blatt steht, prüft InvoiceRendererTest; hier nur die
        // Entscheidung.

        test("eine gewöhnliche Rechnung fordert zur Zahlung auf") {
            InvoiceCalculator.calculate(testInvoice(), config).requestsPayment shouldBe true
        }

        test("eine Schlussrechnung ohne Restbetrag rechnet ab, fordert aber kein Geld") {
            // 4.000 € netto, 19 % USt.: 4.760 € brutto, genau der Abschlag.
            val calculated =
                InvoiceCalculator.calculate(
                    testInvoice(
                        type = InvoiceType.FINAL,
                        items = listOf(testItem(quantity = "1", unitPrice = "4000.00")),
                        prepayments = listOf(testPrepayment()),
                    ),
                    config,
                )

            calculated.remainingAmount shouldBe Money("0.00")
            calculated.requestsPayment shouldBe false
            calculated.dueDate shouldBe null
        }

        test("eine Stornorechnung fordert kein Geld") {
            val calculated =
                InvoiceCalculator.calculate(
                    testInvoice(
                        type = InvoiceType.CANCELLATION,
                        items = listOf(testItem(quantity = "-1", unitPrice = "1000.00")),
                        cancelled = testCancelled(),
                    ),
                    config,
                )

            calculated.requestsPayment shouldBe false
            calculated.dueDate shouldBe null
        }

        test("ein negativer Betrag fordert kein Geld, auch wo die Art eine Leistung abrechnet") {
            // Auf ein PDF kommt diese Rechnung nicht, GESAMTBETRAG_NEGATIV lehnt
            // sie ab. Dass sie kein Geld fordert, soll aber am Wert selbst hängen
            // und nicht an einer Regel in einer anderen Gruppe (#130).
            val calculated =
                InvoiceCalculator.calculate(
                    testInvoice(items = listOf(testItem(quantity = "-1", unitPrice = "1000.00"))),
                    config,
                )

            calculated.requestsPayment shouldBe false
            calculated.dueDate shouldBe null
        }
    }

    context("Inland und EU") {
        // Zwei Fragen, nicht eine (CONTEXT.md, *Ausland*). Regeln und Renderer
        // fragen beide hier, statt je für sich zu vergleichen (#120). Was daraus
        // folgt, prüfen VatIdCheckRulesTest und InvoiceRendererTest.

        /** Die Beispielrechnung, mit Rechnungssteller und Kunde in den genannten Ländern. */
        fun zwischen(steller: String, kunde: String): CalculatedInvoice {
            val invoice = testInvoice()
            val sellerConfig = testSellerConfig()
            return InvoiceCalculator.calculate(
                invoice.copy(customer = invoice.customer.copy(address = invoice.customer.address.copy(countryCode = CountryCode(kunde)))),
                sellerConfig.copy(address = sellerConfig.address.copy(countryCode = CountryCode(steller))),
            )
        }

        test("Kunde im Land des Rechnungsstellers: Inland") {
            val calculated = zwischen(steller = "DE", kunde = "DE")

            calculated.isDomestic shouldBe true
            calculated.customerInEu shouldBe true
        }

        test("Kunde in einem anderen Mitgliedstaat: Ausland, aber EU") {
            val calculated = zwischen(steller = "DE", kunde = "AT")

            calculated.isDomestic shouldBe false
            calculated.customerInEu shouldBe true
        }

        test("Inland heißt das Land des Rechnungsstellers, nicht Deutschland") {
            // Die Verwechslung, die im Renderer schon einmal stand: Für einen
            // Rechnungssteller in Österreich ist ein Kunde in Wien Inland.
            val calculated = zwischen(steller = "AT", kunde = "AT")

            calculated.isDomestic shouldBe true
            calculated.customerInEu shouldBe true
        }

        test("Kunde in einem Drittland: Ausland und nicht EU") {
            val calculated = zwischen(steller = "DE", kunde = "CH")

            calculated.isDomestic shouldBe false
            calculated.customerInEu shouldBe false
        }
    }
})
