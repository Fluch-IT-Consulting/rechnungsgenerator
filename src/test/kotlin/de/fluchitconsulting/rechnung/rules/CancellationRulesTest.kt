package de.fluchitconsulting.rechnung.rules

import de.fluchitconsulting.rechnung.InvoiceCalculator
import de.fluchitconsulting.rechnung.Rule
import de.fluchitconsulting.rechnung.aborts
import de.fluchitconsulting.rechnung.messageOf
import de.fluchitconsulting.rechnung.model.Address
import de.fluchitconsulting.rechnung.model.CancelledInvoice
import de.fluchitconsulting.rechnung.model.CheckMethod
import de.fluchitconsulting.rechnung.model.Customer
import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.model.ServicePeriod
import de.fluchitconsulting.rechnung.model.VatIdCheck
import de.fluchitconsulting.rechnung.rules
import de.fluchitconsulting.rechnung.testCancelled
import de.fluchitconsulting.rechnung.testInvoice
import de.fluchitconsulting.rechnung.testItem
import de.fluchitconsulting.rechnung.testSellerConfig
import de.fluchitconsulting.rechnung.values.CountryCode
import de.fluchitconsulting.rechnung.values.InvoiceType
import de.fluchitconsulting.rechnung.values.Money
import de.fluchitconsulting.rechnung.values.TaxCategory
import de.fluchitconsulting.rechnung.values.VatId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate

/**
 * Die Stornorechnung (#63, ADR-0006): Sie nennt ihre Ursprungsrechnung, ihr
 * Gesamtbetrag ist negativ, und sie fordert nicht zur Zahlung auf.
 */
class CancellationRulesTest : FunSpec({

    val config = testSellerConfig()

    // Das Gegenstück zur Ursprungsrechnung: dieselbe Position, negative Menge.
    fun storno(
        cancelled: CancelledInvoice? = testCancelled(),
        quantity: String = "-1",
    ) = testInvoice(
        type = InvoiceType.CANCELLATION,
        items = listOf(testItem(quantity = quantity, unitPrice = "1000.00")),
        cancelled = cancelled,
    )

    fun messageFor(invoice: Invoice, rule: Rule): String = InvoiceCalculator.calculate(invoice, config).messageOf(rule)

    test("ein Storno mit Bezug und negativer Menge geht durch") {
        val calculated = InvoiceCalculator.calculate(storno(), config)

        calculated.aborts().shouldBeEmpty()
        calculated.grossTotal shouldBe Money("-1190.00")
    }

    // Ob ein Storno überhaupt einen Bezug trägt, prüft InvoiceKindRulesTest.

    test("ein Storno auf die eigene Nummer wird abgelehnt") {
        val message =
            messageFor(storno(cancelled = testCancelled(invoiceNumber = "R26-001")), Rule.STORNO_AUF_SICH_SELBST)

        message shouldContain "R26-001"
        message shouldContain "eigene Nummer"
    }

    test("eine Ursprungsrechnung nach dem Rechnungsdatum wird abgelehnt") {
        // Die Rechnung des Bausatzes ist auf den 14.08.2026 datiert.
        val message =
            messageFor(
                storno(cancelled = testCancelled(issueDate = LocalDate.of(2026, 9, 1))),
                Rule.URSPRUNGSRECHNUNG_SPAETER_DATIERT,
            )

        message shouldContain "2026-09-01"
        message shouldContain "was es schon gibt"
    }

    test("ein Storno über einen positiven Betrag ist ein Widerspruch") {
        val message = messageFor(storno(quantity = "1"), Rule.STORNO_NICHT_NEGATIV)

        message shouldContain "negativem Vorzeichen"
    }

    test("ein Storno über null ebenso") {
        // "Nicht negativ" und nicht "positiv": Ein Storno über 0,00 hebt nichts
        // auf und wäre trotzdem durchgegangen, stünde hier isPositive().
        InvoiceCalculator
            .calculate(storno(quantity = "0"), config)
            .rules() shouldContain Rule.STORNO_NICHT_NEGATIV
    }

    test("die Regel gegen negative Gesamtbeträge gilt im Storno nicht") {
        InvoiceCalculator.calculate(storno(), config).rules() shouldNotContain Rule.GESAMTBETRAG_NEGATIV
    }

    test("ein Storno hat keine Fälligkeit") {
        // Es fordert nicht zur Zahlung auf, sondern kündigt eine Erstattung an.
        InvoiceCalculator.calculate(storno(), config).dueDate shouldBe null
    }

    context("was ein Storno nicht abrechnet, mahnt es auch nicht an") {
        // Beide Hinweise hängen am Rechnungsdatum und meldeten sich deshalb auch
        // auf einem Storno, das Monate nach der Ursprungsrechnung entsteht –
        // genau der Normalfall. Sie forderten damit, ein richtiges Datum zu
        // ändern und eine gegenstandslose Abfrage zu wiederholen.

        test("die Sechs-Monats-Frist des §14 Abs. 2 UStG gilt dem Ausstellen, nicht dem Aufheben") {
            // Leistung endete im Januar, der Fehler fällt im September auf.
            val spaetesStorno =
                storno().copy(
                    issueDate = LocalDate.of(2026, 9, 12),
                    servicePeriod = ServicePeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
                )

            InvoiceCalculator.calculate(spaetesStorno, config).rules() shouldNotContain Rule.RECHNUNG_SPAET
        }

        test("eine alternde Bestätigungsabfrage wird im Storno nicht gerügt") {
            // Das Storno wiederholt die Abfrage der Ursprungsrechnung; zu der
            // musste sie passen, nicht zum Storno.
            val alteAbfrage =
                storno(quantity = "-10").copy(
                    issueDate = LocalDate.of(2026, 9, 12),
                    items = listOf(testItem(quantity = "-10", vatRate = "0", taxCategory = TaxCategory.REVERSE_CHARGE)),
                    customer =
                        Customer(
                            name = "Voorbeeld Software B.V.",
                            address =
                                Address(
                                    street = "Keizersgracht 1",
                                    postalCode = "1015",
                                    city = "Amsterdam",
                                    countryCode = CountryCode("NL"),
                                ),
                            vatId = VatId("NL123456789B01"),
                            vatIdCheck =
                                VatIdCheck(
                                    date = LocalDate.of(2025, 6, 1),
                                    method = CheckMethod.QUALIFIED,
                                    reference = "BZSt-Protokoll 2025-06-01",
                                ),
                        ),
                )

            InvoiceCalculator.calculate(alteAbfrage, config).rules() shouldNotContain Rule.ABFRAGE_ALT
        }
    }
})
