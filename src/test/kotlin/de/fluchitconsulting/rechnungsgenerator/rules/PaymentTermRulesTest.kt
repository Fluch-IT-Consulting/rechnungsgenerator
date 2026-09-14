package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.InvoiceCalculator
import de.fluchitconsulting.rechnungsgenerator.Rule
import de.fluchitconsulting.rechnungsgenerator.messageOf
import de.fluchitconsulting.rechnungsgenerator.testInvoice
import de.fluchitconsulting.rechnungsgenerator.testSellerConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain

class PaymentTermRulesTest : FunSpec({

    val config = testSellerConfig()

    // Ein negatives Zahlungsziel setzte die Fälligkeit vor das
    // Rechnungsdatum: "zahlbar bis 9. August" auf einer Rechnung vom
    // 14. August (#47). Die Meldung nennt, woher der Wert kommt.
    test("ein negatives Zahlungsziel in der Rechnung wird abgelehnt") {
        val message =
            InvoiceCalculator
                .calculate(testInvoice(paymentTermDaysOverride = -5), config)
                .messageOf(Rule.ZAHLUNGSZIEL_NEGATIV)

        message shouldContain "-5"
        message shouldContain "Rechnung"
    }

    test("ebenso eines aus den Stammdaten") {
        val message =
            InvoiceCalculator
                .calculate(testInvoice(), testSellerConfig(paymentTermDays = -5))
                .messageOf(Rule.ZAHLUNGSZIEL_NEGATIV)

        message shouldContain "-5"
        message shouldContain "Stammdaten"
    }
})
