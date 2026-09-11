package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.model.TaxShare
import de.fluchitconsulting.rechnung.values.InvoiceType
import de.fluchitconsulting.rechnung.values.Money
import de.fluchitconsulting.rechnung.values.Percent
import de.fluchitconsulting.rechnung.values.TaxCategory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.time.LocalDate

/**
 * Prüft, wie aus Abschlagsrechnungen die Abschläge einer Schlussrechnung
 * werden – ohne Dateien und ohne Kommando.
 */
class PrepaymentsTest : FunSpec({

    val config = testSellerConfig()

    // testInvoice trägt die Nummer R26-001 und das Datum 14.08.2026.
    val abschlagsrechnung =
        testInvoice(
            type = InvoiceType.PREPAYMENT,
            items = listOf(testItem(unitPrice = "4000.00")),
            receivedOn = LocalDate.of(2026, 8, 10),
        )

    test("aus einer Abschlagsrechnung wird ihr Abschlag, mit Netto und Steuer je Satz") {
        val abschlag = prepaymentOf(abschlagsrechnung, InvoiceCalculator.calculate(abschlagsrechnung, config))

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

        val abschlag = prepaymentOf(mixed, InvoiceCalculator.calculate(mixed, config))

        abschlag.taxShares shouldBe
            listOf(
                TaxShare(TaxCategory.STANDARD, Percent("19"), Money("1000.00"), Money("190.00")),
                TaxShare(TaxCategory.STANDARD, Percent("7"), Money("500.00"), Money("35.00")),
            )
    }

    test("aus einer gewöhnlichen Rechnung wird kein Abschlag") {
        // Sonst setzte die Schlussrechnung eine 19-%-Rechnung klaglos ab – keine
        // ihrer Regeln kennt die Art des Dokuments, aus dem ein Abschlag stammt.
        val rechnung = testInvoice()

        val failure =
            shouldThrow<InvoiceValidationException> { prepaymentOf(rechnung, InvoiceCalculator.calculate(rechnung, config)) }

        failure.message!! shouldContain "R26-001"
        failure.message!! shouldContain "keine Abschlagsrechnung"
    }

    test("mehrere Abschlagsrechnungen ergeben ihre Abschläge in der übergebenen Reihenfolge") {
        val second = abschlagsrechnung.copy(number = "R26-002")

        prepaymentsOf(listOf(second, abschlagsrechnung), config).map { it.invoiceNumber } shouldBe
            listOf("R26-002", "R26-001")
    }

    test("Abschlagsrechnungen an verschiedene Kunden werden nicht zusammen abgesetzt") {
        // Die Schlussrechnung kennt die Kunden ihrer Abschläge nicht – prüfen
        // kann das nur, wer die Abschlagsrechnungen noch in der Hand hat.
        val otherCustomer =
            abschlagsrechnung.copy(
                number = "R26-002",
                customer = abschlagsrechnung.customer.copy(name = "Andere Software AG"),
            )

        val failure = shouldThrow<InvoiceValidationException> { prepaymentsOf(listOf(abschlagsrechnung, otherCustomer), config) }

        failure.message!! shouldContain "Beispiel Software GmbH"
        failure.message!! shouldContain "Andere Software AG"
    }

    test("dieselbe Nummer zweimal wird abgelehnt") {
        val failure =
            shouldThrow<InvoiceValidationException> { prepaymentsOf(listOf(abschlagsrechnung, abschlagsrechnung), config) }

        failure.message!! shouldContain "R26-001"
        failure.message!! shouldContain "zweimal"
    }

    test("anders geschriebene Nummern sind verschiedene Abschlagsrechnungen") {
        // Der Generator nimmt nichts über das Schema einer Nummer an (#53):
        // Anderswo können "a-1" und "A-1" zwei verschiedene Rechnungen sein.
        val upper = abschlagsrechnung.copy(number = "R26-009")
        val lower = abschlagsrechnung.copy(number = "r26-009")
        val padded = abschlagsrechnung.copy(number = "R26-009 ")

        prepaymentsOf(listOf(upper, lower, padded), config).map { it.invoiceNumber } shouldBe
            listOf("R26-009", "r26-009", "R26-009 ")
    }

    test("ein Regelverstoß in einer Abschlagsrechnung nennt ihre Nummer") {
        // Bei mehreren Abschlagsrechnungen sagte "Position 1 (…)" allein nicht,
        // in welcher. Die Nummer ist das, woran man die Rechnung wiedererkennt.
        val contradictory = abschlagsrechnung.copy(number = "R26-002", items = listOf(testItem(vatRate = "0")))

        val failure =
            shouldThrow<InvoiceValidationException> { prepaymentsOf(listOf(abschlagsrechnung, contradictory), config) }

        failure.message!! shouldStartWith "R26-002: "
        failure.message!! shouldContain "widersprüchlich"
    }
})
