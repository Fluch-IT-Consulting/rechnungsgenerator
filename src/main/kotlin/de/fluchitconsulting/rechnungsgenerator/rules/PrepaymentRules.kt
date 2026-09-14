package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.Finding
import de.fluchitconsulting.rechnungsgenerator.Rule
import de.fluchitconsulting.rechnungsgenerator.Subject
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType

/**
 * Die Abschlagsrechnung selbst: der Zeitpunkt der Vereinnahmung (BT-7).
 *
 * Dass er nur auf einer Abschlagsrechnung stehen darf, prüft [InvoiceKindRules]
 * (ADR-0008). Hier steht, was am Datum selbst falsch sein kann – und nur auf
 * einer Abschlagsrechnung (#97).
 *
 * Was eine Schlussrechnung mit den Abschlägen tut, prüft [FinalInvoiceRules].
 */
internal object PrepaymentRules {
    fun check(calculated: CalculatedInvoice): List<Finding> =
        buildList {
            val invoice = calculated.invoice
            // Auf einer anderen Art meldet InvoiceKindRules schon, dass das Feld
            // dort nicht hingehört. Eine Meldung über das Datum spräche von einer
            // Vereinnahmung, die es auf diesem Dokument nicht geben kann –
            // unterdrückt durch frühes Weiterspringen wie in ADR-0004.
            if (invoice.type != InvoiceType.PREPAYMENT) return@buildList
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
