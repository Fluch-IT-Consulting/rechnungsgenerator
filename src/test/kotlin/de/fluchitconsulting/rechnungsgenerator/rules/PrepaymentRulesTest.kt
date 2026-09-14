package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.InvoiceCalculator
import de.fluchitconsulting.rechnungsgenerator.Rule
import de.fluchitconsulting.rechnungsgenerator.messageOf
import de.fluchitconsulting.rechnungsgenerator.rules
import de.fluchitconsulting.rechnungsgenerator.testInvoice
import de.fluchitconsulting.rechnungsgenerator.testItem
import de.fluchitconsulting.rechnungsgenerator.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.values.Money
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate

/**
 * Die Regeln der Abschlagsrechnung. Die Absetzung in der Schlussrechnung ist
 * hier noch nicht dabei – die Abschlagsrechnung steht für sich.
 */
class PrepaymentRulesTest : FunSpec({

    val config = testSellerConfig()

    test("eine Abschlagsrechnung rechnet wie jede andere Rechnung") {
        val invoice =
            testInvoice(
                type = InvoiceType.PREPAYMENT,
                items = listOf(testItem(quantity = "1", unitPrice = "4000.00")),
                receivedOn = LocalDate.of(2026, 8, 10),
            )

        val totals = InvoiceCalculator.calculate(invoice, config)

        totals.netTotal shouldBe Money("4000.00")
        totals.grossTotal shouldBe Money("4760.00")
    }

    // Dass vereinnahmt-am nur auf eine Abschlagsrechnung gehört, prüft
    // InvoiceKindRulesTest.

    test("eine Vereinnahmung nach dem Rechnungsdatum belegt für diese Rechnung nichts") {
        // Das Feld ist für den Fall gedacht, dass das Geld schon vor der
        // Rechnungsstellung da war. Steht der Zeitpunkt noch nicht fest,
        // bleibt die Angabe weg – ein künftiges Datum wäre eine Behauptung.
        val invoice =
            testInvoice(
                type = InvoiceType.PREPAYMENT,
                receivedOn = LocalDate.of(2026, 8, 15),
            )

        val message = InvoiceCalculator.calculate(invoice, config).messageOf(Rule.VEREINNAHMUNG_NACH_RECHNUNGSDATUM)

        message shouldContain "2026-08-15"
    }

    test("auf einer anderen Rechnungsart schweigen diese Regeln") {
        // Dass 'vereinnahmt-am' dort nicht hingehört, meldet InvoiceKindRules.
        // Eine Meldung über das Datum spräche von einer Vereinnahmung, die es
        // auf diesem Dokument nicht geben kann (#97). Verglichen wird mit
        // derselben Rechnung ohne das Feld: Hinzukommen darf nur der eine Befund.
        val ohne = InvoiceCalculator.calculate(testInvoice(), config)
        val mit = InvoiceCalculator.calculate(testInvoice(receivedOn = LocalDate.of(2026, 8, 15)), config)

        mit.rules() shouldContainExactlyInAnyOrder ohne.rules() + Rule.VEREINNAHMUNG_FALSCHE_ART
    }
})
