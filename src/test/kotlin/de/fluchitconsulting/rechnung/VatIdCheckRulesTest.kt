package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.model.Address
import de.fluchitconsulting.rechnung.model.CheckMethod
import de.fluchitconsulting.rechnung.model.Customer
import de.fluchitconsulting.rechnung.model.VatIdCheck
import de.fluchitconsulting.rechnung.values.CountryCode
import de.fluchitconsulting.rechnung.values.EuMemberStates
import de.fluchitconsulting.rechnung.values.TaxCategory
import de.fluchitconsulting.rechnung.values.VatId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
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
        shouldThrow<InvoiceValidationException> {
            InvoiceCalculator.calculate(rechnung("AT", TaxCategory.REVERSE_CHARGE), config)
        }.message!! shouldContain "Bestätigungsabfrage"
    }

    test("Reverse Charge in die EU mit Nachweis geht durch") {
        val totals = InvoiceCalculator.calculate(rechnung("AT", TaxCategory.REVERSE_CHARGE, geprueft), config)

        totals.hints.shouldBeEmpty()
    }

    test("Reverse Charge ins Drittland verlangt keinen VIES-Nachweis") {
        // Für die Schweiz gibt es keine VIES-Abfrage. Einen Nachweis zu
        // verlangen, den es nicht geben kann, wäre keine Strenge, sondern falsch.
        val totals =
            InvoiceCalculator.calculate(
                rechnung("CH", TaxCategory.REVERSE_CHARGE, vatId = VatId("CHE123456789")),
                config,
            )

        totals.hints.single() shouldContain "Unternehmerbescheinigung"
    }

    test("innergemeinschaftliche Lieferung verlangt den Nachweis auch im Drittland-Fall") {
        // §6a UStG kennt hier keinen Spielraum.
        shouldThrow<InvoiceValidationException> {
            InvoiceCalculator.calculate(rechnung("AT", TaxCategory.INTRA_COMMUNITY), config)
        }
    }

    test("eine Abfrage nach dem Rechnungsdatum belegt nichts") {
        val spaeter = geprueft.copy(date = LocalDate.of(2026, 12, 1))

        shouldThrow<InvoiceValidationException> {
            InvoiceCalculator.calculate(rechnung("AT", TaxCategory.REVERSE_CHARGE, spaeter), config)
        }.message!! shouldContain "nach dem Rechnungsdatum"
    }

    test("die einfache Abfrage wird angenommen, aber angemerkt") {
        val einfach = geprueft.copy(method = CheckMethod.SIMPLE)

        val totals = InvoiceCalculator.calculate(rechnung("AT", TaxCategory.REVERSE_CHARGE, einfach), config)

        totals.hints.single() shouldContain "Vertrauensschutz"
    }

    test("eine Abfrage ohne Protokoll wird angemerkt") {
        val ohneNachweis = geprueft.copy(reference = null)

        InvoiceCalculator
            .calculate(rechnung("AT", TaxCategory.REVERSE_CHARGE, ohneNachweis), config)
            .hints
            .single() shouldContain "kein Nachweis"
    }

    test("eine alte Abfrage wird angemerkt, aber nicht abgelehnt") {
        val alt = geprueft.copy(date = LocalDate.of(2025, 1, 1))

        val totals = InvoiceCalculator.calculate(rechnung("AT", TaxCategory.REVERSE_CHARGE, alt), config)

        totals.hints.single() shouldContain "Monate alt"
    }

    test("Griechenland zählt unter beiden Ländercodes zur EU") {
        // ISO 3166 kennt GR, die Umsatzsteuer-Systeme verwenden EL.
        listOf("GR", "EL").forEach { land ->
            shouldThrow<InvoiceValidationException> {
                InvoiceCalculator.calculate(rechnung(land, TaxCategory.REVERSE_CHARGE), config)
            }
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

        totals.hints.shouldBeEmpty()
    }

    test("Regelsatz braucht überhaupt keine Abfrage") {
        InvoiceCalculator.calculate(testInvoice(), config).hints.shouldBeEmpty()
    }

    context("Alter der EU-Liste") {
        // Die Liste ist heute richtig und wird irgendwann still falsch. Ohne
        // Prüfung merkt das niemand, weil nichts kaputtgeht – es wird nur leise
        // die falsche Regel angewandt.
        fun rechnungAm(datum: LocalDate) =
            rechnung("CH", TaxCategory.REVERSE_CHARGE, vatId = VatId("CHE123456789")).copy(issueDate = datum)

        test("frische Liste erzeugt keinen Hinweis auf ihr Alter") {
            val totals = InvoiceCalculator.calculate(rechnungAm(EuMemberStates.AS_OF.plusMonths(6)), config)

            totals.hints.none { it.contains("Mitgliedstaaten ist") } shouldBe true
        }

        test("ab zwölf Monaten wird zum Abgleich aufgefordert") {
            val totals = InvoiceCalculator.calculate(rechnungAm(EuMemberStates.AS_OF.plusMonths(13)), config)

            totals.hints.any { it.contains("13 Monate alt") } shouldBe true
        }

        test("bei einer Inlandsrechnung bleibt die Liste unerwähnt") {
            // Sie wird dort gar nicht befragt – ein Hinweis wäre Lärm, und Lärm
            // führt dazu, dass auch die wichtigen Hinweise überlesen werden.
            val inland =
                testInvoice().copy(issueDate = EuMemberStates.AS_OF.plusYears(5))

            InvoiceCalculator.calculate(inland, config).hints.shouldBeEmpty()
        }
    }
})
