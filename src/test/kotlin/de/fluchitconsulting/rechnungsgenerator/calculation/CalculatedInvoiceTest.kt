package de.fluchitconsulting.rechnungsgenerator.calculation

import de.fluchitconsulting.rechnungsgenerator.model.SkontoTier
import de.fluchitconsulting.rechnungsgenerator.model.testCancelled
import de.fluchitconsulting.rechnungsgenerator.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.model.testItem
import de.fluchitconsulting.rechnungsgenerator.model.testPrepayment
import de.fluchitconsulting.rechnungsgenerator.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.values.CountryCode
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.values.Money
import de.fluchitconsulting.rechnungsgenerator.values.Percent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class CalculatedInvoiceTest : FunSpec({

    val config = testSellerConfig()

    context("Dateiname") {
        test("setzt sich aus Rechnungsart und -nummer zusammen") {
            // Im Ordner liegen die Dokumente eines Vorgangs nebeneinander; welches
            // der Abschlag ist, soll man am Namen sehen.
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

    // Einzelpreise unterhalb der Cent-Grenze sind nach EN 16931 (BT-146) zulässig.
    // Würde der Einzelpreis beim Einlesen auf Cent normalisiert (0,335 -> 0,34),
    // stünden hier 340,00 € statt 335,00 € – 5 € Fehler auf einer einzigen Position.
    test("Einzelpreis unter einem Cent wird erst nach der Multiplikation gerundet") {
        val invoice = testInvoice(items = listOf(testItem(quantity = "1000", unitPrice = "0.335")))

        val totals = CalculatedInvoice(invoice, config)

        totals.lines.single().net shouldBe Money("335.00")
        totals.grossTotal shouldBe Money("398.65")
    }

    // Der Rabatt mindert den Positionsbetrag, nicht den Einzelpreis (EN 16931
    // BG-27): Basis (BT-137) und Rabattbetrag (BT-136) sind je auf Cent
    // gerundet, das Netto (BT-131) ist ihre Differenz.
    context("Rabatt") {
        test("mindert das Positionsnetto um den Rabattbetrag") {
            val invoice = testInvoice(items = listOf(testItem(quantity = "1", unitPrice = "800.00", discount = "30")))

            val line = CalculatedInvoice(invoice, config).lines.single()

            line.base shouldBe Money("800.00")
            line.discountAmount shouldBe Money("240.00")
            line.net shouldBe Money("560.00")
        }

        // 50 % von 10,05 sind 5,025. Gerundet wird der Rabattbetrag (5,03), das
        // Netto ist der Rest (5,02) – nicht Basis × 50 % gerundet, das wären
        // ebenfalls 5,03, und Rabatt und Netto ergäben zusammen 10,06.
        test("rundet den Rabattbetrag, das Netto ist der Rest") {
            val invoice = testInvoice(items = listOf(testItem(quantity = "1", unitPrice = "10.05", discount = "50")))

            val line = CalculatedInvoice(invoice, config).lines.single()

            line.discountAmount shouldBe Money("5.03")
            line.net shouldBe Money("5.02")
        }

        // Ein Storno rechnet mit negativer Menge (ADR-0006). Hebt es die
        // Ursprungsrechnung auf den Cent genau auf, muss auch die Rundung des
        // Rabattbetrags spiegelbildlich sein.
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

    // Die Summen gehören zur Rechnung, aus der sie entstanden – auch nach copy().
    // Ein Paar aus einer Rechnung und den Summen einer anderen soll nicht baubar sein.
    test("copy mit einer anderen Rechnung rechnet deren Summen") {
        val calculated = CalculatedInvoice(testInvoice(items = listOf(testItem(quantity = "1", unitPrice = "100.00"))), config)
        val andere = testInvoice(items = listOf(testItem(quantity = "2", unitPrice = "100.00")))

        calculated.copy(invoice = andere).netTotal shouldBe Money("200.00")
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

        // Ein negatives Zahlungsziel lehnt PaymentTermRules ab, siehe den Test dort.
        test("null Tage heißen sofort") {
            CalculatedInvoice(testInvoice(paymentTermDaysOverride = 0), config).dueDate shouldBe
                LocalDate.of(2026, 8, 14)
        }
    }

    context("Skonto") {
        test("die Skontostufen stehen nach der Skontofrist sortiert") {
            val kurz = SkontoTier(rate = Percent("2"), days = 7)
            val lang = SkontoTier(rate = Percent("1"), days = 10)

            CalculatedInvoice(testInvoice(skonto = listOf(lang, kurz)), config).skonto shouldBe listOf(kurz, lang)
        }

        test("die Skontofrist endet so viele Tage nach dem Rechnungsdatum") {
            // Rechnungsdatum 14.08.2026, wie beim Zahlungsziel oben.
            CalculatedInvoice(testInvoice(), config).skontoUntil(SkontoTier(rate = Percent("2"), days = 7)) shouldBe
                LocalDate.of(2026, 8, 21)
        }
    }

    context("Zahlungsaufforderung") {
        // Aus requestsPayment folgen Zahlungsziel und Bankverbindung (#94). Was
        // davon auf dem Blatt steht, prüft InvoiceRendererTest; hier nur die
        // Entscheidung.

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
            // Auf ein PDF kommt diese Rechnung nicht, GESAMTBETRAG_NEGATIV lehnt
            // sie ab. Dass sie kein Geld fordert, soll aber am Wert selbst hängen
            // und nicht an einer Regel in einer anderen Gruppe (#130).
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
        // Zwei Fragen, nicht eine (CONTEXT.md, *Ausland*). Regeln und Renderer
        // fragen beide hier, statt je für sich zu vergleichen (#120). Was daraus
        // folgt, prüfen VatIdCheckRulesTest und InvoiceRendererTest.

        /** Die Beispielrechnung, mit Rechnungssteller und Kunde in den genannten Ländern. */
        fun zwischen(steller: String, kunde: String): CalculatedInvoice {
            val invoice = testInvoice()
            val sellerConfig = testSellerConfig()
            return CalculatedInvoice(
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
