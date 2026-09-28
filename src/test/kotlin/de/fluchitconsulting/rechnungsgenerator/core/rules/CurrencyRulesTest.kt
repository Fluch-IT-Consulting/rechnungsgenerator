package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.CurrencyConversion
import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testItem
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.values.Currency
import de.fluchitconsulting.rechnungsgenerator.core.values.ExchangeRate
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.string.shouldContain
import java.time.YearMonth

/**
 * Umsatzsteuer in Fremdwährung nur mit einer Umrechnung in Euro (#409, #418).
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

    val conversion = CurrencyConversion(ExchangeRate("1.1685"), YearMonth.of(2026, 7))

    // Alle Regeln dieser Gruppe, damit ein Test sagen kann, dass keine anschlägt.
    val currencyRules =
        listOf(
            Rule.WAEHRUNG_MIT_UMSATZSTEUER,
            Rule.WAEHRUNG_MIT_UMSATZSTEUER_AUF_DIESER_ART_NICHT_MOEGLICH,
            Rule.UMRECHNUNG_BEI_EURO,
            Rule.UMRECHNUNG_OHNE_UMSATZSTEUER,
        )

    context("WAEHRUNG_MIT_UMSATZSTEUER") {
        test("eine Position mit Regelsatz in Fremdwährung ohne Umrechnung bricht ab") {
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
            message shouldContain "'umrechnung'"
        }

        test("mit Umrechnung wird die Rechnung angenommen") {
            calculated(testInvoice(currency = Currency.USD, conversion = conversion)).aborts().shouldBeEmpty()
        }

        test("mit Umrechnung gilt keine Regel der Währung auf Rechnung, Teilrechnung und Storno") {
            listOf(InvoiceType.INVOICE, InvoiceType.PARTIAL, InvoiceType.CANCELLATION).forEach { type ->
                withClue(type) {
                    val rules = calculated(testInvoice(type = type, currency = Currency.USD, conversion = conversion)).rules()
                    currencyRules.forEach { rules shouldNotContain it }
                }
            }
        }

        test("in Euro gilt die Regel nicht") {
            calculated(testInvoice(currency = Currency.EUR)).rules() shouldNotContain Rule.WAEHRUNG_MIT_UMSATZSTEUER
        }
    }

    context("WAEHRUNG_MIT_UMSATZSTEUER_AUF_DIESER_ART_NICHT_MOEGLICH") {
        val types = listOf(InvoiceType.PREPAYMENT, InvoiceType.FINAL)

        test("Abschlags- und Schlussrechnung tragen keine Umsatzsteuer in Fremdwährung, auch nicht mit Umrechnung") {
            types.forEach { type ->
                listOf(null, conversion).forEach { umrechnung ->
                    withClue("$type, umrechnung = $umrechnung") {
                        val rules =
                            calculated(testInvoice(type = type, currency = Currency.USD, conversion = umrechnung)).rules()

                        rules shouldContain Rule.WAEHRUNG_MIT_UMSATZSTEUER_AUF_DIESER_ART_NICHT_MOEGLICH
                        // Nur dieser Befund: Ein Satz über die fehlende
                        // Umrechnung lenkte davon ab, dass keine hilft.
                        rules shouldNotContain Rule.WAEHRUNG_MIT_UMSATZSTEUER
                    }
                }
            }
        }

        test("die Meldung nennt Art, Währung und die Positionen mit Regelsatz") {
            val message =
                calculated(testInvoice(type = InvoiceType.PREPAYMENT, currency = Currency.GBP))
                    .messageOf(Rule.WAEHRUNG_MIT_UMSATZSTEUER_AUF_DIESER_ART_NICHT_MOEGLICH)

            message shouldContain "Abschlagsrechnung"
            message shouldContain "GBP"
            message shouldContain "Position 1"
        }

        test("ohne Regelsatz gilt die Regel nicht") {
            types.forEach { type ->
                withClue(type) {
                    calculated(
                        testInvoice(type = type, currency = Currency.USD, items = listOf(untaxed(TaxCategory.OUT_OF_SCOPE))),
                    ).rules() shouldNotContain Rule.WAEHRUNG_MIT_UMSATZSTEUER_AUF_DIESER_ART_NICHT_MOEGLICH
                }
            }
        }

        test("in Euro gilt die Regel nicht") {
            types.forEach { type ->
                withClue(type) {
                    calculated(testInvoice(type = type)).rules() shouldNotContain
                        Rule.WAEHRUNG_MIT_UMSATZSTEUER_AUF_DIESER_ART_NICHT_MOEGLICH
                }
            }
        }
    }

    context("UMRECHNUNG_BEI_EURO") {
        test("eine Rechnung in Euro mit Umrechnung bricht ab") {
            calculated(testInvoice(conversion = conversion)).aborts().rules() shouldContain Rule.UMRECHNUNG_BEI_EURO
        }

        test("ohne Umrechnung gilt die Regel nicht") {
            calculated(testInvoice()).rules() shouldNotContain Rule.UMRECHNUNG_BEI_EURO
        }
    }

    context("UMRECHNUNG_OHNE_UMSATZSTEUER") {
        test("eine Umrechnung ohne Position mit Regelsatz bricht ab") {
            calculated(
                testInvoice(currency = Currency.USD, conversion = conversion, items = listOf(untaxed(TaxCategory.ZERO_RATED))),
            ).aborts().rules() shouldContain Rule.UMRECHNUNG_OHNE_UMSATZSTEUER
        }

        test("mit einer Position mit Regelsatz gilt die Regel nicht") {
            val items = listOf(untaxed(TaxCategory.OUT_OF_SCOPE), testItem())

            calculated(testInvoice(currency = Currency.USD, conversion = conversion, items = items))
                .rules() shouldNotContain Rule.UMRECHNUNG_OHNE_UMSATZSTEUER
        }
    }

    context("ohne Regelsatz in Fremdwährung") {
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
