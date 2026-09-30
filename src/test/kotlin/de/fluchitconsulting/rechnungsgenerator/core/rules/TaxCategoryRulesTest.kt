package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.model.VatIdConfirmation
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.core.model.taxCategoryExamples
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testItem
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import de.fluchitconsulting.rechnungsgenerator.core.values.VatId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import kotlin.io.path.readText

/** BT-151 */
class TaxCategoryRulesTest : FunSpec({

    val config = testSellerConfig()

    test("für jede Kategorie ist entschieden, was sie verlangt") {
        // Ausgeschrieben, nicht aus dem Enum abgeleitet: Eine neue Kategorie ohne
        // Zeile hier wird rot.
        val expected =
            mapOf(
                TaxCategory.STANDARD to (false to VatIdConfirmation.NEVER),
                TaxCategory.ZERO_RATED to (false to VatIdConfirmation.NEVER),
                TaxCategory.EXEMPT to (false to VatIdConfirmation.NEVER),
                TaxCategory.REVERSE_CHARGE to (true to VatIdConfirmation.IN_EU),
                TaxCategory.INTRA_COMMUNITY to (true to VatIdConfirmation.ALWAYS),
                TaxCategory.EXPORT to (false to VatIdConfirmation.NEVER),
                TaxCategory.OUT_OF_SCOPE to (false to VatIdConfirmation.NEVER),
            )

        expected.keys shouldBe TaxCategory.entries.toSet()
        TaxCategory.entries.forEach { category ->
            withClue("${category.code} (${category.label})") {
                (category.requiresCustomerVatId to category.vatIdConfirmation) shouldBe expected[category]
            }
        }
    }

    test("Regelsatz bleibt der Normalfall und braucht nichts weiter") {
        val totals = CalculatedInvoice(testInvoice(), config)

        totals.vatGroups.single().category shouldBe TaxCategory.STANDARD
        totals.vatGroups.single().exemptionReason shouldBe null
    }

    test("Reverse Charge rechnet ohne Steuer und begründet das von selbst") {
        val invoice =
            testInvoice(
                items = listOf(testItem(unitPrice = "1000.00", vatRate = "0", taxCategory = TaxCategory.REVERSE_CHARGE)),
                customerVatId = VatId("ATU12345678"),
            )

        val totals = CalculatedInvoice(invoice, config)

        totals.grossTotal shouldBe Money("1000.00")
        // §14a Abs. 1 und 5 UStG
        totals.vatGroups.single().exemptionReason!! shouldContain "Steuerschuldnerschaft des Leistungsempfängers"
    }

    test("0 % und Kategorie AE ohne USt-IdNr. des Kunden wird abgelehnt") {
        val invoice =
            testInvoice(items = listOf(testItem(vatRate = "0", taxCategory = TaxCategory.REVERSE_CHARGE)))

        CalculatedInvoice(invoice, config).messageOf(Rule.KUNDEN_UST_ID_FEHLT) shouldContain
            "USt-IdNr. des Kunden"
    }

    test("eine steuerfreie Kategorie mit Steuersatz ist ein Widerspruch") {
        val invoice =
            testInvoice(
                items = listOf(testItem(vatRate = "19", taxCategory = TaxCategory.EXEMPT, taxExemptionReason = "Grund")),
            )

        val message = CalculatedInvoice(invoice, config).messageOf(Rule.SATZ_MUSS_NULL_SEIN)

        message shouldContain "verlangt einen Steuersatz von 0"
        message shouldContain "angegeben ist 19%"
    }

    test("ein Befreiungsgrund bei einer Kategorie ohne Befreiung wird abgelehnt") {
        val invoice = testInvoice(items = listOf(testItem(taxExemptionReason = "Steuerfrei nach §4 Nr. 21 UStG")))

        CalculatedInvoice(invoice, config).messageOf(Rule.BEFREIUNGSGRUND_UNZULAESSIG) shouldContain
            "keinen Steuerbefreiungsgrund"
    }

    test("Regelsatz mit 0 % weist auf die vermutlich gemeinte Kategorie hin") {
        val invoice = testInvoice(items = listOf(testItem(vatRate = "0")))

        CalculatedInvoice(invoice, config).messageOf(Rule.REGELSATZ_MIT_NULL) shouldContain "vermutlich"
    }

    test("Regelsatz unter 0 % wird abgelehnt, und nur mit dieser Kennung (#488, BR-S-05)") {
        val invoice = testInvoice(items = listOf(testItem(vatRate = "-19")))

        val calculated = CalculatedInvoice(invoice, config)

        calculated.messageOf(Rule.STEUERSATZ_NEGATIV) shouldContain "angegeben ist -19%"
        calculated.rules() shouldBe listOf(Rule.STEUERSATZ_NEGATIV)
    }

    test("ein Satz unter 0 % bei einer Kategorie mit Nullsatz meldet nur SATZ_MUSS_NULL_SEIN") {
        val invoice = testInvoice(items = listOf(testItem(vatRate = "-19", taxCategory = TaxCategory.ZERO_RATED)))

        CalculatedInvoice(invoice, config).rules() shouldBe listOf(Rule.SATZ_MUSS_NULL_SEIN)
    }

    test("Steuerbefreiung ohne Grund wird abgelehnt, wenn die Kategorie keinen vorgibt") {
        // E hat anders als AE keinen vorgesehenen Wortlaut.
        val invoice = testInvoice(items = listOf(testItem(vatRate = "0", taxCategory = TaxCategory.EXEMPT)))

        CalculatedInvoice(invoice, config).messageOf(Rule.BEFREIUNGSGRUND_FEHLT) shouldContain
            "steuerbefreiungsgrund"
    }

    test("zwei verschiedene Gründe in derselben Kategorie gehen nicht") {
        // BG-23 trägt genau einen Grund je Kategorie und Satz.
        val invoice =
            testInvoice(
                items =
                    listOf(
                        testItem(vatRate = "0", taxCategory = TaxCategory.EXEMPT, taxExemptionReason = "Grund A"),
                        testItem(vatRate = "0", taxCategory = TaxCategory.EXEMPT, taxExemptionReason = "Grund B"),
                    ),
            )

        val calculated = CalculatedInvoice(invoice, config)

        calculated.messageOf(Rule.BEFREIUNGSGRUENDE_UNEINHEITLICH) shouldContain "zwei verschiedene"
        calculated.vatGroups.single().reasons shouldBe setOf("Grund A", "Grund B")
        calculated.vatGroups.single().exemptionReason shouldBe null
    }

    test("wer einen Befreiungsgrund verlangt, verlangt auch den Satz 0") {
        // Darauf verlässt sich ItemBlocks.taxNotes. Eine Kategorie mit Grund und
        // Steuer wird hier entschieden, nicht im Renderer.
        TaxCategory.entries.filter { it.requiresReason }.forEach { category ->
            withClue("${category.code} (${category.label})") { category.requiresZeroRate shouldBe true }
        }
    }

    test("jede Sprache hat einen Vorgabegrund genau für die Kategorien, die einen mitbringen") {
        // Darauf verlässt sich BEFREIUNGSGRUND_FEHLT, das hasDefaultReason ohne Sprache fragt.
        TaxCategory.entries.forEach { category ->
            Language.entries.forEach { language ->
                withClue("${category.code} auf $language") {
                    (category.defaultReason(language) != null) shouldBe category.hasDefaultReason
                }
            }
        }
    }

    // Hier und nicht im Pflichtangaben-Netz, damit es auch ohne die Werkzeuge auffällt (#427).
    test("jede Kategorie mit Vorgabegrund hat eine Beispielrechnung, die in jeder Sprache ohne Abbruch durchrechnet") {
        taxCategoryExamples.keys shouldBe TaxCategory.entries.filter { it.hasDefaultReason }.toSet()

        val seller = YamlLoader.loadSellerConfig(Path.of("src/test/resources/stammdaten.yaml"))
        taxCategoryExamples.forEach { (category, file) ->
            Language.entries.forEach { language ->
                withClue("$file.yaml auf ${language.tag}") {
                    val text = "sprache: ${language.tag}\n" + Path.of("src/test/resources/$file.yaml").readText()
                    val calculated = CalculatedInvoice(YamlLoader.parseInvoice(text), seller)

                    calculated.aborts().shouldBeEmpty()
                    calculated.vatGroups.map { it.category } shouldBe listOf(category)
                    calculated.vatGroups.single().exemptionReason shouldBe category.defaultReason(language)
                }
            }
        }
    }

    test("gleicher Satz, verschiedene Kategorien ergeben getrennte Gruppen") {
        val invoice =
            testInvoice(
                items =
                    listOf(
                        testItem(vatRate = "0", taxCategory = TaxCategory.REVERSE_CHARGE),
                        testItem(vatRate = "0", taxCategory = TaxCategory.EXEMPT, taxExemptionReason = "§4 Nr. 21 UStG"),
                    ),
                customerVatId = VatId("ATU12345678"),
            )

        CalculatedInvoice(invoice, config).vatGroups.size shouldBe 2
    }
})
