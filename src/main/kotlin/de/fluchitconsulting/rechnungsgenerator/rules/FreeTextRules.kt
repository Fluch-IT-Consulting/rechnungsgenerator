package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice

/**
 * Die Freitextangaben der Rechnung: Ansprechpartner (#196), Kundennummer
 * (#190), die eigene Auftragsnummer (#191) und die Einleitung (#209).
 *
 * Geprüft wird nur, was jeder Freitext erfüllen muss: Steht der Schlüssel da,
 * nennt er etwas, und zwar ohne Leerraum am Rand. Ein Muster gibt es nicht:
 * Kunden- und Auftragsnummern vergibt jeder Rechnungssteller anders, und wie
 * ein Kunde seine Abteilungen nennt, weiß nur er.
 *
 * Leerraum am Rand sieht man im PDF nicht, aber er stünde später wörtlich im
 * ZUGFeRD-XML. Tragen kann ihn ohnehin nur ein gequoteter YAML-Wert oder ein
 * Block mit `|`, der auf einen Zeilenumbruch endet, und dann ist er ein
 * Versehen – dieselbe Überlegung wie bei `RECHNUNGSNUMMER_MIT_LEERRAUM`.
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

    /**
     * Ein optionaler Freitext: fehlt er, ist nichts zu prüfen. Leer ist etwas
     * anderes als fehlend – `ansprechpartner: ""` nennt niemanden, und wer das
     * schreibt, wollte vermutlich jemanden nennen.
     */
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
