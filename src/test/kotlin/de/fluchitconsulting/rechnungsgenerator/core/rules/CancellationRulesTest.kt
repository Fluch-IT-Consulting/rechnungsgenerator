package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Address
import de.fluchitconsulting.rechnungsgenerator.core.model.CancelledInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.CheckMethod
import de.fluchitconsulting.rechnungsgenerator.core.model.Customer
import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.ServicePeriod
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.model.VatIdCheck
import de.fluchitconsulting.rechnungsgenerator.core.model.testCancelled
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testItem
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.values.CountryCode
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import de.fluchitconsulting.rechnungsgenerator.core.values.VatId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
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
    fun cancellation(
        cancelled: CancelledInvoice? = testCancelled(),
        quantity: String = "-1",
    ) = testInvoice(
        type = InvoiceType.CANCELLATION,
        items = listOf(testItem(quantity = quantity, unitPrice = "1000.00")),
        cancelled = cancelled,
    )

    fun messageFor(invoice: Invoice, rule: Rule): String = CalculatedInvoice(invoice, config).messageOf(rule)

    test("ein Storno mit Bezug und negativer Menge geht durch") {
        val calculated = CalculatedInvoice(cancellation(), config)

        calculated.aborts().shouldBeEmpty()
        calculated.grossTotal shouldBe Money("-1190.00")
    }

    // Ob ein Storno überhaupt einen Bezug trägt, prüft InvoiceKindRulesTest.

    test("ein Storno auf die eigene Nummer wird abgelehnt") {
        val message =
            messageFor(cancellation(cancelled = testCancelled(invoiceNumber = "R26-001")), Rule.STORNO_AUF_SICH_SELBST)

        message shouldContain "R26-001"
        message shouldContain "eigene Nummer"
    }

    test("eine Ursprungsrechnung nach dem Rechnungsdatum wird abgelehnt") {
        // Die Rechnung des Bausatzes ist auf den 14.08.2026 datiert.
        val message =
            messageFor(
                cancellation(cancelled = testCancelled(issueDate = LocalDate.of(2026, 9, 1))),
                Rule.URSPRUNGSRECHNUNG_SPAETER_DATIERT,
            )

        message shouldContain "2026-09-01"
        message shouldContain "was es schon gibt"
    }

    test("auf einer anderen Rechnungsart schweigt die Prüfung des Bezugs") {
        // Dass 'storniert' dort nicht hingehört, meldet InvoiceKindRules. Die
        // Meldungen über den Bezug sprächen von "der Stornorechnung" und "diesem
        // Storno", auf einem Blatt, das keines ist (#97). Der Bezug hier ist
        // doppelt falsch: eigene Nummer und später datiert.
        val absent = CalculatedInvoice(testInvoice(), config)
        val present =
            CalculatedInvoice(
                testInvoice(cancelled = testCancelled(invoiceNumber = "R26-001", issueDate = LocalDate.of(2026, 9, 1))),
                config,
            )

        present.rules() shouldContainExactlyInAnyOrder absent.rules() + Rule.BEZUG_OHNE_STORNO
    }

    test("ein Storno über einen positiven Betrag ist ein Widerspruch") {
        val message = messageFor(cancellation(quantity = "1"), Rule.STORNO_NICHT_NEGATIV)

        message shouldContain "negativem Vorzeichen"
    }

    test("ein Storno über null ebenso") {
        // "Nicht negativ" und nicht "positiv": Ein Storno über 0,00 hebt nichts
        // auf und wäre trotzdem durchgegangen, stünde hier isPositive().
        CalculatedInvoice(cancellation(quantity = "0"), config)
            .rules() shouldContain Rule.STORNO_NICHT_NEGATIV
    }

    test("die Regel gegen negative Gesamtbeträge gilt im Storno nicht") {
        CalculatedInvoice(cancellation(), config).rules() shouldNotContain Rule.GESAMTBETRAG_NEGATIV
    }

    context("ABSCHLAGSRECHNUNG_OHNE_BETRAG (#214)") {
        fun abschlag(
            unitPrice: String,
            quantity: String = "1",
        ) = testInvoice(type = InvoiceType.PREPAYMENT, items = listOf(testItem(quantity = quantity, unitPrice = unitPrice)))

        test("eine Abschlagsrechnung über null wird abgelehnt") {
            val message = messageFor(abschlag("0.00"), Rule.ABSCHLAGSRECHNUNG_OHNE_BETRAG)

            message shouldContain "fordert nichts"
        }

        test("eine Abschlagsrechnung über einen positiven Betrag bleibt zulässig") {
            CalculatedInvoice(abschlag("100.00"), config).aborts().shouldBeEmpty()
        }

        test("eine negative meldet nur das Vorzeichen") {
            // Negativ ist auch nicht null; eine zweite Meldung über denselben
            // Betrag sagte nichts Neues.
            val rules = CalculatedInvoice(abschlag("100.00", quantity = "-1"), config).rules()

            rules shouldContain Rule.GESAMTBETRAG_NEGATIV
            rules shouldNotContain Rule.ABSCHLAGSRECHNUNG_OHNE_BETRAG
        }

        test("eine gewöhnliche Rechnung über null bricht nicht ab") {
            CalculatedInvoice(testInvoice(items = listOf(testItem(unitPrice = "0.00"))), config)
                .aborts()
                .shouldBeEmpty()
        }
    }

    test("ein Storno hat keine Fälligkeit") {
        // Es fordert nicht zur Zahlung auf, sondern kündigt eine Erstattung an.
        CalculatedInvoice(cancellation(), config).dueDate shouldBe null
    }

    context("was ein Storno nicht abrechnet, mahnt es auch nicht an") {
        // Beide Hinweise hängen am Rechnungsdatum und meldeten sich deshalb auch
        // auf einem Storno, das Monate nach der Ursprungsrechnung entsteht –
        // genau der Normalfall. Sie forderten damit, ein richtiges Datum zu
        // ändern und eine gegenstandslose Abfrage zu wiederholen.

        test("die Sechs-Monats-Frist des §14 Abs. 2 UStG gilt dem Ausstellen, nicht dem Aufheben") {
            // Leistung endete im Januar, der Fehler fällt im September auf.
            val lateCancellation =
                cancellation().copy(
                    issueDate = LocalDate.of(2026, 9, 12),
                    servicePeriod = ServicePeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
                )

            CalculatedInvoice(lateCancellation, config).rules() shouldNotContain Rule.RECHNUNG_SPAET
        }

        test("eine alternde Bestätigungsabfrage wird im Storno nicht gerügt") {
            // Das Storno wiederholt die Abfrage der Ursprungsrechnung; zu der
            // musste sie passen, nicht zum Storno.
            val oldCheck =
                cancellation(quantity = "-10").copy(
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

            CalculatedInvoice(oldCheck, config).rules() shouldNotContain Rule.ABFRAGE_ALT
        }
    }
})
