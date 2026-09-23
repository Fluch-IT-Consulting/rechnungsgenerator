package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testItem
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import de.fluchitconsulting.rechnungsgenerator.core.values.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.values.VatId
import de.fluchitconsulting.rechnungsgenerator.core.values.VatIdConfirmation
import io.kotest.assertions.withClue
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

    test("für jede Kategorie ist entschieden, was sie verlangt") {
        // Die Tabelle steht absichtlich ausgeschrieben da und wird nicht aus
        // dem Enum abgeleitet – sie ist die zweite Meinung dazu. Ihr Zweck ist
        // weniger, die heutigen Werte zu bestätigen, als eine künftige
        // Kategorie nicht durchrutschen zu lassen: Wer eine ergänzt, ohne hier
        // eine Zeile hinzuzufügen, bekommt einen roten Test statt einer stillen
        // Vorgabe.
        val erwartet =
            mapOf(
                TaxCategory.STANDARD to (false to VatIdConfirmation.NEVER),
                TaxCategory.ZERO_RATED to (false to VatIdConfirmation.NEVER),
                TaxCategory.EXEMPT to (false to VatIdConfirmation.NEVER),
                TaxCategory.REVERSE_CHARGE to (true to VatIdConfirmation.IN_EU),
                TaxCategory.INTRA_COMMUNITY to (true to VatIdConfirmation.ALWAYS),
                TaxCategory.EXPORT to (false to VatIdConfirmation.NEVER),
                TaxCategory.OUT_OF_SCOPE to (false to VatIdConfirmation.NEVER),
            )

        erwartet.keys shouldBe TaxCategory.entries.toSet()
        TaxCategory.entries.forEach { kategorie ->
            withClue("${kategorie.code} (${kategorie.label})") {
                (kategorie.requiresCustomerVatId to kategorie.vatIdConfirmation) shouldBe erwartet[kategorie]
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
        // §14a Abs. 5 UStG verlangt genau diesen Hinweis; ohne eigene Angabe
        // setzt ihn die Kategorie selbst ein.
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
        // Der Satz, der beanstandet wird, steht lesbar in der Meldung: "19%"
        // und nicht "19.0000000%" – sonst sucht der Leser den Fehler bei den
        // Nullen statt bei seinem Steuersatz.
        message shouldContain "angegeben ist 19%"
    }

    test("ein Befreiungsgrund bei einer Kategorie ohne Befreiung wird abgelehnt") {
        // S ist der Regelsatz. Ein Steuerbefreiungsgrund daneben heißt: Entweder
        // stimmt die Kategorie nicht oder der Grund gehört nicht dorthin – so
        // oder so soll es niemand auf dem PDF lesen.
        val invoice = testInvoice(items = listOf(testItem(taxExemptionReason = "Steuerfrei nach §4 Nr. 21 UStG")))

        CalculatedInvoice(invoice, config).messageOf(Rule.BEFREIUNGSGRUND_UNZULAESSIG) shouldContain
            "keinen Steuerbefreiungsgrund"
    }

    test("Regelsatz mit 0 % weist auf die vermutlich gemeinte Kategorie hin") {
        val invoice = testInvoice(items = listOf(testItem(vatRate = "0")))

        CalculatedInvoice(invoice, config).messageOf(Rule.REGELSATZ_MIT_NULL) shouldContain "vermutlich"
    }

    test("Steuerbefreiung ohne Grund wird abgelehnt, wenn die Kategorie keinen vorgibt") {
        // E hat anders als AE keinen vorgesehenen Wortlaut – der Grund hängt
        // vom Befreiungstatbestand ab und muss deshalb angegeben werden.
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
        // Die Gruppe kennt beide Gründe und setzt keinen davon aufs PDF.
        calculated.vatGroups.single().reasons shouldBe setOf("Grund A", "Grund B")
        calculated.vatGroups.single().exemptionReason shouldBe null
    }

    test("wer einen Befreiungsgrund verlangt, verlangt auch den Satz 0") {
        // Darauf verlässt sich ItemBlocks.taxNotes: Es fragt nur nach dem Grund
        // und nicht zusätzlich, ob der Steuerbetrag null ist. Eine Kategorie
        // mit Grund und Steuer bräche diese Annahme – dann hier entscheiden,
        // nicht im Renderer.
        TaxCategory.entries.filter { it.requiresReason }.forEach { kategorie ->
            withClue("${kategorie.code} (${kategorie.label})") { kategorie.requiresZeroRate shouldBe true }
        }
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

        CalculatedInvoice(invoice, config).vatGroups.size shouldBe 2
    }
})
