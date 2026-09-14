package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.InvoiceCalculator
import de.fluchitconsulting.rechnungsgenerator.Rule
import de.fluchitconsulting.rechnungsgenerator.aborts
import de.fluchitconsulting.rechnungsgenerator.messageOf
import de.fluchitconsulting.rechnungsgenerator.rules
import de.fluchitconsulting.rechnungsgenerator.testInvoice
import de.fluchitconsulting.rechnungsgenerator.testItem
import de.fluchitconsulting.rechnungsgenerator.testPrepayment
import de.fluchitconsulting.rechnungsgenerator.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceNumberPattern
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Die Form der Rechnungsnummer (#54, ADR-0005).
 *
 * Das Muster kommt aus den Stammdaten, nicht aus dem Code – deshalb steht in
 * jedem Test, der eines braucht, auch eines da. Die Tests ohne Muster sind
 * nicht die Ausnahme, sondern der Regelfall: Ohne Eintrag wird die Form nicht
 * geprüft.
 */
class InvoiceNumberRulesTest : FunSpec({

    val ohneMuster = testSellerConfig()
    val mitMuster = testSellerConfig(invoiceNumberPattern = InvoiceNumberPattern("R\\d{2}-\\d{3}"))

    context("ohne Muster in den Stammdaten") {
        test("geht jede Nummer durch") {
            // Wer den Generator mit Nummern aus seiner Buchhaltung füttert, hat
            // ein anderes Format als R26-009.
            InvoiceCalculator.calculate(testInvoice(number = "2026/Beratung/7"), ohneMuster).aborts().shouldBeEmpty()
        }

        test("eine leere Nummer wird trotzdem abgelehnt") {
            // Unabhängig vom Muster: Aus der Nummer entsteht der Dateiname.
            val message = InvoiceCalculator.calculate(testInvoice(number = ""), ohneMuster).messageOf(Rule.RECHNUNGSNUMMER_FEHLT)

            message shouldContain "fehlt"
            message shouldContain "Dateiname"
        }

        test("eine Nummer aus lauter Leerzeichen fehlt, sie trägt nicht bloß Leerraum") {
            InvoiceCalculator
                .calculate(testInvoice(number = "   "), ohneMuster)
                .rules()
                .shouldContainOnlyRule(Rule.RECHNUNGSNUMMER_FEHLT)
        }

        test("Leerraum am Rand wird abgelehnt und die gemeinte Fassung genannt") {
            // Nur ein gequoteter YAML-Wert kann ihn überhaupt tragen;
            // ungequotet schneidet YAML ihn selbst ab.
            val message =
                InvoiceCalculator
                    .calculate(testInvoice(number = " R26-001"), ohneMuster)
                    .messageOf(Rule.RECHNUNGSNUMMER_MIT_LEERRAUM)

            message shouldContain "Leerraum"
            message shouldContain "'R26-001'"
        }
    }

    context("mit Muster in den Stammdaten") {
        test("eine passende Nummer geht durch") {
            InvoiceCalculator.calculate(testInvoice(number = "R26-001"), mitMuster).aborts().shouldBeEmpty()
        }

        test("eine unpassende Nummer wird abgelehnt, und die Meldung zeigt beides") {
            val message =
                InvoiceCalculator
                    .calculate(testInvoice(number = "2026-1"), mitMuster)
                    .messageOf(Rule.RECHNUNGSNUMMER_PASST_NICHT)

            message shouldContain "'2026-1'"
            message shouldContain "R\\d{2}-\\d{3}"
            // Der Schlüssel, an dem es zu ändern wäre.
            message shouldContain "rechnungsnummer-muster"
        }

        test("das Muster gilt für die ganze Nummer, auch ohne Anker") {
            // Regex.matches, nicht containsMatchIn: Sonst ließe ein Muster ohne
            // ^ und $ jede Nummer durch, die irgendwo eine passende Stelle hat.
            InvoiceCalculator
                .calculate(testInvoice(number = "XR26-001Y"), mitMuster)
                .rules()
                .shouldContainOnlyRule(Rule.RECHNUNGSNUMMER_PASST_NICHT)
        }

        test("eine fehlende Nummer meldet nicht zusätzlich, dass sie nicht passt") {
            // Sie passt zu keinem Muster – das zu melden hieße, denselben
            // Fehler zweimal zu nennen.
            InvoiceCalculator
                .calculate(testInvoice(number = ""), mitMuster)
                .rules()
                .shouldContainOnlyRule(Rule.RECHNUNGSNUMMER_FEHLT)
        }

        test("die Nummern der abgesetzten Abschläge bleiben ungeprüft") {
            // Sie stammen aus früheren Rechnungen, womöglich aus der Zeit vor
            // einem Wechsel des Musters. Gegen das heutige geprüft, hielte eine
            // korrekte alte Nummer die Schlussrechnung auf.
            val schlussrechnung =
                testInvoice(
                    number = "R26-001",
                    type = InvoiceType.FINAL,
                    items = listOf(testItem(quantity = "1", unitPrice = "20000.00")),
                    prepayments = listOf(testPrepayment(invoiceNumber = "2024/Beratung/7")),
                )

            InvoiceCalculator.calculate(schlussrechnung, mitMuster).aborts().shouldBeEmpty()
        }
    }
})

/**
 * Genau diese Regel zur Rechnungsnummer und keine zweite. Die übrigen Regeln
 * dürfen sich melden – geprüft wird, dass die Nummer nicht doppelt gerügt wird.
 */
private fun List<Rule>.shouldContainOnlyRule(rule: Rule) {
    filter { it.name.startsWith("RECHNUNGSNUMMER_") } shouldBe listOf(rule)
}
