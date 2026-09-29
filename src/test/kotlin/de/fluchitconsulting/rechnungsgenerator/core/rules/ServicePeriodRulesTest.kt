package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.ServicePeriod
import de.fluchitconsulting.rechnungsgenerator.core.model.testCancelled
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testItem
import de.fluchitconsulting.rechnungsgenerator.core.model.testPrepayment
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate

/** #47. testInvoice trägt den Leistungszeitraum 01.–31.07.2026 und das Rechnungsdatum 14.08.2026. */
class ServicePeriodRulesTest : FunSpec({

    val config = testSellerConfig()

    fun messageFor(invoice: Invoice, rule: Rule): String = CalculatedInvoice(invoice, config).messageOf(rule)

    context("Rechnungsdatum und Ende der Leistung") {
        test("eine Rechnung vor dem Ende ihres Leistungszeitraums wird abgelehnt") {
            val message =
                messageFor(testInvoice().copy(issueDate = LocalDate.of(2026, 7, 15)), Rule.ZEITRAUM_NACH_RECHNUNGSDATUM)

            message shouldContain "endet am 2026-07-31"
            message shouldContain "erbrachte Leistung"
        }

        test("am letzten Tag des Zeitraums darf sie entstehen") {
            CalculatedInvoice(testInvoice().copy(issueDate = LocalDate.of(2026, 7, 31)), config)
                .aborts()
                .shouldBeEmpty()
        }

        test("auch der Zeitraum einer Position zählt") {
            val item = testItem().copy(servicePeriod = ServicePeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))

            messageFor(testInvoice(items = listOf(item)), Rule.ZEITRAUM_NACH_RECHNUNGSDATUM) shouldContain "Position 1"
        }

        test("für Teil- und Schlussrechnung gilt dasselbe") {
            val early = LocalDate.of(2026, 7, 15)

            messageFor(
                testInvoice(type = InvoiceType.PARTIAL).copy(issueDate = early),
                Rule.ZEITRAUM_NACH_RECHNUNGSDATUM,
            ) shouldContain "Teilrechnung"
            messageFor(
                testInvoice(type = InvoiceType.FINAL, prepayments = listOf(testPrepayment())).copy(issueDate = early),
                Rule.ZEITRAUM_NACH_RECHNUNGSDATUM,
            ) shouldContain "Schlussrechnung"
        }

        test("eine Abschlagsrechnung darf vor dem Ende ihres Zeitraums entstehen") {
            val item = testItem().copy(servicePeriod = ServicePeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
            val prepaymentInvoice =
                testInvoice(type = InvoiceType.PREPAYMENT, items = listOf(item)).copy(issueDate = LocalDate.of(2026, 6, 15))

            CalculatedInvoice(prepaymentInvoice, config).aborts().shouldBeEmpty()
        }

        test("das Storno einer Abschlagsrechnung darf den Zeitraum der Prognose tragen") {
            // Der Zeitraum stammt aus der Ursprungsrechnung, und die war die Prognose (#151, #66).
            val cancellation =
                testInvoice(
                    type = InvoiceType.CANCELLATION,
                    items = listOf(testItem(quantity = "-1")),
                    cancelled = testCancelled(invoiceNumber = "R26-009", issueDate = LocalDate.of(2026, 6, 15)),
                ).copy(
                    issueDate = LocalDate.of(2026, 6, 20),
                    servicePeriod = ServicePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 10, 31)),
                )

            CalculatedInvoice(cancellation, config).aborts().shouldBeEmpty()
        }
    }

    context("Beginn und Ende") {
        test("ein Leistungszeitraum, der vor seinem Beginn endet, wird abgelehnt (BR-29)") {
            val invoice = testInvoice().copy(servicePeriod = ServicePeriod(LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 1)))

            messageFor(invoice, Rule.ZEITRAUM_VERDREHT) shouldContain "beginnt am 2026-07-31"
        }

        test("ebenso der Zeitraum einer Position (BR-30)") {
            val item = testItem().copy(servicePeriod = ServicePeriod(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 10)))

            val message = messageFor(testInvoice(items = listOf(item)), Rule.ZEITRAUM_VERDREHT)

            message shouldContain "Position 1"
            message shouldContain "beginnt am 2026-07-20"
        }

        test("auch bei einer Abschlagsrechnung – die Prognose darf in der Zukunft liegen, verdreht sein darf sie nicht") {
            val prepaymentInvoice =
                testInvoice(type = InvoiceType.PREPAYMENT)
                    .copy(servicePeriod = ServicePeriod(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 6, 1)))

            messageFor(prepaymentInvoice, Rule.ZEITRAUM_VERDREHT) shouldContain "beginnt am 2026-10-31"
        }
    }
})
