package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice

/**
 * Steht ein Freitext da, nennt er etwas, ohne Leerraum am Rand: Den sähe man im PDF nicht, aber im XML. Ein
 * Muster gibt es nicht, solche Nummern vergibt jeder anders.
 */
internal object FreeTextRules {
    fun check(calculated: CalculatedInvoice): List<Finding> {
        val invoice = calculated.invoice
        val customer = invoice.customer
        return listOf(
            freeText(
                customer.contactPoint,
                key = "kunde: ansprechpartner",
                designation = "Der Ansprechpartner",
                blank = Rule.ANSPRECHPARTNER_LEER,
                padded = Rule.ANSPRECHPARTNER_MIT_LEERRAUM,
            ),
            freeText(
                customer.buyerIdentifier,
                key = "kunde: kundennummer",
                designation = "Die Kundennummer",
                blank = Rule.KUNDENNUMMER_LEER,
                padded = Rule.KUNDENNUMMER_MIT_LEERRAUM,
            ),
            freeText(
                invoice.salesOrderReference,
                key = "auftragsnummer",
                designation = "Die Auftragsnummer",
                blank = Rule.AUFTRAGSNUMMER_LEER,
                padded = Rule.AUFTRAGSNUMMER_MIT_LEERRAUM,
            ),
            freeText(
                invoice.introduction,
                key = "einleitung",
                designation = "Die Einleitung",
                blank = Rule.EINLEITUNG_LEER,
                padded = Rule.EINLEITUNG_MIT_LEERRAUM,
            ),
        ).flatten()
    }

    /** Leer ist nicht fehlend: `ansprechpartner: ""` wollte vermutlich jemanden nennen. */
    private fun freeText(
        value: String?,
        key: String,
        designation: String,
        blank: Rule,
        padded: Rule,
    ): List<Finding> {
        if (value == null) return emptyList()
        if (value.isBlank()) {
            return listOf(
                Finding(
                    blank,
                    Subject.WholeInvoice,
                    "$designation ist leer – '$key' nennt nichts. Ist keiner gemeint, entfällt der Schlüssel ganz.",
                ),
            )
        }
        if (value != value.trim()) {
            return listOf(
                Finding(
                    padded,
                    Subject.WholeInvoice,
                    "$designation '$value' beginnt oder endet mit Leerraum. Tragen kann ihn nur ein " +
                        "gequoteter YAML-Wert oder ein Block mit '|', der auf einen Zeilenumbruch endet " +
                        "('>-' tut das nicht); gemeint war vermutlich '${value.trim()}'.",
                ),
            )
        }
        return emptyList()
    }
}
