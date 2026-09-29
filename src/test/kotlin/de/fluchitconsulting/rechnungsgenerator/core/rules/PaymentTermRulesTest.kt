package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain

class PaymentTermRulesTest : FunSpec({

    val config = testSellerConfig()

    // #47
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
