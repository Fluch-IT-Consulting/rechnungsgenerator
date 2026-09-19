package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice

/**
 * Die Freitextangaben zum Kunden (#196).
 *
 * Geprüft wird nur, was jeder Freitext erfüllen muss: Steht der Schlüssel da,
 * nennt er etwas, und zwar ohne Leerraum am Rand. Ein Muster gibt es nicht –
 * wie ein Kunde seine Abteilungen nennt, weiß nur er.
 *
 * Leerraum am Rand sieht man im PDF nicht, aber er stünde später wörtlich im
 * ZUGFeRD-XML. Tragen kann ihn ohnehin nur ein gequoteter YAML-Wert, und dann
 * ist er ein Versehen – dieselbe Überlegung wie bei
 * `RECHNUNGSNUMMER_MIT_LEERRAUM`.
 */
internal object CustomerRules {
    fun check(calculated: CalculatedInvoice): List<Finding> =
        freeText(
            calculated.invoice.customer.contactPoint,
            key = "kunde: ansprechpartner",
            designation = "Der Ansprechpartner",
            blank = Rule.ANSPRECHPARTNER_LEER,
            padded = Rule.ANSPRECHPARTNER_MIT_LEERRAUM,
        )

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
                    "$designation '$value' beginnt oder endet mit Leerraum. Nur ein gequoteter YAML-Wert " +
                        "kann ihn tragen; gemeint war vermutlich '${value.trim()}'.",
                ),
            )
        }
        return emptyList()
    }
}
