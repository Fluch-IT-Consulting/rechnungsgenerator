package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.model.LineItem
import de.fluchitconsulting.rechnung.model.Prepayment
import de.fluchitconsulting.rechnung.model.TaxShare
import de.fluchitconsulting.rechnung.values.InvoiceType
import de.fluchitconsulting.rechnung.values.Money
import de.fluchitconsulting.rechnung.values.Percent
import de.fluchitconsulting.rechnung.values.TaxCategory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate

/**
 * Die Absetzung in der Schlussrechnung: die neun Abbruchregeln aus Issue #1
 * und das, was am Ende dabei herauskommen soll.
 *
 * Jede Regel prüft die Meldung mit, nicht nur den Abbruch. Wer eine
 * Schlussrechnung schreibt, sitzt vor Zahlen aus mehreren Monaten – eine
 * Meldung, die nur "ungültig" sagt, lässt ihn suchen.
 */
class FinalInvoiceRulesTest : FunSpec({

    val config = testSellerConfig()

    // Die Gesamtleistung: 20.000 € netto, 19 % USt., also 23.800 € brutto.
    fun gesamtleistung(): List<LineItem> = listOf(testItem(quantity = "1", unitPrice = "20000.00"))

    fun schlussrechnung(vararg prepayments: Prepayment) =
        testInvoice(type = InvoiceType.FINAL, items = gesamtleistung(), prepayments = prepayments.toList())

    fun fehlerVon(invoice: Invoice): String =
        shouldThrow<InvoiceValidationException> { InvoiceCalculator.calculate(invoice, config) }.message!!

    context("die neun Abbruchregeln") {
        test("1. Abschläge nur in einer Schlussrechnung") {
            val fehler = fehlerVon(testInvoice(items = gesamtleistung(), prepayments = listOf(testPrepayment())))

            fehler shouldContain "schlussrechnung"
        }

        test("2. eine Schlussrechnung ohne Abschläge") {
            val fehler = fehlerVon(schlussrechnung())

            fehler shouldContain "abschlaege"
        }

        test("3. ein Abschlag ohne Vereinnahmung wird nicht abgesetzt") {
            // §14 Abs. 5 Satz 2 UStG setzt ab, was vereinnahmt wurde. Ein
            // berechneter, aber unbezahlter Abschlag ist eine offene Forderung.
            val fehler = fehlerVon(schlussrechnung(testPrepayment(receivedOn = null)))

            fehler shouldContain "R26-009"
            fehler shouldContain "vereinnahmt-am"
        }

        test("4. vereinnahmt nach dem Rechnungsdatum") {
            val fehler = fehlerVon(schlussrechnung(testPrepayment(receivedOn = LocalDate.of(2026, 9, 1))))

            fehler shouldContain "2026-09-01"
            fehler shouldContain "2026-08-14"
        }

        test("5. die Steuer des Abschlags stimmt nicht mit Netto und Satz überein") {
            val fehler = fehlerVon(schlussrechnung(testPrepayment(vat = "670.00")))

            fehler shouldContain "670"
            fehler shouldContain "760"
        }

        test("6. eine Kombination, die in der Schlussrechnung nicht vorkommt") {
            // Die Gesamtleistung ist mit 19 % abgerechnet; ein Abschlag mit 7 %
            // gehört entweder zu einer anderen Rechnung oder war damals falsch.
            val fehler = fehlerVon(schlussrechnung(testPrepayment(vatRate = "7", vat = "280.00")))

            fehler shouldContain "kommt in dieser Schlussrechnung nicht vor"
            fehler shouldContain "R26-009"
        }

        test("7. dieselbe Rechnungsnummer zweimal") {
            val fehler = fehlerVon(schlussrechnung(testPrepayment(), testPrepayment()))

            fehler shouldContain "R26-009"
            fehler shouldContain "zweimal"
        }

        test("8. ein negativer Restbetrag") {
            // Mehr abgesetzt als berechnet: Das ist keine Rechnung mehr,
            // sondern ein Fall für eine Gutschrift.
            val zuViel = testPrepayment(net = "30000.00", vat = "5700.00")

            val fehler = fehlerVon(schlussrechnung(zuViel))

            fehler shouldContain "Restbetrag"
        }

        test("9. ein Abschlag ohne Steueranteile") {
            // Aus demselben Grund wie Regel 5: Sonst setzte der Abschlag still
            // 0,00 € ab, ohne dass die Prüfsumme je etwas zu prüfen hätte.
            val fehler = fehlerVon(schlussrechnung(testPrepayment(taxShares = emptyList())))

            fehler shouldContain "steueranteile"
            fehler shouldContain "R26-009"
        }
    }

    context("was am Ende dasteht") {
        test("zwei Abschläge, Netto und Steuer getrennt abgesetzt") {
            val invoice =
                schlussrechnung(
                    testPrepayment(invoiceNumber = "R26-009"),
                    testPrepayment(invoiceNumber = "R26-011", issueDate = LocalDate.of(2026, 7, 20)),
                )

            val totals = InvoiceCalculator.calculate(invoice, config)

            totals.grossTotal shouldBe Money("23800.00")
            // Netto und Steuer bleiben getrennt (ADR-0002) – deshalb hier
            // einzeln geprüft und nicht nur der Bruttobetrag.
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

            val totals = InvoiceCalculator.calculate(invoice, config)

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

            val totals = InvoiceCalculator.calculate(invoice, config)

            totals.remainingAmount shouldBe Money.ZERO
            totals.prepaymentTotal shouldBe totals.grossTotal
        }

        test("ohne Abschläge bleibt der Restbetrag der Gesamtbetrag") {
            val totals = InvoiceCalculator.calculate(testInvoice(items = gesamtleistung()), config)

            totals.prepayments.shouldBeEmpty()
            totals.remainingAmount shouldBe totals.grossTotal
        }
    }
})
