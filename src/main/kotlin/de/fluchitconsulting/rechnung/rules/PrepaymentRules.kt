package de.fluchitconsulting.rechnung.rules

import de.fluchitconsulting.rechnung.CalculatedInvoice
import de.fluchitconsulting.rechnung.Finding
import de.fluchitconsulting.rechnung.Rule
import de.fluchitconsulting.rechnung.Subject
import de.fluchitconsulting.rechnung.values.InvoiceType

/**
 * Die Abschlagsrechnung selbst: der Zeitpunkt der Vereinnahmung (BT-7).
 *
 * Er gehört auf die Abschlagsrechnung – und nur dorthin. Auf einer Rechnung
 * über eine erbrachte Leistung ist die Umsatzsteuer mit der Leistung
 * entstanden, nicht mit dem Geldeingang; die Angabe wäre dort keine
 * Zusatzinformation, sondern eine falsche.
 *
 * Was eine Schlussrechnung mit den Abschlägen tut, prüft [FinalInvoiceRules].
 */
internal object PrepaymentRules {
    fun check(calculated: CalculatedInvoice): List<Finding> =
        buildList {
            val invoice = calculated.invoice
            val vereinnahmt = invoice.receivedOn ?: return@buildList

            if (invoice.type != InvoiceType.PREPAYMENT) {
                add(
                    Finding(
                        Rule.VEREINNAHMUNG_FALSCHE_ART,
                        Subject.WholeInvoice,
                        "'vereinnahmt-am' gehört nur auf eine Abschlagsrechnung (art: abschlagsrechnung); " +
                            "dieses Dokument ist eine ${invoice.type.documentName}.",
                    ),
                )
            }
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
