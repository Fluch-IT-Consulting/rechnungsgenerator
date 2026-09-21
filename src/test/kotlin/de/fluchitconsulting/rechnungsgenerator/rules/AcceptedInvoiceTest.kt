package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.model.testItem
import de.fluchitconsulting.rechnungsgenerator.model.testSellerConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Die Prüfung, hinter der gesetzt wird (ADR-0007). Dass Renderer und das
 * Zusammentragen der Abschläge nur eine [AcceptedInvoice] annehmen, prüft der
 * Compiler; hier steht, wann es eine gibt.
 */
class AcceptedInvoiceTest : FunSpec({

    val config = testSellerConfig()

    test("eine Rechnung ohne Abbruch wird angenommen und trägt ihre Hinweise") {
        // Menge 0 ist zulässig, aber ein Hinweis (MENGE_NULL) – und weil es die
        // einzige Position ist, auch der Gesamtbetrag (GESAMTBETRAG_NULL).
        val calculated = CalculatedInvoice(testInvoice(items = listOf(testItem(quantity = "0"))), config)

        val accepted = AcceptedInvoice.check(calculated).shouldBeInstanceOf<Verdict.Accepted>().invoice

        accepted.calculated shouldBe calculated
        accepted.hints.rules() shouldBe listOf(Rule.MENGE_NULL, Rule.GESAMTBETRAG_NULL)
        accepted.fileBaseName shouldBe "Rechnung-R26-001"
    }

    test("eine Nummer ohne Dateinamen wird nie ohne Grund abgelehnt") {
        // check verlangt den Dateinamen selbst, die Gründe nennen aber die
        // Regeln zur Rechnungsnummer (#150). Liefen beide auseinander, stünde
        // eine Ablehnung ohne einen einzigen Abbruch da.
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
        // Die Regeltests lesen ihre Befunde auch von abgelehnten Rechnungen (#105).
        rejected.findings.rules() shouldBe
            listOf(Rule.EINZELPREIS_NEGATIV, Rule.ZAHLUNGSZIEL_NEGATIV, Rule.MENGE_NULL, Rule.GESAMTBETRAG_NULL)
    }
})
