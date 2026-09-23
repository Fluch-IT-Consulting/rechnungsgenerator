package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.LineItem
import de.fluchitconsulting.rechnungsgenerator.core.model.Prepayment
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxShare
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testItem
import de.fluchitconsulting.rechnungsgenerator.core.model.testPrepayment
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.values.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent
import de.fluchitconsulting.rechnungsgenerator.core.values.TaxCategory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate

/**
 * Die Absetzung in der Schlussrechnung: die Abbruchregeln aus Issue #1 und #47
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

    fun messageFor(invoice: Invoice, rule: Rule): String = CalculatedInvoice(invoice, config).messageOf(rule)

    context("die Abbruchregeln") {
        // Ob Abschläge dort stehen, wo sie hingehören, ob eine Schlussrechnung
        // welche hat und ob jeder vereinnahmt ist, prüft InvoiceKindRulesTest.

        test("vereinnahmt nach dem Rechnungsdatum") {
            val message =
                messageFor(
                    schlussrechnung(testPrepayment(receivedOn = LocalDate.of(2026, 9, 1))),
                    Rule.ABSCHLAG_NACH_RECHNUNGSDATUM,
                )

            message shouldContain "2026-09-01"
            message shouldContain "2026-08-14"
        }

        test("die Steuer des Abschlags stimmt nicht mit Netto und Satz überein") {
            val message = messageFor(schlussrechnung(testPrepayment(vat = "670.00")), Rule.ABSCHLAG_PRUEFSUMME)

            message shouldContain "670"
            message shouldContain "760"
        }

        test("eine Kombination, die in der Schlussrechnung nicht vorkommt") {
            // Die Gesamtleistung ist mit 19 % abgerechnet; ein Abschlag mit 7 %
            // gehört entweder zu einer anderen Rechnung oder war damals falsch.
            val message =
                messageFor(schlussrechnung(testPrepayment(vatRate = "7", vat = "280.00")), Rule.ABSCHLAG_KOMBINATION_FEHLT)

            message shouldContain "kommt in dieser Schlussrechnung nicht vor"
            message shouldContain "R26-009"
        }

        test("dieselbe Rechnungsnummer zweimal") {
            val message = messageFor(schlussrechnung(testPrepayment(), testPrepayment()), Rule.ABSCHLAG_DOPPELT)

            message shouldContain "R26-009"
            message shouldContain "2-mal"
        }

        test("zwei verschiedene Nummern je doppelt geben zwei Befunde") {
            // ADR-0004 will alle Gründe auf einmal. Mit firstOrNull stünde nur
            // die erste doppelte Nummer da; die zweite fiele erst im nächsten
            // Lauf auf (#152).
            val invoice =
                schlussrechnung(
                    testPrepayment(invoiceNumber = "R26-009"),
                    testPrepayment(invoiceNumber = "R26-009"),
                    testPrepayment(invoiceNumber = "R26-011"),
                    testPrepayment(invoiceNumber = "R26-011"),
                )

            val doppelte =
                CalculatedInvoice(invoice, config).aborts().filter { it.rule == Rule.ABSCHLAG_DOPPELT }

            doppelte.size shouldBe 2
            doppelte.joinToString { it.describe() } shouldContain "R26-009"
            doppelte.joinToString { it.describe() } shouldContain "R26-011"
        }

        test("dreimal dieselbe Nummer nennt die Anzahl, nicht zweimal") {
            // Wer "zweimal" liest und eine Zeile löscht, läuft in denselben Abbruch.
            val message =
                messageFor(schlussrechnung(testPrepayment(), testPrepayment(), testPrepayment()), Rule.ABSCHLAG_DOPPELT)

            message shouldContain "3-mal"
        }

        test("zweimal dieselbe Nummer heißt: exakt gleich geschrieben") {
            // Der Generator nimmt nichts über das Schema einer Nummer an (#53):
            // "r26-009" und "R26-009 " sind andere Nummern als "R26-009".
            val invoice =
                schlussrechnung(
                    testPrepayment(invoiceNumber = "R26-009"),
                    testPrepayment(invoiceNumber = "r26-009"),
                    testPrepayment(invoiceNumber = "R26-009 "),
                )

            CalculatedInvoice(invoice, config).prepayments.map { it.prepayment.invoiceNumber } shouldBe
                listOf("R26-009", "r26-009", "R26-009 ")
        }

        test("ein negativer Restbetrag") {
            // Mehr abgesetzt als berechnet: Das ist keine Rechnung mehr,
            // sondern ein Fall für eine Rückzahlung.
            val tooLarge = testPrepayment(net = "30000.00", vat = "5700.00")

            val message = messageFor(schlussrechnung(tooLarge), Rule.RESTBETRAG_NEGATIV)

            message shouldContain "Restbetrag"
        }

        test("ein Abschlag ohne Steueranteile") {
            // Aus demselben Grund wie ABSCHLAG_PRUEFSUMME: Sonst setzte der
            // Abschlag still 0,00 € ab, ohne dass die Prüfsumme je etwas zu
            // prüfen hätte.
            val message =
                messageFor(schlussrechnung(testPrepayment(taxShares = emptyList())), Rule.ABSCHLAG_OHNE_STEUERANTEILE)

            message shouldContain "steueranteile"
            message shouldContain "R26-009"
        }

        test("ein Abschlag, der nach der Schlussrechnung datiert ist") {
            // Das Gegenstück zu ABSCHLAG_NACH_RECHNUNGSDATUM, diesmal für das
            // Rechnungsdatum (#47): Ein Abschlag geht der Schlussrechnung
            // voraus. Später datiert ist er ein Tippfehler – oder eine Rechnung,
            // die nicht hierher gehört.
            val message =
                messageFor(
                    schlussrechnung(testPrepayment(issueDate = LocalDate.of(2026, 9, 1))),
                    Rule.ABSCHLAG_SPAETER_DATIERT,
                )

            message shouldContain "R26-009"
            message shouldContain "2026-09-01"
            message shouldContain "2026-08-14"
        }

        test("auf einer anderen Rechnungsart schweigen die Regeln der Absetzung") {
            // Dass Abschläge dort nicht hingehören, meldet InvoiceKindRules. Die
            // Regeln hier sprächen von "dieser Schlussrechnung", auf einem Blatt,
            // das keine ist (#97). Die Abschläge sind absichtlich falsch: falsche
            // Prüfsumme, später datiert und vereinnahmt, fremde Kombination.
            // Verglichen wird mit derselben Rechnung ohne sie, damit auch eine
            // künftige Regel dieser Gruppe erfasst ist.
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
            val ohne = CalculatedInvoice(testInvoice(items = gesamtleistung()), config)
            val mit = CalculatedInvoice(testInvoice(items = gesamtleistung(), prepayments = abschlaege), config)

            mit.rules() shouldContainExactlyInAnyOrder ohne.rules() + Rule.ABSCHLAEGE_FALSCHE_ART
        }
    }

    context("was am Ende dasteht") {
        test("zwei Abschläge, Netto und Steuer getrennt abgesetzt") {
            val invoice =
                schlussrechnung(
                    testPrepayment(invoiceNumber = "R26-009"),
                    testPrepayment(invoiceNumber = "R26-011", issueDate = LocalDate.of(2026, 7, 20)),
                )

            val totals = CalculatedInvoice(invoice, config)

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
            // Und kein Zahlungsziel: Es gibt nichts mehr zu zahlen. Die
            // Entscheidung fällt hier, nicht im Renderer.
            totals.dueDate shouldBe null
        }

        test("ohne Abschläge bleibt der Restbetrag der Gesamtbetrag") {
            val totals = CalculatedInvoice(testInvoice(items = gesamtleistung()), config)

            totals.prepayments.shouldBeEmpty()
            totals.remainingAmount shouldBe totals.grossTotal
            // Offener Betrag, also ein Zahlungsziel.
            totals.dueDate shouldNotBe null
        }
    }
})
