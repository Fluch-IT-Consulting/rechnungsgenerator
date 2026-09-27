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
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** Was Englisch im ersten Schritt schon setzen kann (#377). */
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
        )

    test("eine englische Rechnung mit S, Z, E und O wird angenommen") {
        calculated(testInvoice(items = supportedItems, language = english)).aborts().shouldBeEmpty()
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

    // Die Arten, die es auf Englisch schon gibt; mit jedem Schritt eine mehr (#405).
    val englishTypes = setOf(InvoiceType.INVOICE, InvoiceType.PARTIAL, InvoiceType.CANCELLATION)

    test("auf Englisch wird jede Art angenommen, deren Sätze übersetzt sind") {
        englishTypes.forEach { type ->
            withClue(type) { languageFindings(testInvoice(type = type, language = english)).shouldBeEmpty() }
        }
    }

    test("auf Englisch bricht jede übrige Art ab, mit einem Befund") {
        (InvoiceType.entries - englishTypes).forEach { type ->
            withClue(type) {
                val findings = languageFindings(testInvoice(type = type, language = english))

                findings.map { it.subject } shouldBe listOf(Subject.WholeInvoice)
                findings.single().message shouldContain type.documentName
            }
        }
    }

    test("auf Englisch bricht jede Position mit AE, K oder G ab und nennt sie") {
        val items =
            listOf(
                testItem(),
                testItem(vatRate = "0", taxCategory = TaxCategory.REVERSE_CHARGE),
                testItem(vatRate = "0", taxCategory = TaxCategory.INTRA_COMMUNITY),
                testItem(vatRate = "0", taxCategory = TaxCategory.EXPORT),
            )

        val findings = languageFindings(testInvoice(items = items, language = english))

        findings.map { it.subject } shouldBe (1..3).map { Subject.Item(it, "Testposition") }
        findings.first().message shouldContain "AE"
    }

    test("auf Englisch bricht Skonto ab") {
        languageFindings(testInvoice(skonto = skonto, language = english)).map { it.subject } shouldBe
            listOf(Subject.WholeInvoice)
    }
})
