package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.calculation.InvoiceCalculator
import de.fluchitconsulting.rechnungsgenerator.model.Address
import de.fluchitconsulting.rechnungsgenerator.model.CheckMethod
import de.fluchitconsulting.rechnungsgenerator.model.Customer
import de.fluchitconsulting.rechnungsgenerator.model.VatIdCheck
import de.fluchitconsulting.rechnungsgenerator.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.model.testItem
import de.fluchitconsulting.rechnungsgenerator.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.values.CountryCode
import de.fluchitconsulting.rechnungsgenerator.values.EuMemberStates
import de.fluchitconsulting.rechnungsgenerator.values.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.values.VatId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate

/**
 * Ohne dokumentierte Bestätigungsabfrage keine Rechnung ohne Umsatzsteuer.
 *
 * Die Regeln sind absichtlich verschieden streng, weil die Rechtslage es ist –
 * die Begründung steht am InvoiceCalculator. Diese Tests halten fest, dass die
 * Unterscheidung zwischen EU und Drittland tatsächlich gemacht wird und nicht
 * bloß im Kommentar behauptet ist.
 */
class VatIdCheckRulesTest : FunSpec({

    val config = testSellerConfig()

    fun rechnung(
        land: String,
        kategorie: TaxCategory,
        pruefung: VatIdCheck? = null,
        vatId: VatId? = VatId("ATU12345678"),
    ) = testInvoice(items = listOf(testItem(vatRate = "0", taxCategory = kategorie))).copy(
        customer =
            Customer(
                name = "Beispiel Software GmbH",
                address = Address(street = "Industriestraße 1", postalCode = "1010", city = "Wien", countryCode = CountryCode(land)),
                vatId = vatId,
                vatIdCheck = pruefung,
            ),
    )

    val geprueft =
        VatIdCheck(
            date = LocalDate.of(2026, 8, 1),
            method = CheckMethod.QUALIFIED,
            reference = "BZSt-Protokoll 2026-08-01-4711",
        )

    test("Reverse Charge in die EU ohne Nachweis wird abgelehnt") {
        InvoiceCalculator
            .calculate(rechnung("AT", TaxCategory.REVERSE_CHARGE), config)
            .messageOf(Rule.BESTAETIGUNGSABFRAGE_FEHLT) shouldContain "Bestätigungsabfrage"
    }

    test("Reverse Charge in die EU mit Nachweis geht durch") {
        val totals = InvoiceCalculator.calculate(rechnung("AT", TaxCategory.REVERSE_CHARGE, geprueft), config)

        totals.hints().shouldBeEmpty()
    }

    test("Reverse Charge ins Drittland verlangt keinen VIES-Nachweis") {
        // Für die Schweiz gibt es keine VIES-Abfrage. Einen Nachweis zu
        // verlangen, den es nicht geben kann, wäre keine Strenge, sondern falsch.
        val totals =
            InvoiceCalculator.calculate(
                rechnung("CH", TaxCategory.REVERSE_CHARGE, vatId = VatId("CHE123456789")),
                config,
            )

        totals.messageOf(Rule.KEIN_VIES_MOEGLICH) shouldContain "Unternehmerbescheinigung"
    }

    test("innergemeinschaftliche Lieferung verlangt den Nachweis auch im Drittland-Fall") {
        // §6a UStG kennt hier keinen Spielraum.
        InvoiceCalculator.calculate(rechnung("AT", TaxCategory.INTRA_COMMUNITY), config).rules() shouldContain
            Rule.BESTAETIGUNGSABFRAGE_FEHLT
    }

    test("eine Abfrage nach dem Rechnungsdatum belegt nichts") {
        val spaeter = geprueft.copy(date = LocalDate.of(2026, 12, 1))

        InvoiceCalculator
            .calculate(rechnung("AT", TaxCategory.REVERSE_CHARGE, spaeter), config)
            .messageOf(Rule.BESTAETIGUNGSABFRAGE_NACH_RECHNUNGSDATUM) shouldContain "nach dem Rechnungsdatum"
    }

    test("die einfache Abfrage wird angenommen, aber angemerkt") {
        val einfach = geprueft.copy(method = CheckMethod.SIMPLE)

        val totals = InvoiceCalculator.calculate(rechnung("AT", TaxCategory.REVERSE_CHARGE, einfach), config)

        totals.messageOf(Rule.ABFRAGE_EINFACH) shouldContain "Vertrauensschutz"
    }

    test("eine Abfrage ohne Protokoll wird angemerkt") {
        val ohneNachweis = geprueft.copy(reference = null)

        InvoiceCalculator
            .calculate(rechnung("AT", TaxCategory.REVERSE_CHARGE, ohneNachweis), config)
            .messageOf(Rule.ABFRAGE_OHNE_NACHWEIS) shouldContain "kein Nachweis"
    }

    test("eine alte Abfrage wird angemerkt, aber nicht abgelehnt") {
        val alt = geprueft.copy(date = LocalDate.of(2025, 1, 1))

        val totals = InvoiceCalculator.calculate(rechnung("AT", TaxCategory.REVERSE_CHARGE, alt), config)

        totals.messageOf(Rule.ABFRAGE_ALT) shouldContain "Monate alt"
    }

    test("Griechenland zählt unter beiden Ländercodes zur EU") {
        // ISO 3166 kennt GR, die Umsatzsteuer-Systeme verwenden EL.
        listOf("GR", "EL").forEach { land ->
            InvoiceCalculator.calculate(rechnung(land, TaxCategory.REVERSE_CHARGE), config).rules() shouldContain
                Rule.BESTAETIGUNGSABFRAGE_FEHLT
        }
    }

    test("inländische Steuerschuldumkehr braucht keine Abfrage") {
        // §13b Abs. 2 im Inland, etwa Bauleistungen: Das BZSt bestätigt
        // ausländische USt-IdNrn.; für die Nummer eines deutschen Kunden gibt
        // es diese Abfrage gar nicht. Sie zu verlangen wäre unerfüllbar.
        val totals =
            InvoiceCalculator.calculate(
                rechnung("DE", TaxCategory.REVERSE_CHARGE, vatId = VatId("DE123456788")),
                config,
            )

        totals.hints().shouldBeEmpty()
    }

    test("Regelsatz braucht überhaupt keine Abfrage") {
        InvoiceCalculator.calculate(testInvoice(), config).hints().shouldBeEmpty()
    }

    context("Alter der EU-Liste") {
        // Die Liste ist heute richtig und wird irgendwann still falsch. Ohne
        // Prüfung merkt das niemand, weil nichts kaputtgeht – es wird nur leise
        // die falsche Regel angewandt.
        fun rechnungAm(datum: LocalDate) =
            rechnung("CH", TaxCategory.REVERSE_CHARGE, vatId = VatId("CHE123456789")).copy(issueDate = datum)

        test("frische Liste erzeugt keinen Hinweis auf ihr Alter") {
            val totals = InvoiceCalculator.calculate(rechnungAm(EuMemberStates.AS_OF.plusMonths(6)), config)

            totals.rules() shouldNotContain Rule.EU_LISTE_ALT
        }

        test("ab zwölf Monaten wird zum Abgleich aufgefordert") {
            val totals = InvoiceCalculator.calculate(rechnungAm(EuMemberStates.AS_OF.plusMonths(13)), config)

            totals.messageOf(Rule.EU_LISTE_ALT) shouldContain "13 Monate alt"
        }

        test("bei einer Inlandsrechnung bleibt die Liste unerwähnt") {
            // Sie wird dort gar nicht befragt – ein Hinweis wäre Lärm, und Lärm
            // führt dazu, dass auch die wichtigen Hinweise überlesen werden.
            // Der Leistungszeitraum rückt mit dem Datum mit: Sonst meldete sich
            // der Hinweis auf eine späte Rechnung (§14 Abs. 2 UStG, #47), und
            // um den geht es hier nicht.
            val datum = EuMemberStates.AS_OF.plusYears(5)
            val inland =
                testInvoice().let {
                    it.copy(
                        issueDate = datum,
                        servicePeriod = it.servicePeriod.copy(start = datum.minusMonths(1), end = datum.minusDays(1)),
                    )
                }

            InvoiceCalculator.calculate(inland, config).hints().shouldBeEmpty()
        }
    }
})
