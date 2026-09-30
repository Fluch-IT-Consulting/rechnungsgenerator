package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice

/** Alle Regelgruppen in der Reihenfolge ihrer Befunde, jede gegen das fertig gerechnete Ergebnis (ADR-0004). */
internal object InvoiceRules {
    /**
     * Die Befunde der Rechnungsart kommen aus dem Bau, den der Aufrufer schon hat (#93) – als Parameter und nicht als
     * Listeneintrag, der unbemerkt fehlen könnte (#105).
     */
    fun check(calculated: CalculatedInvoice, kindCheck: InvoiceKindRules.Result): List<Finding> =
        listOf(
            InvoiceNumberRules.check(calculated),
            SellerConfigRules.check(calculated),
            AddressRules.check(calculated),
            FreeTextRules.check(calculated),
            kindCheck.findings,
            CancellationRules.check(calculated),
            ItemRules.check(calculated),
            TaxCategoryRules.check(calculated),
            CurrencyRules.check(calculated),
            ServicePeriodRules.check(calculated),
            PrepaymentRules.check(calculated),
            FinalInvoiceRules.check(calculated),
            PaymentTermRules.check(calculated),
            SkontoRules.check(calculated),
            PlausibilityHints.check(calculated),
            VatIdCheckRules.check(calculated),
        ).flatten()
}
