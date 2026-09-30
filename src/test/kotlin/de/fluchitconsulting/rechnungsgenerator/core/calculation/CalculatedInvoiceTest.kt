package de.fluchitconsulting.rechnungsgenerator.core.calculation

import de.fluchitconsulting.rechnungsgenerator.core.model.CurrencyConversion
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.SkontoTier
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.model.testCancelled
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testItem
import de.fluchitconsulting.rechnungsgenerator.core.model.testPrepayment
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.values.CountryCode
import de.fluchitconsulting.rechnungsgenerator.core.values.Currency
import de.fluchitconsulting.rechnungsgenerator.core.values.ExchangeRate
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.YearMonth

class CalculatedInvoiceTest : FunSpec({

    val config = testSellerConfig()

    context("Jobname") {
        test("setzt sich aus Rechnungsart und -nummer zusammen") {
            // #297
            CalculatedInvoice(testInvoice(number = "R26-009", type = InvoiceType.PREPAYMENT), config)
                .fileBaseName shouldBe "Abschlagsrechnung-R26-009"
        }

        test("ersetzt Zeichen außerhalb des portablen Zeichensatzes durch Bindestriche") {
            CalculatedInvoice(testInvoice(number = "R26/009"), config).fileBaseName shouldBe "Rechnung-R26-009"
        }

        test("gibt es nicht, wenn von der Nummer nichts übrig bleibt") {
            CalculatedInvoice(testInvoice(number = "///"), config).fileBaseName shouldBe null
        }
    }

    test("Positionsnetto wird kaufmännisch auf Cent gerundet") {
        // 1.25 × 98.99 = 123.7375 -> 123.74
        val invoice = testInvoice(items = listOf(testItem(quantity = "1.25", unitPrice = "98.99")))

        val totals = CalculatedInvoice(invoice, config)

        totals.lines.single().net shouldBe Money("123.74")
    }

    // BT-146
    test("Einzelpreis unter einem Cent wird erst nach der Multiplikation gerundet") {
        val invoice = testInvoice(items = listOf(testItem(quantity = "1000", unitPrice = "0.335")))

        val totals = CalculatedInvoice(invoice, config)

        totals.lines.single().net shouldBe Money("335.00")
        totals.grossTotal shouldBe Money("398.65")
    }

    // BG-27, ADR-0013
    context("Rabatt") {
        test("mindert das Positionsnetto um den Rabattbetrag") {
            val invoice = testInvoice(items = listOf(testItem(quantity = "1", unitPrice = "800.00", discount = "30")))

            val line = CalculatedInvoice(invoice, config).lines.single()

            line.base shouldBe Money("800.00")
            line.discountAmount shouldBe Money("240.00")
            line.net shouldBe Money("560.00")
        }

        // 50 % von 10,05 sind 5,025: Rabatt 5,03, Netto 5,02. Basis × 50 % gerundet
        // wären ebenfalls 5,03, zusammen also 10,06.
        test("rundet den Rabattbetrag, das Netto ist der Rest") {
            val invoice = testInvoice(items = listOf(testItem(quantity = "1", unitPrice = "10.05", discount = "50")))

            val line = CalculatedInvoice(invoice, config).lines.single()

            line.discountAmount shouldBe Money("5.03")
            line.net shouldBe Money("5.02")
        }

        // ADR-0006
        test("rundet bei negativer Menge spiegelbildlich") {
            val invoice = testInvoice(items = listOf(testItem(quantity = "-1", unitPrice = "10.05", discount = "50")))

            val line = CalculatedInvoice(invoice, config).lines.single()

            line.discountAmount shouldBe Money("-5.03")
            line.net shouldBe Money("-5.02")
        }

        test("100 % ergeben ein Netto von null") {
            val invoice = testInvoice(items = listOf(testItem(quantity = "1", unitPrice = "800.00", discount = "100")))

            CalculatedInvoice(invoice, config).lines.single().net shouldBe Money("0.00")
        }

        // #233
        test("ein fester Rabattbetrag mindert das Positionsnetto") {
            val invoice = testInvoice(items = listOf(testItem(quantity = "2", unitPrice = "800.00", discountAmount = "200.00")))

            val line = CalculatedInvoice(invoice, config).lines.single()

            line.base shouldBe Money("1600.00")
            line.discountAmount shouldBe Money("200.00")
            line.net shouldBe Money("1400.00")
        }

        test("ein fester Rabattbetrag folgt bei negativer Menge der Basis") {
            val invoice = testInvoice(items = listOf(testItem(quantity = "-2", unitPrice = "800.00", discountAmount = "200.00")))

            val line = CalculatedInvoice(invoice, config).lines.single()

            line.base shouldBe Money("-1600.00")
            line.discountAmount shouldBe Money("-200.00")
            line.net shouldBe Money("-1400.00")
        }

        test("Rechnung und Storno heben sich mit festem Rabattbetrag auf") {
            val invoiceItem = testItem(quantity = "3", unitPrice = "33.33", discountAmount = "19.99")
            val cancellationItem = testItem(quantity = "-3", unitPrice = "33.33", discountAmount = "19.99")

            val net = CalculatedInvoice(testInvoice(items = listOf(invoiceItem)), config).lines.single().net
            val counterNet = CalculatedInvoice(testInvoice(items = listOf(cancellationItem)), config).lines.single().net

            net + counterNet shouldBe Money.ZERO
        }

        test("ohne Rabatt gibt es keinen Rabattbetrag, und das Netto ist die Basis") {
            val invoice = testInvoice(items = listOf(testItem(quantity = "1", unitPrice = "800.00")))

            val line = CalculatedInvoice(invoice, config).lines.single()

            line.discountAmount shouldBe null
            line.net shouldBe Money("800.00")
        }

        test("die Umsatzsteuer rechnet mit dem geminderten Netto") {
            val invoice =
                testInvoice(
                    items =
                        listOf(
                            testItem(quantity = "1", unitPrice = "800.00", discount = "30"),
                            testItem(quantity = "1", unitPrice = "200.00"),
                        ),
                )

            val calculated = CalculatedInvoice(invoice, config)

            calculated.netTotal shouldBe Money("760.00")
            calculated.vatGroups.single().amount shouldBe Money("144.40")
            calculated.grossTotal shouldBe Money("904.40")
        }
    }

    // Ein Paar aus einer Rechnung und den Summen einer anderen soll nicht baubar sein.
    test("copy mit einer anderen Rechnung rechnet deren Summen") {
        val calculated = CalculatedInvoice(testInvoice(items = listOf(testItem(quantity = "1", unitPrice = "100.00"))), config)
        val other = testInvoice(items = listOf(testItem(quantity = "2", unitPrice = "100.00")))

        calculated.copy(invoice = other).netTotal shouldBe Money("200.00")
    }

    context("USt.-Gruppen") {
        test("je Satz auf die summierte Bemessungsgrundlage") {
            val invoice =
                testInvoice(
                    items =
                        listOf(
                            testItem(quantity = "10", unitPrice = "100.00", vatRate = "19"),
                            testItem(quantity = "2", unitPrice = "50.00", vatRate = "19"),
                            testItem(quantity = "1", unitPrice = "80.00", vatRate = "7"),
                        ),
                )

            val totals = CalculatedInvoice(invoice, config)

            totals.vatGroups.size shouldBe 2
            val (vat19, vat7) = totals.vatGroups
            vat19.base shouldBe Money("1100.00")
            vat19.amount shouldBe Money("209.00")
            vat7.base shouldBe Money("80.00")
            vat7.amount shouldBe Money("5.60")
            totals.netTotal shouldBe Money("1180.00")
            totals.grossTotal shouldBe Money("1394.60")
        }

        // Die Positionen stehen verkehrt herum, sonst wäre der Test auch ohne Sortierung grün.
        context("Reihenfolge") {
            test("innerhalb einer Kategorie der höhere Satz zuerst") {
                val invoice = testInvoice(items = listOf(testItem(vatRate = "7"), testItem(vatRate = "19")))

                CalculatedInvoice(invoice, config).vatGroups.map { it.rate } shouldBe listOf(Percent("19"), Percent("7"))
            }

            test("die Kategorien in der Reihenfolge von TaxCategory, nicht in der der Datei") {
                val invoice =
                    testInvoice(
                        items =
                            listOf(
                                testItem(vatRate = "0", taxCategory = TaxCategory.OUT_OF_SCOPE),
                                testItem(vatRate = "0", taxCategory = TaxCategory.EXEMPT),
                            ),
                    )

                CalculatedInvoice(invoice, config).vatGroups.map { it.category } shouldBe
                    listOf(TaxCategory.EXEMPT, TaxCategory.OUT_OF_SCOPE)
            }
        }

        // Ein Schlüssel aus nur Kategorie oder nur Satz ordnete hier falsch zu.
        test("jede Position findet ihre Gruppe") {
            val invoice =
                testInvoice(
                    items =
                        listOf(
                            testItem(vatRate = "7"),
                            testItem(vatRate = "0", taxCategory = TaxCategory.EXEMPT, taxExemptionReason = "§4 Nr. 21 UStG"),
                            testItem(vatRate = "19"),
                            testItem(vatRate = "0", taxCategory = TaxCategory.ZERO_RATED),
                        ),
                )
            val calculated = CalculatedInvoice(invoice, config)

            calculated.lines.map { calculated.vatGroupOf(it).category to calculated.vatGroupOf(it).rate } shouldBe
                calculated.lines.map { it.item.taxCategory to it.item.vatRate }
        }
    }

    context("Umsatzsteuer in Euro (#418)") {
        test("gibt es nur mit einer Umrechnung") {
            CalculatedInvoice(testInvoice(currency = Currency.USD), config).vatTotalInEuro shouldBe null
        }

        test("ist die Summe der Umsatzsteuer, geteilt durch den Kurs") {
            // 1 × 8337.50 × 19 % = 1584.13 $; 1584.13 / 1.1685 = 1355.6953… -> 1355.70 €
            val invoice =
                testInvoice(
                    items = listOf(testItem(quantity = "1", unitPrice = "8337.50")),
                    currency = Currency.USD,
                    conversion = CurrencyConversion(ExchangeRate("1.1685"), YearMonth.of(2026, 7)),
                )

            val calculated = CalculatedInvoice(invoice, config)

            calculated.vatTotal shouldBe Money("1584.13")
            calculated.vatTotalInEuro shouldBe Money("1355.70")
        }

        test("rechnet die Summe um und nicht jede Gruppe") {
            val invoice =
                testInvoice(
                    items =
                        listOf(
                            testItem(quantity = "1", unitPrice = "5.00", vatRate = "19"),
                            testItem(quantity = "1", unitPrice = "5.00", vatRate = "7"),
                        ),
                    currency = Currency.USD,
                    conversion = CurrencyConversion(ExchangeRate("0.6"), YearMonth.of(2026, 7)),
                )

            val calculated = CalculatedInvoice(invoice, config)

            // Summe: 0.95 + 0.35 = 1.30 $, 1.30 / 0.6 = 2.1666… -> 2.17 €.
            // Je Gruppe wären es 1.5833… -> 1.58 und 0.5833… -> 0.58, zusammen 2.16 €.
            calculated.vatTotalInEuro shouldBe Money("2.17")
        }
    }

    context("Fälligkeit") {
        test("Standard-Zahlungsziel kommt aus den Stammdaten") {
            val totals = CalculatedInvoice(testInvoice(), testSellerConfig(paymentTermDays = 14))

            totals.dueDate shouldBe LocalDate.of(2026, 8, 28)
        }

        test("Override in der Rechnung gewinnt") {
            val totals = CalculatedInvoice(testInvoice(paymentTermDaysOverride = 30), config)

            totals.dueDate shouldBe LocalDate.of(2026, 9, 13)
        }

        test("null Tage heißen sofort") {
            CalculatedInvoice(testInvoice(paymentTermDaysOverride = 0), config).dueDate shouldBe
                LocalDate.of(2026, 8, 14)
        }
    }

    context("Skonto") {
        test("die Skontostufen stehen nach der Skontofrist sortiert") {
            val short = SkontoTier(rate = Percent("2"), days = 7)
            val long = SkontoTier(rate = Percent("1"), days = 10)

            CalculatedInvoice(testInvoice(skonto = listOf(long, short)), config).skonto shouldBe listOf(short, long)
        }

        test("die Skontofrist endet so viele Tage nach dem Rechnungsdatum") {
            // Rechnungsdatum 14.08.2026, wie beim Zahlungsziel oben.
            CalculatedInvoice(testInvoice(), config).skontoUntil(SkontoTier(rate = Percent("2"), days = 7)) shouldBe
                LocalDate.of(2026, 8, 21)
        }
    }

    context("Zahlungsaufforderung") {
        // #94

        test("eine gewöhnliche Rechnung fordert zur Zahlung auf") {
            CalculatedInvoice(testInvoice(), config).requestsPayment shouldBe true
        }

        test("eine Schlussrechnung ohne Restbetrag rechnet ab, fordert aber kein Geld") {
            // 4.000 € netto, 19 % USt.: 4.760 € brutto, genau der Abschlag.
            val calculated =
                CalculatedInvoice(
                    testInvoice(
                        type = InvoiceType.FINAL,
                        items = listOf(testItem(quantity = "1", unitPrice = "4000.00")),
                        prepayments = listOf(testPrepayment()),
                    ),
                    config,
                )

            calculated.remainingAmount shouldBe Money("0.00")
            calculated.settledByPrepayments shouldBe true
            calculated.requestsPayment shouldBe false
            calculated.dueDate shouldBe null
        }

        test("eine Schlussrechnung mit Restbetrag ist nicht ausgeglichen") {
            val calculated =
                CalculatedInvoice(
                    testInvoice(
                        type = InvoiceType.FINAL,
                        items = listOf(testItem(quantity = "2", unitPrice = "4000.00")),
                        prepayments = listOf(testPrepayment()),
                    ),
                    config,
                )

            calculated.settledByPrepayments shouldBe false
            calculated.requestsPayment shouldBe true
        }

        test("ohne Abschläge ist nichts ausgeglichen, auch bei Gesamtbetrag null") {
            val calculated =
                CalculatedInvoice(
                    testInvoice(items = listOf(testItem(quantity = "0", unitPrice = "1000.00"))),
                    config,
                )

            calculated.remainingAmount shouldBe Money("0.00")
            calculated.settledByPrepayments shouldBe false
        }

        // #503: Vereinnahmt ist der ganze Abschlag, nicht ein Teil.
        test("eine Abschlagsrechnung mit vereinnahmt-am ist ganz vereinnahmt und fordert nichts") {
            val calculated =
                CalculatedInvoice(testInvoice(type = InvoiceType.PREPAYMENT, receivedOn = LocalDate.of(2026, 8, 10)), config)

            calculated.paidAmount shouldBe calculated.grossTotal
            calculated.amountDue.isZero() shouldBe true
            calculated.requestsPayment shouldBe false
            calculated.dueDate shouldBe null
        }

        test("eine Abschlagsrechnung ohne vereinnahmt-am fordert ihren ganzen Betrag") {
            val calculated = CalculatedInvoice(testInvoice(type = InvoiceType.PREPAYMENT), config)

            calculated.paidAmount.isZero() shouldBe true
            calculated.amountDue shouldBe calculated.grossTotal
            calculated.requestsPayment shouldBe true
        }

        test("eine Stornorechnung fordert kein Geld") {
            val calculated =
                CalculatedInvoice(
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
            // GESAMTBETRAG_NEGATIV lehnt sie ab, aber es soll am Wert selbst hängen (#130).
            val calculated =
                CalculatedInvoice(
                    testInvoice(items = listOf(testItem(quantity = "-1", unitPrice = "1000.00"))),
                    config,
                )

            calculated.requestsPayment shouldBe false
            calculated.dueDate shouldBe null
        }
    }

    context("Inland und EU") {
        // Zwei Fragen, nicht eine (CONTEXT.md, *Ausland*, #120).

        fun between(seller: String, customer: String): CalculatedInvoice {
            val invoice = testInvoice()
            val sellerConfig = testSellerConfig()
            return CalculatedInvoice(
                invoice.copy(
                    customer = invoice.customer.copy(address = invoice.customer.address.copy(countryCode = CountryCode(customer))),
                ),
                sellerConfig.copy(address = sellerConfig.address.copy(countryCode = CountryCode(seller))),
            )
        }

        test("Kunde im Land des Rechnungsstellers: Inland") {
            val calculated = between(seller = "DE", customer = "DE")

            calculated.isDomestic shouldBe true
            calculated.customerInEu shouldBe true
        }

        test("Kunde in einem anderen Mitgliedstaat: Ausland, aber EU") {
            val calculated = between(seller = "DE", customer = "AT")

            calculated.isDomestic shouldBe false
            calculated.customerInEu shouldBe true
        }

        test("Inland heißt das Land des Rechnungsstellers, nicht Deutschland") {
            val calculated = between(seller = "AT", customer = "AT")

            calculated.isDomestic shouldBe true
            calculated.customerInEu shouldBe true
        }

        test("Kunde in einem Drittland: Ausland und nicht EU") {
            val calculated = between(seller = "DE", customer = "CH")

            calculated.isDomestic shouldBe false
            calculated.customerInEu shouldBe false
        }
    }
})
