package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.LineItem
import de.fluchitconsulting.rechnungsgenerator.core.model.Prepayment
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxShare
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testItem
import de.fluchitconsulting.rechnungsgenerator.core.model.testPrepayment
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate

/** #1, #47. Jede Regel prüft die Meldung mit, nicht nur den Abbruch. */
class FinalInvoiceRulesTest : FunSpec({

    val config = testSellerConfig()

    // Die Gesamtleistung: 20.000 € netto, 19 % USt., also 23.800 € brutto.
    fun totalService(): List<LineItem> = listOf(testItem(quantity = "1", unitPrice = "20000.00"))

    fun finalInvoice(vararg prepayments: Prepayment) =
        testInvoice(type = InvoiceType.FINAL, items = totalService(), prepayments = prepayments.toList())

    fun messageFor(invoice: Invoice, rule: Rule): String = CalculatedInvoice(invoice, config).messageOf(rule)

    context("die Abbruchregeln") {

        test("vereinnahmt nach dem Rechnungsdatum") {
            val message =
                messageFor(
                    finalInvoice(testPrepayment(receivedOn = LocalDate.of(2026, 9, 1))),
                    Rule.ABSCHLAG_NACH_RECHNUNGSDATUM,
                )

            message shouldContain "2026-09-01"
            message shouldContain "2026-08-14"
        }

        test("die Steuer des Abschlags stimmt nicht mit Netto und Satz überein") {
            val message = messageFor(finalInvoice(testPrepayment(vat = "670.00")), Rule.ABSCHLAG_PRUEFSUMME)

            message shouldContain "670"
            message shouldContain "760"
        }

        test("eine Kombination, die in der Schlussrechnung nicht vorkommt") {
            val message =
                messageFor(finalInvoice(testPrepayment(vatRate = "7", vat = "280.00")), Rule.ABSCHLAG_KOMBINATION_FEHLT)

            message shouldContain "kommt in dieser Schlussrechnung nicht vor"
            message shouldContain "R26-009"
        }

        test("dieselbe Rechnungsnummer zweimal") {
            val message = messageFor(finalInvoice(testPrepayment(), testPrepayment()), Rule.ABSCHLAG_DOPPELT)

            message shouldContain "R26-009"
            message shouldContain "2-mal"
        }

        test("zwei verschiedene Nummern je doppelt geben zwei Befunde") {
            // ADR-0004, #152
            val invoice =
                finalInvoice(
                    testPrepayment(invoiceNumber = "R26-009"),
                    testPrepayment(invoiceNumber = "R26-009"),
                    testPrepayment(invoiceNumber = "R26-011"),
                    testPrepayment(invoiceNumber = "R26-011"),
                )

            val duplicates =
                CalculatedInvoice(invoice, config).aborts().filter { it.rule == Rule.ABSCHLAG_DOPPELT }

            duplicates.size shouldBe 2
            duplicates.joinToString { it.describe() } shouldContain "R26-009"
            duplicates.joinToString { it.describe() } shouldContain "R26-011"
        }

        test("dreimal dieselbe Nummer nennt die Anzahl, nicht zweimal") {
            val message =
                messageFor(finalInvoice(testPrepayment(), testPrepayment(), testPrepayment()), Rule.ABSCHLAG_DOPPELT)

            message shouldContain "3-mal"
        }

        test("zweimal dieselbe Nummer heißt: exakt gleich geschrieben") {
            // Der Generator nimmt nichts über das Schema einer Nummer an (#53).
            val invoice =
                finalInvoice(
                    testPrepayment(invoiceNumber = "R26-009"),
                    testPrepayment(invoiceNumber = "r26-009"),
                    testPrepayment(invoiceNumber = "R26-009 "),
                )

            CalculatedInvoice(invoice, config).prepayments.map { it.prepayment.invoiceNumber } shouldBe
                listOf("R26-009", "r26-009", "R26-009 ")
        }

        test("ein negativer Restbetrag") {
            val tooLarge = testPrepayment(net = "30000.00", vat = "5700.00")

            val message = messageFor(finalInvoice(tooLarge), Rule.RESTBETRAG_NEGATIV)

            message shouldContain "Restbetrag"
        }

        test("ein Abschlag ohne Steueranteile") {
            // Wie ABSCHLAG_PRUEFSUMME: Sonst setzte der Abschlag still 0,00 € ab.
            val message =
                messageFor(finalInvoice(testPrepayment(taxShares = emptyList())), Rule.ABSCHLAG_OHNE_STEUERANTEILE)

            message shouldContain "steueranteile"
            message shouldContain "R26-009"
        }

        test("ein Abschlag, der nach der Schlussrechnung datiert ist") {
            // Das Gegenstück zu ABSCHLAG_NACH_RECHNUNGSDATUM (#47).
            val message =
                messageFor(
                    finalInvoice(testPrepayment(issueDate = LocalDate.of(2026, 9, 1))),
                    Rule.ABSCHLAG_SPAETER_DATIERT,
                )

            message shouldContain "R26-009"
            message shouldContain "2026-09-01"
            message shouldContain "2026-08-14"
        }

        test("auf einer anderen Rechnungsart schweigen die Regeln der Absetzung") {
            // #97. Die Abschläge sind absichtlich falsch; der Vergleich mit derselben
            // Rechnung ohne sie erfasst auch eine künftige Regel dieser Gruppe.
            val abschlaege =
                listOf(
                    testPrepayment(vat = "670.00"),
                    testPrepayment(
                        invoiceNumber = "R26-011",
                        issueDate = LocalDate.of(2026, 9, 1),
                        receivedOn = LocalDate.of(2026, 9, 2),
                    ),
                    testPrepayment(invoiceNumber = "R26-012", vatRate = "7", vat = "280.00"),
                )
            val absent = CalculatedInvoice(testInvoice(items = totalService()), config)
            val present = CalculatedInvoice(testInvoice(items = totalService(), prepayments = abschlaege), config)

            present.rules() shouldContainExactlyInAnyOrder absent.rules() + Rule.ABSCHLAEGE_FALSCHE_ART
        }
    }

    context("was am Ende dasteht") {
        test("zwei Abschläge, Netto und Steuer getrennt abgesetzt") {
            val invoice =
                finalInvoice(
                    testPrepayment(invoiceNumber = "R26-009"),
                    testPrepayment(invoiceNumber = "R26-011", issueDate = LocalDate.of(2026, 7, 20)),
                )

            val totals = CalculatedInvoice(invoice, config)

            totals.grossTotal shouldBe Money("23800.00")
            // Netto und Steuer getrennt (ADR-0002).
            totals.prepayments.map { it.net } shouldBe listOf(Money("4000.00"), Money("4000.00"))
            totals.prepayments.map { it.vat } shouldBe listOf(Money("760.00"), Money("760.00"))
            totals.prepayments.map { it.gross } shouldBe listOf(Money("4760.00"), Money("4760.00"))
            totals.prepaymentTotal shouldBe Money("9520.00")
            totals.remainingAmount shouldBe Money("14280.00")
        }

        test("ein gemischter Abschlag wird je Steuerart abgesetzt") {
            val invoice =
                testInvoice(
                    type = InvoiceType.FINAL,
                    items =
                        listOf(
                            testItem(quantity = "1", unitPrice = "20000.00"),
                            testItem(quantity = "1", unitPrice = "1000.00", vatRate = "7"),
                        ),
                    prepayments =
                        listOf(
                            testPrepayment(
                                taxShares =
                                    listOf(
                                        TaxShare(TaxCategory.STANDARD, Percent("19"), Money("4000.00"), Money("760.00")),
                                        TaxShare(TaxCategory.STANDARD, Percent("7"), Money("1000.00"), Money("70.00")),
                                    ),
                            ),
                        ),
                )

            val totals = CalculatedInvoice(invoice, config)

            totals.prepayments.single().net shouldBe Money("5000.00")
            totals.prepayments.single().vat shouldBe Money("830.00")
            totals.prepayments.single().gross shouldBe Money("5830.00")
        }

        test("ein Restbetrag von null ist zulässig") {
            val invoice =
                testInvoice(
                    type = InvoiceType.FINAL,
                    items = listOf(testItem(quantity = "1", unitPrice = "4000.00")),
                    prepayments = listOf(testPrepayment()),
                )

            val totals = CalculatedInvoice(invoice, config)

            totals.remainingAmount shouldBe Money.ZERO
            totals.prepaymentTotal shouldBe totals.grossTotal
            // Die Entscheidung fällt hier, nicht im Renderer.
            totals.dueDate shouldBe null
        }

        test("ohne Abschläge bleibt der Restbetrag der Gesamtbetrag") {
            val totals = CalculatedInvoice(testInvoice(items = totalService()), config)

            totals.prepayments.shouldBeEmpty()
            totals.remainingAmount shouldBe totals.grossTotal
            totals.dueDate shouldNotBe null
        }
    }
})
