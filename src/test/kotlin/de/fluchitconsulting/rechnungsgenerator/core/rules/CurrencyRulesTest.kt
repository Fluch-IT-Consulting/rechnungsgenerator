package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testItem
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.values.Currency
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.string.shouldContain

/**
 * Eine Fremdwährung nur ohne ausgewiesene Umsatzsteuer (#409).
 */
class CurrencyRulesTest : FunSpec({

    fun calculated(invoice: Invoice) = CalculatedInvoice(invoice, testSellerConfig())

    // Je Kategorie ohne Steuerbetrag eine Position, die ihre übrigen Regeln
    // besteht: Satz 0, und wo die Kategorie ihn verlangt, ein Grund.
    fun untaxed(category: TaxCategory) =
        testItem(
            vatRate = "0",
            taxCategory = category,
            taxExemptionReason = "Grund".takeIf { category.requiresReason && !category.hasDefaultReason },
        )

    context("WAEHRUNG_MIT_UMSATZSTEUER") {
        test("eine Position mit Regelsatz in Fremdwährung bricht ab") {
            Currency.entries.filter { it != Currency.EUR }.forEach { currency ->
                withClue(currency) {
                    calculated(testInvoice(currency = currency)).aborts().rules() shouldContain
                        Rule.WAEHRUNG_MIT_UMSATZSTEUER
                }
            }
        }

        test("die Meldung nennt die Währung und die Positionen mit Regelsatz") {
            val invoice =
                testInvoice(
                    currency = Currency.USD,
                    items = listOf(testItem(), untaxed(TaxCategory.OUT_OF_SCOPE), testItem(vatRate = "7")),
                )

            val message = calculated(invoice).messageOf(Rule.WAEHRUNG_MIT_UMSATZSTEUER)

            message shouldContain "USD"
            message shouldContain "Positionen 1, 3"
        }

        test("in Euro gilt die Regel nicht") {
            calculated(testInvoice(currency = Currency.EUR)).rules() shouldNotContain Rule.WAEHRUNG_MIT_UMSATZSTEUER
        }

        test("jede Kategorie ohne Steuerbetrag ist in Fremdwährung zulässig") {
            // Auch Z: Der Satz ist null, umzurechnen ist nichts.
            TaxCategory.entries.filter { it != TaxCategory.STANDARD }.forEach { category ->
                withClue(category) {
                    calculated(testInvoice(currency = Currency.GBP, items = listOf(untaxed(category))))
                        .rules() shouldNotContain Rule.WAEHRUNG_MIT_UMSATZSTEUER
                }
            }
        }

        test("eine Rechnung in Fremdwährung ohne Regelsatz wird angenommen") {
            // Die Zusage der Regel: Mehr als sie steht einer Rechnung in USD
            // nicht im Weg.
            calculated(testInvoice(currency = Currency.USD, items = listOf(untaxed(TaxCategory.OUT_OF_SCOPE))))
                .aborts()
                .shouldBeEmpty()
        }
    }
})
