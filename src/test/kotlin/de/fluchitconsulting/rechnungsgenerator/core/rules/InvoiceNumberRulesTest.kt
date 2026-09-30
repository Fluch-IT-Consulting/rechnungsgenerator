package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testItem
import de.fluchitconsulting.rechnungsgenerator.core.model.testPrepayment
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.values.InvoiceNumberPattern
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** #54, ADR-0005. Ohne Muster in den Stammdaten wird die Form nicht geprüft. */
class InvoiceNumberRulesTest : FunSpec({

    val withoutPattern = testSellerConfig()
    val withPattern = testSellerConfig(invoiceNumberPattern = InvoiceNumberPattern("R\\d{2}-\\d{3}"))

    context("ohne Muster in den Stammdaten") {
        test("geht jede Nummer durch") {
            CalculatedInvoice(testInvoice(number = "2026/Beratung/7"), withoutPattern).aborts().shouldBeEmpty()
        }

        // Aus einer Datei kommt sie nicht an: Ein leerer Wert bricht beim Lesen ab (#504, #514).
        test("eine leere Nummer aus Code bricht trotzdem ab, denn von ihr bleibt kein Dateiname") {
            CalculatedInvoice(testInvoice(number = ""), withoutPattern)
                .rules()
                .shouldContainOnlyRule(Rule.RECHNUNGSNUMMER_OHNE_DATEINAMEN)
        }

        test("Leerraum am Rand wird abgelehnt und die gemeinte Fassung genannt") {
            // Nur ein gequoteter YAML-Wert trägt ihn.
            val message =
                CalculatedInvoice(testInvoice(number = " R26-001"), withoutPattern)
                    .messageOf(Rule.RECHNUNGSNUMMER_MIT_LEERRAUM)

            message shouldContain "Leerraum"
            message shouldContain "'R26-001'"
        }

        test("eine Nummer, von der kein Dateiname übrig bleibt, wird abgelehnt und nennt die Zeichen") {
            // ADR-0004, #150
            val message =
                CalculatedInvoice(testInvoice(number = "///"), withoutPattern)
                    .messageOf(Rule.RECHNUNGSNUMMER_OHNE_DATEINAMEN)

            message shouldContain "'///'"
            message shouldContain "Bindestrich"
        }
    }

    context("mit Muster in den Stammdaten") {
        test("eine passende Nummer geht durch") {
            CalculatedInvoice(testInvoice(number = "R26-001"), withPattern).aborts().shouldBeEmpty()
        }

        test("eine unpassende Nummer wird abgelehnt, und die Meldung zeigt beides") {
            val message =
                CalculatedInvoice(testInvoice(number = "2026-1"), withPattern)
                    .messageOf(Rule.RECHNUNGSNUMMER_PASST_NICHT)

            message shouldContain "'2026-1'"
            message shouldContain "R\\d{2}-\\d{3}"
            message shouldContain "rechnungsnummer-muster"
        }

        test("das Muster gilt für die ganze Nummer, auch ohne Anker") {
            CalculatedInvoice(testInvoice(number = "XR26-001Y"), withPattern)
                .rules()
                .shouldContainOnlyRule(Rule.RECHNUNGSNUMMER_PASST_NICHT)
        }

        test("eine Nummer ohne Dateinamen meldet nicht zusätzlich, dass sie nicht passt") {
            CalculatedInvoice(testInvoice(number = "///"), withPattern)
                .rules()
                .shouldContainOnlyRule(Rule.RECHNUNGSNUMMER_OHNE_DATEINAMEN)
        }

        test("die Nummern der abgesetzten Abschläge bleiben ungeprüft") {
            // Sie stammen womöglich aus der Zeit vor einem Wechsel des Musters.
            val finalInvoice =
                testInvoice(
                    number = "R26-001",
                    type = InvoiceType.FINAL,
                    items = listOf(testItem(quantity = "1", unitPrice = "20000.00")),
                    prepayments = listOf(testPrepayment(invoiceNumber = "2024/Beratung/7")),
                )

            CalculatedInvoice(finalInvoice, withPattern).aborts().shouldBeEmpty()
        }
    }
})

// Regeln anderer Gruppen dürfen sich melden.
private fun List<Rule>.shouldContainOnlyRule(rule: Rule) {
    filter { it.name.startsWith("RECHNUNGSNUMMER_") } shouldBe listOf(rule)
}
