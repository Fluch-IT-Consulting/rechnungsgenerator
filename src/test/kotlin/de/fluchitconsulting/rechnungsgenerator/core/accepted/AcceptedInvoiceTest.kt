package de.fluchitconsulting.rechnungsgenerator.core.accepted

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testItem
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.rules.Rule
import de.fluchitconsulting.rechnungsgenerator.core.rules.rules
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** ADR-0007 */
class AcceptedInvoiceTest : FunSpec({

    val config = testSellerConfig()

    test("eine Rechnung ohne Abbruch wird angenommen und trägt ihre Hinweise") {
        val calculated = CalculatedInvoice(testInvoice(items = listOf(testItem(quantity = "0"))), config)

        val accepted = AcceptedInvoice.check(calculated).shouldBeInstanceOf<Verdict.Accepted>().invoice

        accepted.calculated shouldBe calculated
        accepted.hints.rules() shouldBe listOf(Rule.MENGE_NULL, Rule.GESAMTBETRAG_NULL)
        accepted.fileBaseName shouldBe "Rechnung-R26-001"
    }

    test("eine Nummer ohne Dateinamen wird nie ohne Grund abgelehnt") {
        // check prüft den Dateinamen selbst, den Grund nennen die Regeln zur Nummer (#150).
        listOf("", "   ", "///", "ä").forAll { number ->
            val calculated = CalculatedInvoice(testInvoice(number = number), config)

            calculated.fileBaseName shouldBe null
            AcceptedInvoice
                .check(calculated)
                .shouldBeInstanceOf<Verdict.Rejected>()
                .aborts
                .shouldNotBeEmpty()
        }
    }

    test("eine Rechnung mit Abbruch wird abgelehnt, mit allen Abbrüchen – und trägt auch ihre Hinweise") {
        val invoice =
            testInvoice(
                items = listOf(testItem(quantity = "0", unitPrice = "-1.00")),
                paymentTermDaysOverride = -5,
            )

        val rejected = AcceptedInvoice.check(CalculatedInvoice(invoice, config)).shouldBeInstanceOf<Verdict.Rejected>()

        rejected.aborts.rules() shouldBe listOf(Rule.EINZELPREIS_NEGATIV, Rule.ZAHLUNGSZIEL_NEGATIV)
        // #105
        rejected.findings.rules() shouldBe
            listOf(Rule.EINZELPREIS_NEGATIV, Rule.ZAHLUNGSZIEL_NEGATIV, Rule.MENGE_NULL, Rule.GESAMTBETRAG_NULL)
    }
})
