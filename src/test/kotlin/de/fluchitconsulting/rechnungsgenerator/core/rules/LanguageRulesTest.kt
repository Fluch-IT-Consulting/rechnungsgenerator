package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.SkontoTier
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testItem
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent
import de.fluchitconsulting.rechnungsgenerator.core.values.VatId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** Was Englisch schon setzen kann (#377, #405). */
class LanguageRulesTest : FunSpec({

    val config = testSellerConfig()
    val english = Language.BRITISH_ENGLISH

    fun calculated(invoice: Invoice) = CalculatedInvoice(invoice, config)

    // Nur die Befunde dieser Regel – eine Stornorechnung ohne Bezug etwa hat
    // eigene, darum geht es hier nicht.
    fun languageFindings(invoice: Invoice): List<Finding> =
        calculated(invoice).verdict().findings.filter { it.rule == Rule.SPRACHE_NOCH_NICHT_MOEGLICH }

    val skonto = listOf(SkontoTier(rate = Percent("2"), days = 7))

    // Je Kategorie eine Position, die ihre übrigen Regeln besteht.
    val supportedItems =
        listOf(
            testItem(),
            testItem(vatRate = "0", taxCategory = TaxCategory.ZERO_RATED),
            testItem(vatRate = "0", taxCategory = TaxCategory.EXEMPT, taxExemptionReason = "Steuerfrei nach §4 Nr. 21 UStG"),
            testItem(vatRate = "0", taxCategory = TaxCategory.OUT_OF_SCOPE, taxExemptionReason = "Not subject to German VAT"),
            testItem(vatRate = "0", taxCategory = TaxCategory.REVERSE_CHARGE),
        )

    test("eine englische Rechnung mit S, Z, E, O und AE wird angenommen") {
        val invoice = testInvoice(items = supportedItems, customerVatId = VatId("ATU12345678"), language = english)

        calculated(invoice).aborts().shouldBeEmpty()
    }

    test("Deutsch kann alles, was das Programm kann") {
        val excluded = listOf(TaxCategory.REVERSE_CHARGE, TaxCategory.INTRA_COMMUNITY, TaxCategory.EXPORT)
        InvoiceType.entries.forEach { type ->
            withClue(type) {
                val items = excluded.map { testItem(vatRate = "0", taxCategory = it) }
                languageFindings(testInvoice(type = type, items = items, skonto = skonto)).shouldBeEmpty()
            }
        }
    }

    // Jede Art hat ihren englischen Namen und Pflichtsatz (#405). Kein Befund
    // dieser Regel, auch wo die Art eigene Regeln verletzte – ein Storno ohne
    // Bezug etwa.
    test("auf Englisch wird jede Art angenommen") {
        InvoiceType.entries.forEach { type ->
            withClue(type) { languageFindings(testInvoice(type = type, language = english)).shouldBeEmpty() }
        }
    }

    test("auf Englisch bricht jede Position mit K oder G ab und nennt sie") {
        val items =
            listOf(
                testItem(),
                testItem(vatRate = "0", taxCategory = TaxCategory.INTRA_COMMUNITY),
                testItem(vatRate = "0", taxCategory = TaxCategory.EXPORT),
            )

        val findings = languageFindings(testInvoice(items = items, language = english))

        findings.map { it.subject } shouldBe (1..2).map { Subject.Item(it, "Testposition") }
        findings.first().message shouldContain "K (innergemeinschaftliche Lieferung)"
    }

    // Auf den beiden Arten, die Skonto überhaupt tragen dürfen
    // (SKONTO_AUF_DIESER_ART_NICHT_MOEGLICH), #406.
    test("auf Englisch wird Skonto angenommen, auf Rechnung und Teilrechnung") {
        listOf(InvoiceType.INVOICE, InvoiceType.PARTIAL).forEach { type ->
            withClue(type) {
                calculated(testInvoice(type = type, skonto = skonto, language = english)).aborts().shouldBeEmpty()
            }
        }
    }
})
