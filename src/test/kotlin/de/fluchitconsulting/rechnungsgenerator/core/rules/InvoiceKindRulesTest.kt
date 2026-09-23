package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.accepted.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.accepted.Verdict
import de.fluchitconsulting.rechnungsgenerator.core.accepted.accepted
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.CancelledInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.LineItem
import de.fluchitconsulting.rechnungsgenerator.core.model.Prepayment
import de.fluchitconsulting.rechnungsgenerator.core.model.testCancelled
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testItem
import de.fluchitconsulting.rechnungsgenerator.core.model.testPrepayment
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.LocalDate

/**
 * Die Rechnungsart als Typ und die Regeln, ohne die er nicht baubar ist
 * (ADR-0008).
 */
class InvoiceKindRulesTest : FunSpec({

    val config = testSellerConfig()

    fun messageFor(invoice: Invoice, rule: Rule): String = CalculatedInvoice(invoice, config).messageOf(rule)

    fun kindOf(invoice: Invoice): InvoiceKind = CalculatedInvoice(invoice, config).accepted().kind

    // Das Gegenstück zur Ursprungsrechnung: dieselbe Position, negative Menge.
    fun storno(
        cancelled: CancelledInvoice? = testCancelled(),
        quantity: String = "-1",
    ) = testInvoice(
        type = InvoiceType.CANCELLATION,
        items = listOf(testItem(quantity = quantity, unitPrice = "1000.00")),
        cancelled = cancelled,
    )

    // Die Gesamtleistung: 20.000 € netto, 19 % USt., also 23.800 € brutto.
    fun gesamtleistung(): List<LineItem> = listOf(testItem(quantity = "1", unitPrice = "20000.00"))

    fun schlussrechnung(vararg prepayments: Prepayment) =
        testInvoice(type = InvoiceType.FINAL, items = gesamtleistung(), prepayments = prepayments.toList())

    // Je ein Weg, auf dem kein Typ entsteht – die Kennungen selbst stehen nicht
    // noch einmal hier.
    fun unbaubar(): List<Invoice> =
        listOf(
            storno(cancelled = null),
            testInvoice(cancelled = testCancelled()),
            testInvoice(receivedOn = LocalDate.of(2026, 8, 10)),
            testInvoice(items = gesamtleistung(), prepayments = listOf(testPrepayment())),
            schlussrechnung(),
            schlussrechnung(testPrepayment(receivedOn = null)),
        )

    context("aus jeder Rechnungsart wird ihr Typ") {
        test("Rechnung und Teilrechnung sind gewöhnlich und behalten ihre Art") {
            kindOf(testInvoice()) shouldBe InvoiceKind.Ordinary(InvoiceType.INVOICE)
            kindOf(testInvoice(type = InvoiceType.PARTIAL)) shouldBe InvoiceKind.Ordinary(InvoiceType.PARTIAL)
        }

        test("die Abschlagsrechnung trägt ihre Vereinnahmung, wenn sie feststeht") {
            kindOf(testInvoice(type = InvoiceType.PREPAYMENT, receivedOn = LocalDate.of(2026, 8, 10))) shouldBe
                InvoiceKind.PrepaymentInvoice(LocalDate.of(2026, 8, 10))
            kindOf(testInvoice(type = InvoiceType.PREPAYMENT)) shouldBe InvoiceKind.PrepaymentInvoice(null)
        }

        test("die Schlussrechnung trägt ihre Abschläge, jeden mit feststehender Vereinnahmung") {
            val kind = kindOf(schlussrechnung(testPrepayment())).shouldBeInstanceOf<InvoiceKind.FinalInvoice>()

            kind.deductions.single().receivedOn shouldBe LocalDate.of(2026, 6, 10)
            kind.deductions
                .single()
                .deducted.gross shouldBe Money("4760.00")
        }

        test("das Storno trägt seinen Bezug") {
            kindOf(storno()) shouldBe InvoiceKind.Cancellation(testCancelled())
        }
    }

    context("was den Typ unbaubar macht") {
        test("ein Storno ohne Bezug wird abgelehnt") {
            val message = messageFor(storno(cancelled = null), Rule.STORNO_OHNE_BEZUG)

            message shouldContain "'storniert'"
            // Die Meldung sagt, warum es die Angabe braucht, nicht nur dass sie fehlt.
            message shouldContain "BG-3"
        }

        test("ein Bezug ohne Storno wird ebenso abgelehnt, und die Meldung nennt die Art des Dokuments") {
            // Dieselbe Symmetrie wie bei 'vereinnahmt-am' und der Abschlagsrechnung.
            //
            // Geprüft wird der ganze Satzschluss und nicht bloß das Wort "Rechnung":
            // Das steckt in jedem Dokumentnamen dieses Projekts, und eine Meldung,
            // die fälschlich immer "Stornorechnung" einsetzte, käme damit durch.
            messageFor(testInvoice(cancelled = testCancelled()), Rule.BEZUG_OHNE_STORNO)
                .shouldContain("'storniert'")
                .shouldContain("dieses Dokument ist eine Rechnung.")

            // Und mit einer zweiten Art, damit belegt ist, dass der Name wirklich
            // aus der Rechnung kommt und nicht zufällig passt.
            messageFor(
                testInvoice(type = InvoiceType.FINAL, cancelled = testCancelled()),
                Rule.BEZUG_OHNE_STORNO,
            ) shouldContain "dieses Dokument ist eine Schlussrechnung."
        }

        test("vereinnahmt-am gehört nur auf eine Abschlagsrechnung") {
            val message =
                CalculatedInvoice(testInvoice(receivedOn = LocalDate.of(2026, 8, 10)), config)
                    .messageOf(Rule.VEREINNAHMUNG_FALSCHE_ART)

            message shouldContain "abschlagsrechnung"
            message shouldContain "Rechnung"
        }

        test("die Meldung nennt das Dokument, auf dem die Angabe steht") {
            val message =
                CalculatedInvoice(testInvoice(type = InvoiceType.FINAL, receivedOn = LocalDate.of(2026, 8, 10)), config)
                    .messageOf(Rule.VEREINNAHMUNG_FALSCHE_ART)

            message shouldContain "Schlussrechnung"
        }

        test("Abschläge nur in einer Schlussrechnung") {
            val message =
                messageFor(
                    testInvoice(items = gesamtleistung(), prepayments = listOf(testPrepayment())),
                    Rule.ABSCHLAEGE_FALSCHE_ART,
                )

            message shouldContain "schlussrechnung"
        }

        test("Abschläge gehören auch nicht in ein Storno") {
            // Ohne neue Regel: Die bestehende sagt "nur in einer Schlussrechnung".
            val message =
                messageFor(
                    storno().copy(prepayments = listOf(testPrepayment())),
                    Rule.ABSCHLAEGE_FALSCHE_ART,
                )

            message shouldContain "Stornorechnung"
        }

        test("eine Schlussrechnung ohne Abschläge") {
            val message = messageFor(schlussrechnung(), Rule.ABSCHLAEGE_FEHLEN)

            message shouldContain "abschlaege"
        }

        test("ein Abschlag ohne Vereinnahmung wird nicht abgesetzt") {
            // §14 Abs. 5 Satz 2 UStG setzt ab, was vereinnahmt wurde. Ein
            // berechneter, aber unbezahlter Abschlag ist eine offene Forderung.
            val message = messageFor(schlussrechnung(testPrepayment(receivedOn = null)), Rule.ABSCHLAG_OHNE_VEREINNAHMUNG)

            message shouldContain "R26-009"
            message shouldContain "vereinnahmt-am"
        }

        test("ohne gebaute Rechnungsart wird nicht angenommen") {
            // Die Voraussetzung von ADR-0008: Eine AcceptedInvoice ohne kind
            // gibt es nicht, also auch keine Stornorechnung ohne Bezug im Renderer.
            val verdict = AcceptedInvoice.check(CalculatedInvoice(storno(cancelled = null), config))

            verdict.shouldBeInstanceOf<Verdict.Rejected>().aborts.rules() shouldContain Rule.STORNO_OHNE_BEZUG
        }

        test("was den Typ unbaubar macht, ist ein Abbruch") {
            // AcceptedInvoice.check lehnt eine Rechnung ohne gebaute Art mit ihren
            // Abbrüchen ab. Wäre einer dieser Befunde bloß ein Hinweis, stünde
            // eine Ablehnung ohne Grund da (#93).
            unbaubar().forEach { invoice ->
                val notBuilt =
                    InvoiceKindRules
                        .check(CalculatedInvoice(invoice, config))
                        .shouldBeInstanceOf<InvoiceKindRules.Result.NotBuilt>()
                notBuilt.findings.forEach { finding ->
                    withClue(
                        "${finding.rule} verhindert den Bau der Rechnungsart. Ohne Art gibt es nichts zu setzen, " +
                            "also muss die Regel ein Abbruch sein und kein Hinweis.",
                    ) {
                        finding.severity shouldBe Severity.ABORT
                    }
                }
            }
        }

        test("die Ablehnung nennt die Befunde, die den Bau verhindert haben") {
            // Das Gegenstück zum Test davor: Abbruch sein genügt nicht, der Befund
            // muss auch in der Ablehnung ankommen (#105). Gebaut wird hier eigens, damit
            // der Test nicht mitliest, was AcceptedInvoice.check selbst gebaut hat.
            unbaubar().forEach { invoice ->
                val calculated = CalculatedInvoice(invoice, config)
                val notBuilt = InvoiceKindRules.check(calculated).shouldBeInstanceOf<InvoiceKindRules.Result.NotBuilt>()

                val rejected = AcceptedInvoice.check(calculated).shouldBeInstanceOf<Verdict.Rejected>()

                rejected.aborts shouldContainAll notBuilt.findings
            }
        }
    }
})
