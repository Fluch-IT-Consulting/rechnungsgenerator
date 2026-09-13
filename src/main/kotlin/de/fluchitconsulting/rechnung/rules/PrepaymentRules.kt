package de.fluchitconsulting.rechnung.rules

import de.fluchitconsulting.rechnung.CalculatedInvoice
import de.fluchitconsulting.rechnung.Finding
import de.fluchitconsulting.rechnung.Rule
import de.fluchitconsulting.rechnung.Subject

/**
 * Die Abschlagsrechnung selbst: der Zeitpunkt der Vereinnahmung (BT-7).
 *
 * Dass er nur auf einer Abschlagsrechnung stehen darf, prüft [InvoiceKindRules]
 * (ADR-0008). Hier steht, was am Datum selbst falsch sein kann.
 *
 * Was eine Schlussrechnung mit den Abschlägen tut, prüft [FinalInvoiceRules].
 */
internal object PrepaymentRules {
    fun check(calculated: CalculatedInvoice): List<Finding> =
        buildList {
            val invoice = calculated.invoice
            val vereinnahmt = invoice.receivedOn ?: return@buildList

            // Gleiche Prüfung wie beim Datum der Bestätigungsabfrage: Was nach dem
            // Rechnungsdatum liegt, belegt für diese Rechnung nichts. Steht der
            // Zeitpunkt noch nicht fest, bleibt die Angabe weg.
            if (vereinnahmt.isAfter(invoice.issueDate)) {
                add(
                    Finding(
                        Rule.VEREINNAHMUNG_NACH_RECHNUNGSDATUM,
                        Subject.WholeInvoice,
                        "Die Vereinnahmung am $vereinnahmt liegt nach dem Rechnungsdatum ${invoice.issueDate}. " +
                            "Steht der Zeitpunkt noch nicht fest, bitte 'vereinnahmt-am' weglassen.",
                    ),
                )
            }
        }
}
