package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.SellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.values.CountryCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.string.shouldContain

/** Die Anschrift des Kunden (#512). */
class AddressRulesTest : FunSpec({

    fun customerIn(
        country: String,
        postalCode: String?,
        seller: SellerConfig = testSellerConfig(),
    ): CalculatedInvoice {
        val basis = testInvoice()
        val address = basis.customer.address.copy(postalCode = postalCode, countryCode = CountryCode(country))
        return CalculatedInvoice(basis.copy(customer = basis.customer.copy(address = address)), seller)
    }

    test("mit Postleitzahl gibt es nichts zu prüfen") {
        customerIn("DE", "90402").aborts().shouldBeEmpty()
    }

    test("im Inland ohne Postleitzahl wird abgelehnt") {
        customerIn("DE", null).messageOf(Rule.POSTLEITZAHL_FEHLT) shouldContain "'kunde: anschrift: plz'"
    }

    test("Inland ist das Land des Rechnungsstellers, nicht Deutschland") {
        val inAustria = testSellerConfig().let { it.copy(address = it.address.copy(countryCode = CountryCode("AT"))) }

        customerIn("AT", null, inAustria).rules() shouldContain Rule.POSTLEITZAHL_FEHLT
        customerIn("DE", null, inAustria).rules() shouldNotContain Rule.POSTLEITZAHL_FEHLT
    }

    test("im Ausland prüft niemand, ob es dort Postleitzahlen gibt") {
        customerIn("AE", null).rules() shouldNotContain Rule.POSTLEITZAHL_FEHLT
    }
})
