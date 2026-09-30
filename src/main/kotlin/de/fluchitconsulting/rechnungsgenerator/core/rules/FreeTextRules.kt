package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice

/**
 * Steht ein Freitext da, trägt er keinen Leerraum am Rand: Den sähe man im PDF nicht, aber im XML. Ein leerer Wert
 * bricht schon beim Lesen ab (#504). Ein Muster gibt es nicht, solche Nummern vergibt jeder anders.
 */
internal object FreeTextRules {
    fun check(calculated: CalculatedInvoice): List<Finding> {
        val invoice = calculated.invoice
        val customer = invoice.customer
        return listOfNotNull(
            padded(customer.contactPoint, "Der Ansprechpartner", Rule.ANSPRECHPARTNER_MIT_LEERRAUM),
            padded(customer.buyerIdentifier, "Die Kundennummer", Rule.KUNDENNUMMER_MIT_LEERRAUM),
            padded(invoice.salesOrderReference, "Die Auftragsnummer", Rule.AUFTRAGSNUMMER_MIT_LEERRAUM),
            padded(invoice.introduction, "Die Einleitung", Rule.EINLEITUNG_MIT_LEERRAUM),
        )
    }

    private fun padded(
        value: String?,
        designation: String,
        rule: Rule,
    ): Finding? {
        if (value == null || value == value.trim()) return null
        return Finding(
            rule,
            Subject.WholeInvoice,
            "$designation '$value' beginnt oder endet mit Leerraum. Tragen kann ihn nur ein " +
                "gequoteter YAML-Wert oder ein Block mit '|', der auf einen Zeilenumbruch endet " +
                "('>-' tut das nicht); gemeint war vermutlich '${value.trim()}'.",
        )
    }
}
