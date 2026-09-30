package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.string.shouldContain

class SellerConfigRulesTest : FunSpec({

    fun withRegister(court: String?, number: String?) =
        CalculatedInvoice(testInvoice(), testSellerConfig().copy(registerCourt = court, registerNumber = number))

    context("Registergericht und Handelsregisternummer (#505)") {
        test("beide oder keins geht durch") {
            withRegister("Würzburg", "HRB 12345").aborts().shouldBeEmpty()
            withRegister(null, null).aborts().shouldBeEmpty()
        }

        test("eine Nummer ohne Gericht wird abgelehnt und nennt, was fehlt") {
            val message = withRegister(null, "HRB 12345").messageOf(Rule.REGISTERANGABEN_UNVOLLSTAENDIG)

            message shouldContain "'HRB 12345'"
            message shouldContain "'registergericht' ergänzen"
        }

        test("ein Gericht ohne Nummer wird abgelehnt und nennt, was fehlt") {
            val message = withRegister("Würzburg", null).messageOf(Rule.REGISTERANGABEN_UNVOLLSTAENDIG)

            message shouldContain "'Würzburg'"
            message shouldContain "'handelsregister-nummer' ergänzen"
        }
    }
})
