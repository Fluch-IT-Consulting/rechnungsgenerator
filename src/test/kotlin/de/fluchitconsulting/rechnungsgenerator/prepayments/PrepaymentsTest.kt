package de.fluchitconsulting.rechnungsgenerator.prepayments

import de.fluchitconsulting.rechnungsgenerator.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.model.TaxShare
import de.fluchitconsulting.rechnungsgenerator.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.model.testItem
import de.fluchitconsulting.rechnungsgenerator.model.testPrepayment
import de.fluchitconsulting.rechnungsgenerator.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.rules.Rule
import de.fluchitconsulting.rechnungsgenerator.rules.messageOf
import de.fluchitconsulting.rechnungsgenerator.rules.rules
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.values.Money
import de.fluchitconsulting.rechnungsgenerator.values.Percent
import de.fluchitconsulting.rechnungsgenerator.values.TaxCategory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.time.LocalDate

/**
 * Prüft, wie aus Abschlagsrechnungen die Abschläge einer Schlussrechnung
 * werden – ohne Dateien und ohne Kommando, aber über dieselbe Naht wie das
 * Kommando: [prepaymentsOf] mit Namen hinein, [CollectedPrepayments] heraus.
 */
class PrepaymentsTest : FunSpec({

    val config = testSellerConfig()

    // Der Name ist hier nur ein Text; im Kommando ist es der Dateiname.
    fun named(vararg invoices: Invoice): List<NamedInvoice> = invoices.map { NamedInvoice("${it.number}.yaml", it) }

    // testInvoice trägt die Nummer R26-001 und das Datum 14.08.2026.
    val abschlagsrechnung =
        testInvoice(
            type = InvoiceType.PREPAYMENT,
            items = listOf(testItem(unitPrice = "4000.00")),
            receivedOn = LocalDate.of(2026, 8, 10),
        )

    test("aus einer Abschlagsrechnung wird ihr Abschlag, mit Netto und Steuer je Satz") {
        val abschlag =
            prepaymentsOf(named(abschlagsrechnung), config)
                .complete()
                .entries
                .single()
                .prepayment

        abschlag shouldBe
            testPrepayment(
                invoiceNumber = "R26-001",
                issueDate = LocalDate.of(2026, 8, 14),
                receivedOn = LocalDate.of(2026, 8, 10),
                net = "4000.00",
                vat = "760.00",
            )
    }

    test("ein gemischter Abschlag bekommt je Satz einen Steueranteil") {
        val mixed =
            abschlagsrechnung.copy(
                items = listOf(testItem(unitPrice = "1000.00"), testItem(unitPrice = "500.00", vatRate = "7")),
            )

        val abschlag =
            prepaymentsOf(named(mixed), config)
                .complete()
                .entries
                .single()
                .prepayment

        abschlag.taxShares shouldBe
            listOf(
                TaxShare(TaxCategory.STANDARD, Percent("19"), Money("1000.00"), Money("190.00")),
                TaxShare(TaxCategory.STANDARD, Percent("7"), Money("500.00"), Money("35.00")),
            )
    }

    test("jeder Eintrag trägt den Namen seiner Rechnung, in der übergebenen Reihenfolge") {
        // Bis #118 heftete das Kommando die Dateinamen per zip an – nach der
        // Position, und ohne dass ein Test die Paarung sah.
        val second = abschlagsrechnung.copy(number = "R26-002")

        val entries = prepaymentsOf(named(second, abschlagsrechnung), config).complete().entries

        entries.map { it.name to it.prepayment.invoiceNumber } shouldBe
            listOf("R26-002.yaml" to "R26-002", "R26-001.yaml" to "R26-001")
    }

    test("aus einer gewöhnlichen Rechnung wird kein Abschlag") {
        // Sonst setzte die Schlussrechnung eine 19-%-Rechnung klaglos ab – keine
        // ihrer Regeln kennt die Art des Dokuments, aus dem ein Abschlag stammt.
        val rechnung = testInvoice()

        val message = prepaymentsOf(named(rechnung), config).rejected().aborts.messageOf(Rule.KEINE_ABSCHLAGSRECHNUNG)

        message shouldContain "R26-001"
        message shouldContain "keine Abschlagsrechnung"
    }

    test("ein Dokument anderer Art wird nicht auch noch geprüft") {
        // Es fliegt ohnehin aus dem Aufruf; seine übrigen Befunde gälten einem
        // Dokument, das hier nichts zu suchen hat (#118). Der Regelsatz 0 %
        // würde sonst REGELSATZ_MIT_NULL melden.
        val rechnung = testInvoice(items = listOf(testItem(vatRate = "0")))

        prepaymentsOf(named(rechnung), config).rejected().aborts.rules() shouldBe listOf(Rule.KEINE_ABSCHLAGSRECHNUNG)
    }

    test("Abschlagsrechnungen an verschiedene Kunden werden nicht zusammen abgesetzt") {
        // Die Schlussrechnung kennt die Kunden ihrer Abschläge nicht – prüfen
        // kann das nur, wer die Abschlagsrechnungen noch in der Hand hat.
        val otherCustomer =
            abschlagsrechnung.copy(
                number = "R26-002",
                customer = abschlagsrechnung.customer.copy(name = "Andere Software AG"),
            )

        val message =
            prepaymentsOf(named(abschlagsrechnung, otherCustomer), config)
                .rejected()
                .aborts
                .messageOf(Rule.ABSCHLAGSRECHNUNGEN_VERSCHIEDENE_KUNDEN)

        message shouldContain "Beispiel Software GmbH"
        message shouldContain "Andere Software AG"
    }

    test("dieselbe Nummer zweimal wird abgelehnt") {
        val message =
            prepaymentsOf(named(abschlagsrechnung, abschlagsrechnung), config)
                .rejected()
                .aborts
                .messageOf(Rule.ABSCHLAGSRECHNUNG_DOPPELT)

        message shouldContain "R26-001"
        message shouldContain "zweimal"
    }

    test("anders geschriebene Nummern sind verschiedene Abschlagsrechnungen") {
        // Der Generator nimmt nichts über das Schema einer Nummer an (#53):
        // Anderswo können "a-1" und "A-1" zwei verschiedene Rechnungen sein.
        val upper = abschlagsrechnung.copy(number = "R26-009")
        val lower = abschlagsrechnung.copy(number = "r26-009")

        val entries = prepaymentsOf(named(upper, lower), config).complete().entries

        entries.map { it.prepayment.invoiceNumber } shouldBe listOf("R26-009", "r26-009")
    }

    test("eine Nummer mit Leerraum ist kein Duplikat, aber auch kein Abschlag") {
        // Bis #70 stand "R26-009 " im vorigen Test als dritter Abschlag. Seit
        // ADR-0005 ist Leerraum am Rand ein Abbruch – und aus einer Rechnung mit
        // Abbruch wird kein Abschlag (ADR-0007), also auch kein Block (#118).
        // Exakt verglichen wird trotzdem: Als Duplikat von "R26-009" gilt sie nicht.
        val upper = abschlagsrechnung.copy(number = "R26-009")
        val padded = abschlagsrechnung.copy(number = "R26-009 ")

        val aborts = prepaymentsOf(named(upper, padded), config).rejected().aborts

        aborts.rules() shouldBe listOf(Rule.RECHNUNGSNUMMER_MIT_LEERRAUM)
        aborts.rules() shouldNotContain Rule.ABSCHLAGSRECHNUNG_DOPPELT
    }

    test("ein Regelverstoß in einer Abschlagsrechnung nennt ihre Nummer") {
        // Bei mehreren Abschlagsrechnungen sagte "Position 1 (…)" allein nicht,
        // in welcher. Die Nummer ist das, woran man die Rechnung wiedererkennt –
        // sie steht jetzt als Herkunft vor dem Gegenstand des Befunds.
        val contradictory = abschlagsrechnung.copy(number = "R26-002", items = listOf(testItem(vatRate = "0")))

        val message =
            prepaymentsOf(named(abschlagsrechnung, contradictory), config)
                .rejected()
                .aborts
                .messageOf(Rule.REGELSATZ_MIT_NULL)

        message shouldStartWith "R26-002, Position 1 "
        message shouldContain "widersprüchlich"
    }
})
