package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.values.Money
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * Positionen und Beträge (#47): Eine Rechnung hat mindestens eine Position,
 * kein Einzelpreis ist negativ und der Gesamtbetrag auch nicht.
 */
class ItemRulesTest : FunSpec({

    val config = testSellerConfig()

    fun messageFor(invoice: Invoice, rule: Rule): String = InvoiceCalculator.calculate(invoice, config).messageOf(rule)

    test("eine Rechnung ohne Position wird abgelehnt (BR-16)") {
        messageFor(testInvoice(items = emptyList()), Rule.KEINE_POSITION) shouldContain "mindestens eine Position"
    }

    test("ein negativer Einzelpreis wird abgelehnt (BR-27)") {
        val message = messageFor(testInvoice(items = listOf(testItem(unitPrice = "-95.00"))), Rule.EINZELPREIS_NEGATIV)

        message shouldContain "Position 1"
        message shouldContain "-95.00"
    }

    test("ein Einzelpreis von null bleibt zulässig") {
        // Eine Leistung, die ohne Berechnung auf der Rechnung stehen soll.
        InvoiceCalculator
            .calculate(testInvoice(items = listOf(testItem(), testItem(unitPrice = "0.00"))), config)
            .aborts()
            .shouldBeEmpty()
    }

    test("ein negativer Gesamtbetrag wird abgelehnt") {
        // Mit dem Einzelpreis ist nur noch eine negative Menge übrig, die dahin
        // führt. Ein negativer Betrag gehört seit ADR-0006 in eine
        // Stornorechnung – auf jeder anderen Art bleibt er ein Abbruch.
        val calculatedInvoice = InvoiceCalculator.calculate(testInvoice(items = listOf(testItem(quantity = "-1"))), config)
        val message = calculatedInvoice.messageOf(Rule.GESAMTBETRAG_NEGATIV)

        message shouldContain "Gesamtbetrag"
        message shouldContain "Stornorechnung"
        // Bis #47 schlug hier Regel 8 der Absetzung zu. Ihre Meldung spricht von
        // abgesetzten Abschlägen – bei einer Rechnung, die gar keine hat.
        message shouldNotContain "Abschläge"
        // Und sie meldet sich auch nicht daneben: Seit alle Befunde erscheinen
        // (#49), fiele eine zweite, unsinnige Zeile sofort auf.
        calculatedInvoice.rules() shouldNotContain Rule.RESTBETRAG_NEGATIV
    }

    test("eine negative Korrekturzeile bleibt zulässig, solange der Gesamtbetrag es ist") {
        // Negative Mengen waren in #47 offengelassen, mit dem Vermerk, die Frage
        // hänge an Gutschrift und Storno. ADR-0006 hat sie beantwortet: Die
        // negative Menge ist der von EN 16931 vorgesehene Weg zu einem
        // negativen Betrag, weil BR-27 den negativen Einzelpreis verbietet.
        // Einzeln bleibt sie damit zulässig, solange die Summe stimmt.
        val invoice =
            testInvoice(items = listOf(testItem(unitPrice = "100.00"), testItem(quantity = "-1", unitPrice = "30.00")))

        InvoiceCalculator.calculate(invoice, config).grossTotal shouldBe Money("83.30")
    }
})
