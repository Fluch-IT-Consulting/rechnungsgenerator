package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.calculation.EuMemberStates
import de.fluchitconsulting.rechnungsgenerator.core.model.CheckMethod
import de.fluchitconsulting.rechnungsgenerator.core.model.Customer
import de.fluchitconsulting.rechnungsgenerator.core.model.CustomerAddress
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.model.VatIdCheck
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testItem
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.values.CountryCode
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import de.fluchitconsulting.rechnungsgenerator.core.values.VatId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate

/** Warum die Regeln verschieden streng sind, steht an `VatIdConfirmation`. */
class VatIdCheckRulesTest : FunSpec({

    val config = testSellerConfig()

    fun invoice(
        country: String,
        category: TaxCategory,
        vatIdCheck: VatIdCheck? = null,
        vatId: VatId? = VatId("ATU12345678"),
    ) = testInvoice(items = listOf(testItem(vatRate = "0", taxCategory = category))).copy(
        customer =
            Customer(
                name = "Beispiel Software GmbH",
                address =
                    CustomerAddress(
                        street = "Industriestraße 1",
                        postalCode = "1010",
                        city = "Wien",
                        countryCode = CountryCode(country),
                    ),
                vatId = vatId,
                vatIdCheck = vatIdCheck,
            ),
    )

    val checked =
        VatIdCheck(
            date = LocalDate.of(2026, 8, 1),
            method = CheckMethod.QUALIFIED,
            reference = "BZSt-Protokoll 2026-08-01-4711",
        )

    test("Reverse Charge in die EU ohne Nachweis wird abgelehnt") {
        CalculatedInvoice(invoice("AT", TaxCategory.REVERSE_CHARGE), config)
            .messageOf(Rule.BESTAETIGUNGSABFRAGE_FEHLT) shouldContain "Bestätigungsabfrage"
    }

    // #406
    test("die Bestätigungsabfrage gilt auf Englisch wie auf Deutsch") {
        val english = invoice("AT", TaxCategory.REVERSE_CHARGE).copy(language = Language.BRITISH_ENGLISH)

        CalculatedInvoice(english, config).rules() shouldContain Rule.BESTAETIGUNGSABFRAGE_FEHLT
        CalculatedInvoice(english.copy(customer = english.customer.copy(vatIdCheck = checked)), config)
            .rules() shouldNotContain Rule.BESTAETIGUNGSABFRAGE_FEHLT
    }

    test("Reverse Charge in die EU mit Nachweis geht durch") {
        val totals = CalculatedInvoice(invoice("AT", TaxCategory.REVERSE_CHARGE, checked), config)

        totals.hints().shouldBeEmpty()
    }

    test("Reverse Charge ins Drittland verlangt keinen VIES-Nachweis") {
        val totals =
            CalculatedInvoice(
                invoice("CH", TaxCategory.REVERSE_CHARGE, vatId = VatId("CHE123456789")),
                config,
            )

        totals.messageOf(Rule.KEIN_VIES_MOEGLICH) shouldContain "Unternehmerbescheinigung"
    }

    test("innergemeinschaftliche Lieferung verlangt den Nachweis auch im Drittland-Fall") {
        // §6a UStG
        CalculatedInvoice(invoice("AT", TaxCategory.INTRA_COMMUNITY), config).rules() shouldContain
            Rule.BESTAETIGUNGSABFRAGE_FEHLT
    }

    // #490
    test("ohne USt-IdNr. kommen KUNDEN_UST_ID_FEHLT und BESTAETIGUNGSABFRAGE_FEHLT, die zweite ohne Nummer") {
        listOf(TaxCategory.INTRA_COMMUNITY, TaxCategory.REVERSE_CHARGE).forEach { category ->
            val totals = CalculatedInvoice(invoice("AT", category, vatId = null), config)

            totals.rules() shouldContainAll listOf(Rule.KUNDEN_UST_ID_FEHLT, Rule.BESTAETIGUNGSABFRAGE_FEHLT)
            totals.messageOf(Rule.BESTAETIGUNGSABFRAGE_FEHLT) shouldContain "die USt-IdNr. des Kunden beim BZSt"
        }
    }

    test("eine Abfrage nach dem Rechnungsdatum belegt nichts") {
        val later = checked.copy(date = LocalDate.of(2026, 12, 1))

        CalculatedInvoice(invoice("AT", TaxCategory.REVERSE_CHARGE, later), config)
            .messageOf(Rule.BESTAETIGUNGSABFRAGE_NACH_RECHNUNGSDATUM) shouldContain "nach dem Rechnungsdatum"
    }

    test("die einfache Abfrage wird angenommen, aber angemerkt") {
        val simple = checked.copy(method = CheckMethod.SIMPLE)

        val totals = CalculatedInvoice(invoice("AT", TaxCategory.REVERSE_CHARGE, simple), config)

        totals.messageOf(Rule.ABFRAGE_EINFACH) shouldContain "Vertrauensschutz"
    }

    test("eine Abfrage ohne Protokoll wird angemerkt") {
        val withoutReference = checked.copy(reference = null)

        CalculatedInvoice(invoice("AT", TaxCategory.REVERSE_CHARGE, withoutReference), config)
            .messageOf(Rule.ABFRAGE_OHNE_NACHWEIS) shouldContain "kein Nachweis"
    }

    test("eine alte Abfrage wird angemerkt, aber nicht abgelehnt") {
        val old = checked.copy(date = LocalDate.of(2025, 1, 1))

        val totals = CalculatedInvoice(invoice("AT", TaxCategory.REVERSE_CHARGE, old), config)

        totals.messageOf(Rule.ABFRAGE_ALT) shouldContain "Monate alt"
    }

    test("Griechenland zählt unter beiden Ländercodes zur EU") {
        // ISO 3166 kennt GR, die Umsatzsteuer-Systeme verwenden EL.
        listOf("GR", "EL").forEach { country ->
            CalculatedInvoice(invoice(country, TaxCategory.REVERSE_CHARGE), config).rules() shouldContain
                Rule.BESTAETIGUNGSABFRAGE_FEHLT
        }
    }

    test("inländische Steuerschuldumkehr braucht keine Abfrage") {
        // §13b Abs. 2 UStG; für eine deutsche Nummer gibt es die Abfrage nicht.
        val totals =
            CalculatedInvoice(
                invoice("DE", TaxCategory.REVERSE_CHARGE, vatId = VatId("DE123456788")),
                config,
            )

        totals.hints().shouldBeEmpty()
    }

    test("Regelsatz braucht überhaupt keine Abfrage") {
        CalculatedInvoice(testInvoice(), config).hints().shouldBeEmpty()
    }

    context("Alter der EU-Liste") {
        fun invoiceOn(date: LocalDate) =
            invoice("CH", TaxCategory.REVERSE_CHARGE, vatId = VatId("CHE123456789")).copy(issueDate = date)

        test("frische Liste erzeugt keinen Hinweis auf ihr Alter") {
            val totals = CalculatedInvoice(invoiceOn(EuMemberStates.AS_OF.plusMonths(6)), config)

            totals.rules() shouldNotContain Rule.EU_LISTE_ALT
        }

        test("ab zwölf Monaten wird zum Abgleich aufgefordert") {
            val totals = CalculatedInvoice(invoiceOn(EuMemberStates.AS_OF.plusMonths(13)), config)

            totals.messageOf(Rule.EU_LISTE_ALT) shouldContain "13 Monate alt"
        }

        test("bei einer Inlandsrechnung bleibt die Liste unerwähnt") {
            // Der Leistungszeitraum rückt mit, sonst meldete sich RECHNUNG_SPAET.
            val date = EuMemberStates.AS_OF.plusYears(5)
            val domestic =
                testInvoice().let {
                    it.copy(
                        issueDate = date,
                        servicePeriod = it.servicePeriod.copy(start = date.minusMonths(1), end = date.minusDays(1)),
                    )
                }

            CalculatedInvoice(domestic, config).hints().shouldBeEmpty()
        }
    }
})
