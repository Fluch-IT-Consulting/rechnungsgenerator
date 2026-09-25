package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testItem
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
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

    fun messageFor(invoice: Invoice, rule: Rule): String = CalculatedInvoice(invoice, config).messageOf(rule)

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
        CalculatedInvoice(testInvoice(items = listOf(testItem(), testItem(unitPrice = "0.00"))), config)
            .aborts()
            .shouldBeEmpty()
    }

    context("RABATT_AUSSERHALB_BEREICH") {
        listOf("0", "-10", "100.01", "150").forEach { discount ->
            test("ein Rabatt von $discount % wird abgelehnt") {
                val message =
                    messageFor(testInvoice(items = listOf(testItem(discount = discount))), Rule.RABATT_AUSSERHALB_BEREICH)

                message shouldContain "Position 1"
                message shouldContain "über 0% bis einschließlich 100%"
            }
        }

        listOf("0.5", "30", "100").forEach { discount ->
            test("ein Rabatt von $discount % bleibt zulässig") {
                CalculatedInvoice(testInvoice(items = listOf(testItem(discount = discount))), config)
                    .aborts()
                    .shouldBeEmpty()
            }
        }
    }

    // Der Rabatt als fester Betrag (#233). Seine Grenze ist die Basis der
    // Position: Darüber würde der Positionsbetrag negativ, wie beim Satz über
    // 100 %.
    context("RABATT_BETRAG_AUSSERHALB_BEREICH") {
        // Basis der Testposition: Menge 1 × 100,00.
        listOf("0.00", "-10.00", "100.01", "150.00").forEach { discount ->
            test("ein Rabattbetrag von $discount wird abgelehnt") {
                val invoice = testInvoice(items = listOf(testItem(discountAmount = discount)))

                val message = messageFor(invoice, Rule.RABATT_BETRAG_AUSSERHALB_BEREICH)

                message shouldContain "Position 1"
                message shouldContain "höchstens die Basis der Position"
            }
        }

        listOf("0.01", "30.00", "100.00").forEach { discount ->
            test("ein Rabattbetrag von $discount bleibt zulässig") {
                CalculatedInvoice(testInvoice(items = listOf(testItem(discountAmount = discount))), config)
                    .aborts()
                    .shouldBeEmpty()
            }
        }

        // Im Storno ist die Basis negativ, der Rabatt steht trotzdem positiv
        // in der Datei – geprüft wird deshalb gegen ihren Betrag. Die Rechnung
        // hat hier andere Abbrüche (negativer Gesamtbetrag ohne Storno); gefragt
        // wird nach der Kennung dieser Regel, nicht nach einer leeren Liste.
        test("ein Rabattbetrag bis zur Basis bleibt auch bei negativer Menge zulässig") {
            val invoice = testInvoice(items = listOf(testItem(quantity = "-1", discountAmount = "100.00")))

            CalculatedInvoice(invoice, config).rules() shouldNotContain Rule.RABATT_BETRAG_AUSSERHALB_BEREICH
        }

        // Bei Menge 0 ist die Basis 0,00 – dann ist jeder Betrag zu groß.
        test("bei Menge 0 wird jeder Rabattbetrag abgelehnt") {
            val invoice = testInvoice(items = listOf(testItem(quantity = "0", discountAmount = "0.01")))

            messageFor(invoice, Rule.RABATT_BETRAG_AUSSERHALB_BEREICH) shouldContain "0.00 = Menge × Einzelpreis"
        }
    }

    test("Satz und Betrag zugleich werden abgelehnt") {
        val invoice = testInvoice(items = listOf(testItem(discount = "30", discountAmount = "10.00")))

        val message = messageFor(invoice, Rule.RABATT_DOPPELT)

        message shouldContain "Position 1"
        message shouldContain "nicht als beides"
    }

    test("ein negativer Gesamtbetrag wird abgelehnt") {
        // Mit dem Einzelpreis ist nur noch eine negative Menge übrig, die dahin
        // führt. Ein negativer Betrag gehört seit ADR-0006 in eine
        // Stornorechnung – auf jeder anderen Art bleibt er ein Abbruch.
        val calculatedInvoice = CalculatedInvoice(testInvoice(items = listOf(testItem(quantity = "-1"))), config)
        val message = calculatedInvoice.messageOf(Rule.GESAMTBETRAG_NEGATIV)

        message shouldContain "Gesamtbetrag"
        message shouldContain "Stornorechnung"
        // Nicht RESTBETRAG_NEGATIV (#47): Seine Meldung spricht von abgesetzten
        // Abschlägen – bei einer Rechnung, die gar keine hat.
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

        CalculatedInvoice(invoice, config).grossTotal shouldBe Money("83.30")
    }
})
