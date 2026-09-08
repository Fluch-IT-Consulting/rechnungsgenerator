package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.values.Money
import de.fluchitconsulting.rechnung.values.TaxCategory
import de.fluchitconsulting.rechnung.values.VatId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Die Regeln, die EN 16931 an die Steuerkategorie (BT-151) knüpft. Sie greifen
 * beim Rechnen, nicht erst beim Export: Eine widersprüchliche Rechnung soll gar
 * nicht erst als PDF entstehen.
 */
class TaxCategoryRulesTest : FunSpec({

    val config = testSellerConfig()

    test("Regelsatz bleibt der Normalfall und braucht nichts weiter") {
        val totals = InvoiceCalculator.calculate(testInvoice(), config)

        totals.vatGroups.single().category shouldBe TaxCategory.STANDARD
        totals.vatGroups.single().exemptionReason shouldBe null
    }

    test("Reverse Charge rechnet ohne Steuer und begründet das von selbst") {
        val invoice =
            testInvoice(
                items = listOf(testItem(unitPrice = "1000.00", vatRate = "0", taxCategory = TaxCategory.REVERSE_CHARGE)),
                customerVatId = VatId("ATU12345678"),
            )

        val totals = InvoiceCalculator.calculate(invoice, config)

        totals.grossTotal shouldBe Money("1000.00")
        // §14a Abs. 5 UStG verlangt genau diesen Hinweis; ohne eigene Angabe
        // setzt ihn die Kategorie selbst ein.
        totals.vatGroups.single().exemptionReason!! shouldContain "Steuerschuldnerschaft des Leistungsempfängers"
    }

    test("0 % und Kategorie AE ohne USt-IdNr. des Kunden wird abgelehnt") {
        val invoice =
            testInvoice(items = listOf(testItem(vatRate = "0", taxCategory = TaxCategory.REVERSE_CHARGE)))

        shouldThrow<InvoiceValidationException> { InvoiceCalculator.calculate(invoice, config) }
            .message!! shouldContain "USt-IdNr. des Kunden"
    }

    test("eine steuerfreie Kategorie mit Steuersatz ist ein Widerspruch") {
        val invoice =
            testInvoice(
                items = listOf(testItem(vatRate = "19", taxCategory = TaxCategory.EXEMPT, taxExemptionReason = "Grund")),
            )

        shouldThrow<InvoiceValidationException> { InvoiceCalculator.calculate(invoice, config) }
            .message!! shouldContain "verlangt einen Steuersatz von 0"
    }

    test("Regelsatz mit 0 % weist auf die vermutlich gemeinte Kategorie hin") {
        val invoice = testInvoice(items = listOf(testItem(vatRate = "0")))

        shouldThrow<InvoiceValidationException> { InvoiceCalculator.calculate(invoice, config) }
            .message!! shouldContain "vermutlich"
    }

    test("Steuerbefreiung ohne Grund wird abgelehnt, wenn die Kategorie keinen vorgibt") {
        // E hat anders als AE keinen vorgesehenen Wortlaut – der Grund hängt
        // vom Befreiungstatbestand ab und muss deshalb angegeben werden.
        val invoice = testInvoice(items = listOf(testItem(vatRate = "0", taxCategory = TaxCategory.EXEMPT)))

        shouldThrow<InvoiceValidationException> { InvoiceCalculator.calculate(invoice, config) }
            .message!! shouldContain "steuerbefreiungsgrund"
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

        shouldThrow<InvoiceValidationException> { InvoiceCalculator.calculate(invoice, config) }
            .message!! shouldContain "zwei verschiedene"
    }

    test("gleicher Satz, verschiedene Kategorien ergeben getrennte Gruppen") {
        // Beides 0 %, steuerlich aber grundverschieden – deshalb zwei Zeilen in
        // der Aufschlüsselung, nicht eine.
        val invoice =
            testInvoice(
                items =
                    listOf(
                        testItem(vatRate = "0", taxCategory = TaxCategory.REVERSE_CHARGE),
                        testItem(vatRate = "0", taxCategory = TaxCategory.EXEMPT, taxExemptionReason = "§4 Nr. 21 UStG"),
                    ),
                customerVatId = VatId("ATU12345678"),
            )

        InvoiceCalculator.calculate(invoice, config).vatGroups.size shouldBe 2
    }
})
