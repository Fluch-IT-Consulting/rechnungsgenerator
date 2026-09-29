package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.SkontoTier
import de.fluchitconsulting.rechnungsgenerator.core.model.testCancelled
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testItem
import de.fluchitconsulting.rechnungsgenerator.core.model.testPrepayment
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** #183 */
class SkontoRulesTest : FunSpec({

    // Zahlungsziel 14 Tage, wie in den Stammdaten der Beispielrechnung.
    val config = testSellerConfig(paymentTermDays = 14)

    fun stufe(
        rate: String,
        days: Int,
    ) = SkontoTier(rate = Percent(rate), days = days)

    fun calculated(invoice: Invoice) = CalculatedInvoice(invoice, config)

    fun skontoRules(invoice: Invoice): List<Rule> =
        calculated(invoice).rules().filter { it in SKONTO_RULES }

    test("eine Rechnung ohne Skonto hat keinen Befund dazu") {
        calculated(testInvoice()).aborts().shouldBeEmpty()
    }

    test("zwei Stufen in beliebiger Reihenfolge werden angenommen") {
        calculated(testInvoice(skonto = listOf(stufe("1", 10), stufe("2", 7)))).aborts().shouldBeEmpty()
    }

    test("auch auf einer Teilrechnung") {
        calculated(testInvoice(type = InvoiceType.PARTIAL, skonto = listOf(stufe("2", 7)))).aborts().shouldBeEmpty()
    }

    context("SKONTO_AUF_DIESER_ART_NICHT_MOEGLICH") {
        // Jede Stufe ist zusätzlich falsch, und es bleibt beim einen Befund (#97).
        val invalid = listOf(stufe("2", 30))

        test("auf der Abschlagsrechnung") {
            skontoRules(testInvoice(type = InvoiceType.PREPAYMENT, skonto = invalid)) shouldBe
                listOf(Rule.SKONTO_AUF_DIESER_ART_NICHT_MOEGLICH)
        }

        test("auf der Schlussrechnung") {
            val finalInvoice =
                testInvoice(
                    type = InvoiceType.FINAL,
                    items = listOf(testItem(unitPrice = "20000.00")),
                    prepayments = listOf(testPrepayment()),
                    skonto = invalid,
                )

            skontoRules(finalInvoice) shouldBe listOf(Rule.SKONTO_AUF_DIESER_ART_NICHT_MOEGLICH)
        }

        test("auf der Stornorechnung") {
            val cancellation =
                testInvoice(
                    type = InvoiceType.CANCELLATION,
                    items = listOf(testItem(quantity = "-1")),
                    cancelled = testCancelled(),
                    skonto = invalid,
                )

            skontoRules(cancellation) shouldBe listOf(Rule.SKONTO_AUF_DIESER_ART_NICHT_MOEGLICH)
        }

        test("die Meldung nennt die Art") {
            calculated(testInvoice(type = InvoiceType.PREPAYMENT, skonto = invalid))
                .messageOf(Rule.SKONTO_AUF_DIESER_ART_NICHT_MOEGLICH) shouldContain "Abschlagsrechnung"
        }
    }

    test("SKONTO_OHNE_ZAHLUNGSAUFFORDERUNG: bei Gesamtbetrag null, und nur dieser Befund") {
        val nothingToPay =
            testInvoice(items = listOf(testItem(quantity = "0")), skonto = listOf(stufe("2", 30)))

        skontoRules(nothingToPay) shouldBe listOf(Rule.SKONTO_OHNE_ZAHLUNGSAUFFORDERUNG)
    }

    context("SKONTOFRIST_NICHT_VOR_ZAHLUNGSZIEL") {
        test("eine Frist gleich dem Zahlungsziel") {
            skontoRules(testInvoice(skonto = listOf(stufe("2", 14)))) shouldBe
                listOf(Rule.SKONTOFRIST_NICHT_VOR_ZAHLUNGSZIEL)
        }

        test("eine Frist nach dem Zahlungsziel nennt beide") {
            val message =
                calculated(testInvoice(skonto = listOf(stufe("2", 20))))
                    .messageOf(Rule.SKONTOFRIST_NICHT_VOR_ZAHLUNGSZIEL)

            message shouldContain "20"
            message shouldContain "14"
        }

        test("bei Zahlungsziel null ist keine Frist möglich") {
            skontoRules(testInvoice(paymentTermDaysOverride = 0, skonto = listOf(stufe("2", 0)))) shouldBe
                listOf(Rule.SKONTOFRIST_NICHT_VOR_ZAHLUNGSZIEL)
        }

        test("eine Frist von null Tagen vor einem späteren Zahlungsziel ist zulässig") {
            skontoRules(testInvoice(skonto = listOf(stufe("2", 0)))).shouldBeEmpty()
        }
    }

    test("SKONTOFRIST_NEGATIV") {
        skontoRules(testInvoice(skonto = listOf(stufe("2", -1)))) shouldBe listOf(Rule.SKONTOFRIST_NEGATIV)
    }

    test("SKONTOFRIST_DOPPELT") {
        skontoRules(testInvoice(skonto = listOf(stufe("2", 7), stufe("1", 7)))) shouldBe
            listOf(Rule.SKONTOFRIST_DOPPELT)
    }

    context("SKONTOSTUFE_WERTLOS") {
        test("eine spätere Frist mit höherem Satz, auch in vertauschter Reihenfolge") {
            skontoRules(testInvoice(skonto = listOf(stufe("2", 10), stufe("1", 7)))) shouldBe
                listOf(Rule.SKONTOSTUFE_WERTLOS)
        }

        test("eine spätere Frist mit gleichem Satz") {
            skontoRules(testInvoice(skonto = listOf(stufe("2", 7), stufe("2", 10)))) shouldBe
                listOf(Rule.SKONTOSTUFE_WERTLOS)
        }
    }

    context("SKONTOSATZ_UNGUELTIG") {
        listOf("0", "-2", "100", "120", "2.125").forEach { rate ->
            test("Satz $rate") {
                skontoRules(testInvoice(skonto = listOf(stufe(rate, 7)))) shouldBe listOf(Rule.SKONTOSATZ_UNGUELTIG)
            }
        }

        test("zwei Nachkommastellen sind zulässig") {
            skontoRules(testInvoice(skonto = listOf(stufe("2.25", 7)))).shouldBeEmpty()
        }
    }
})

private val SKONTO_RULES =
    setOf(
        Rule.SKONTO_AUF_DIESER_ART_NICHT_MOEGLICH,
        Rule.SKONTO_OHNE_ZAHLUNGSAUFFORDERUNG,
        Rule.SKONTOFRIST_NEGATIV,
        Rule.SKONTOFRIST_NICHT_VOR_ZAHLUNGSZIEL,
        Rule.SKONTOFRIST_DOPPELT,
        Rule.SKONTOSTUFE_WERTLOS,
        Rule.SKONTOSATZ_UNGUELTIG,
    )
