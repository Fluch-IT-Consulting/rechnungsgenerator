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
            CalculatedInvoice(testInvoice(number = "2026/Beratung/7"), ohneMuster).aborts().shouldBeEmpty()
        }

        test("eine leere Nummer wird trotzdem abgelehnt") {
            // Unabhängig vom Muster: Ohne Nummer gibt es keinen Jobnamen und
            // nichts, worauf eine Schlussrechnung oder ein Storno verweisen kann.
            val message = CalculatedInvoice(testInvoice(number = ""), ohneMuster).messageOf(Rule.RECHNUNGSNUMMER_FEHLT)

            message shouldContain "fehlt"
        }

        test("eine Nummer aus lauter Leerzeichen fehlt, sie trägt nicht bloß Leerraum") {
            CalculatedInvoice(testInvoice(number = "   "), ohneMuster)
                .rules()
                .shouldContainOnlyRule(Rule.RECHNUNGSNUMMER_FEHLT)
        }

        test("Leerraum am Rand wird abgelehnt und die gemeinte Fassung genannt") {
            // Nur ein gequoteter YAML-Wert kann ihn überhaupt tragen;
            // ungequotet schneidet YAML ihn selbst ab.
            val message =
                CalculatedInvoice(testInvoice(number = " R26-001"), ohneMuster)
                    .messageOf(Rule.RECHNUNGSNUMMER_MIT_LEERRAUM)

            message shouldContain "Leerraum"
            message shouldContain "'R26-001'"
        }

        test("eine Nummer, von der kein Dateiname übrig bleibt, wird abgelehnt und nennt die Zeichen") {
            // Ohne diese Regel fiele sie erst in `erzeuge` auf, nachdem die
            // Rechnung angenommen ist – mit weiteren Abbrüchen in der Datei
            // also erst im zweiten Lauf (ADR-0004, #150).
            val message =
                CalculatedInvoice(testInvoice(number = "///"), ohneMuster)
                    .messageOf(Rule.RECHNUNGSNUMMER_OHNE_DATEINAMEN)

            message shouldContain "'///'"
            message shouldContain "Bindestrich"
        }
    }

    context("mit Muster in den Stammdaten") {
        test("eine passende Nummer geht durch") {
            CalculatedInvoice(testInvoice(number = "R26-001"), mitMuster).aborts().shouldBeEmpty()
        }

        test("eine unpassende Nummer wird abgelehnt, und die Meldung zeigt beides") {
            val message =
                CalculatedInvoice(testInvoice(number = "2026-1"), mitMuster)
                    .messageOf(Rule.RECHNUNGSNUMMER_PASST_NICHT)

            message shouldContain "'2026-1'"
            message shouldContain "R\\d{2}-\\d{3}"
            // Der Schlüssel, an dem es zu ändern wäre.
            message shouldContain "rechnungsnummer-muster"
        }

        test("das Muster gilt für die ganze Nummer, auch ohne Anker") {
            // Regex.matches, nicht containsMatchIn: Sonst ließe ein Muster ohne
            // ^ und $ jede Nummer durch, die irgendwo eine passende Stelle hat.
            CalculatedInvoice(testInvoice(number = "XR26-001Y"), mitMuster)
                .rules()
                .shouldContainOnlyRule(Rule.RECHNUNGSNUMMER_PASST_NICHT)
        }

        test("eine fehlende Nummer meldet nicht zusätzlich, dass sie nicht passt") {
            // Sie passt zu keinem Muster – das zu melden hieße, denselben
            // Fehler zweimal zu nennen.
            CalculatedInvoice(testInvoice(number = ""), mitMuster)
                .rules()
                .shouldContainOnlyRule(Rule.RECHNUNGSNUMMER_FEHLT)
        }

        test("eine Nummer ohne Dateinamen meldet nicht zusätzlich, dass sie nicht passt") {
            // Wie leer und Leerraum gilt das unabhängig vom Muster und wird
            // vorher geprüft: Es nennt die Ursache.
            CalculatedInvoice(testInvoice(number = "///"), mitMuster)
                .rules()
                .shouldContainOnlyRule(Rule.RECHNUNGSNUMMER_OHNE_DATEINAMEN)
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

            CalculatedInvoice(schlussrechnung, mitMuster).aborts().shouldBeEmpty()
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
