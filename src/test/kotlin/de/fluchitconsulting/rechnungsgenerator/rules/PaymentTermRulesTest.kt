package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.model.testSellerConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain

class PaymentTermRulesTest : FunSpec({

    val config = testSellerConfig()

    // Ein negatives Zahlungsziel setzte die Fälligkeit vor das
    // Rechnungsdatum: "zahlbar bis 9. August" auf einer Rechnung vom
    // 14. August (#47). Die Meldung nennt, woher der Wert kommt.
    test("ein negatives Zahlungsziel in der Rechnung wird abgelehnt") {
        val message =
            CalculatedInvoice(testInvoice(paymentTermDaysOverride = -5), config)
                .messageOf(Rule.ZAHLUNGSZIEL_NEGATIV)

        message shouldContain "-5"
        message shouldContain "Rechnung"
    }

    test("ebenso eines aus den Stammdaten") {
        val message =
            CalculatedInvoice(testInvoice(), testSellerConfig(paymentTermDays = -5))
                .messageOf(Rule.ZAHLUNGSZIEL_NEGATIV)

        message shouldContain "-5"
        message shouldContain "Stammdaten"
    }
})
