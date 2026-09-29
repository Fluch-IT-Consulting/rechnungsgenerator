package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType

/** Was am Zeitpunkt der Vereinnahmung (BT-7) falsch sein kann, nur auf der Abschlagsrechnung. */
internal object PrepaymentRules {
    fun check(calculated: CalculatedInvoice): List<Finding> =
        buildList {
            val invoice = calculated.invoice
            when (invoice.type) {
                InvoiceType.PREPAYMENT -> {}
                InvoiceType.INVOICE, InvoiceType.FINAL, InvoiceType.PARTIAL, InvoiceType.CANCELLATION -> {
                    return@buildList
                }
            }
            val vereinnahmt = invoice.receivedOn ?: return@buildList

            // Was nach dem Rechnungsdatum liegt, belegt für diese Rechnung nichts.
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
