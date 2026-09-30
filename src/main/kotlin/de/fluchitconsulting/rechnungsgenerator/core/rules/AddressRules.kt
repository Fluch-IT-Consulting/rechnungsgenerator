package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice

/** Die Anschrift des Kunden. */
internal object AddressRules {
    fun check(calculated: CalculatedInvoice): List<Finding> = listOfNotNull(postalCode(calculated))

    /**
     * Nur im Inland: Dort hat der Rechnungssteller selbst eine Postleitzahl, es gibt also welche, und §14 Abs. 4 Nr. 1
     * UStG verlangt die vollständige Anschrift. Ob ein anderes Land welche kennt, weiß das Programm nicht (#512).
     */
    private fun postalCode(calculated: CalculatedInvoice): Finding? {
        val address = calculated.invoice.customer.address
        if (address.postalCode != null || !calculated.isDomestic) return null
        return Finding(
            Rule.POSTLEITZAHL_FEHLT,
            Subject.WholeInvoice,
            "Die Anschrift des Kunden in ${address.countryCode} nennt keine Postleitzahl ('kunde: anschrift: plz'). " +
                "Weglassen lässt sie sich nur für einen Kunden in einem anderen Land als dem des Rechnungsstellers; " +
                "§14 Abs. 4 Nr. 1 UStG verlangt die vollständige Anschrift.",
        )
    }
}
