package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.model.ServicePeriod
import de.fluchitconsulting.rechnung.values.InvoiceType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate

/**
 * Die Zeiträume einer Rechnung (#47): Ein Zeitraum endet nicht vor seinem
 * Beginn, und eine Rechnung über eine erbrachte Leistung entsteht nicht, bevor
 * die Leistung zu Ende ist.
 *
 * testInvoice trägt den Leistungszeitraum 01.–31.07.2026 und das Rechnungsdatum
 * 14.08.2026.
 */
class ServicePeriodRulesTest : FunSpec({

    val config = testSellerConfig()

    fun errorMessageOf(invoice: Invoice): String =
        shouldThrow<InvoiceValidationException> { InvoiceCalculator.calculate(invoice, config) }.message!!

    context("Rechnungsdatum und Ende der Leistung") {
        test("eine Rechnung vor dem Ende ihres Leistungszeitraums wird abgelehnt") {
            val message = errorMessageOf(testInvoice().copy(issueDate = LocalDate.of(2026, 7, 15)))

            message shouldContain "endet am 2026-07-31"
            message shouldContain "erbrachte Leistung"
        }

        test("am letzten Tag des Zeitraums darf sie entstehen") {
            InvoiceCalculator.calculate(testInvoice().copy(issueDate = LocalDate.of(2026, 7, 31)), config)
        }

        test("auch der Zeitraum einer Position zählt") {
            val item = testItem().copy(servicePeriod = ServicePeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))

            errorMessageOf(testInvoice(items = listOf(item))) shouldContain "Position 1"
        }

        test("für Teil- und Schlussrechnung gilt dasselbe") {
            val vorzeitig = LocalDate.of(2026, 7, 15)

            errorMessageOf(testInvoice(type = InvoiceType.PARTIAL).copy(issueDate = vorzeitig)) shouldContain
                "Teilrechnung"
            errorMessageOf(
                testInvoice(type = InvoiceType.FINAL, prepayments = listOf(testPrepayment())).copy(issueDate = vorzeitig),
            ) shouldContain "Schlussrechnung"
        }

        test("eine Abschlagsrechnung darf vor dem Ende ihres Zeitraums entstehen") {
            // Ihr Zeitraum ist eine Prognose: Die Leistung steht noch aus, und
            // genau darüber stellt sie die Rechnung.
            val item = testItem().copy(servicePeriod = ServicePeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
            val abschlagsrechnung =
                testInvoice(type = InvoiceType.PREPAYMENT, items = listOf(item)).copy(issueDate = LocalDate.of(2026, 6, 15))

            InvoiceCalculator.calculate(abschlagsrechnung, config)
        }
    }

    context("Beginn und Ende") {
        test("ein Leistungszeitraum, der vor seinem Beginn endet, wird abgelehnt (BR-29)") {
            val invoice = testInvoice().copy(servicePeriod = ServicePeriod(LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 1)))

            errorMessageOf(invoice) shouldContain "beginnt am 2026-07-31"
        }

        test("ebenso der Zeitraum einer Position (BR-30)") {
            val item = testItem().copy(servicePeriod = ServicePeriod(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 10)))

            val message = errorMessageOf(testInvoice(items = listOf(item)))

            message shouldContain "Position 1"
            message shouldContain "beginnt am 2026-07-20"
        }

        test("auch bei einer Abschlagsrechnung – die Prognose darf in der Zukunft liegen, verdreht sein darf sie nicht") {
            val abschlagsrechnung =
                testInvoice(type = InvoiceType.PREPAYMENT)
                    .copy(servicePeriod = ServicePeriod(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 6, 1)))

            errorMessageOf(abschlagsrechnung) shouldContain "beginnt am 2026-10-31"
        }
    }
})
