package de.fluchitconsulting.rechnungsgenerator.core.accepted

import de.fluchitconsulting.rechnungsgenerator.core.model.CancelledInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceVariable
import de.fluchitconsulting.rechnungsgenerator.core.rules.InvoiceKind
import de.fluchitconsulting.rechnungsgenerator.core.text.TextValue
import de.fluchitconsulting.rechnungsgenerator.core.text.VariableResolver

/**
 * Woher ein Wert kommt, nicht wie er aussieht (ADR-0019). Außen die Variable, nicht die Art, damit sich das `when`
 * als Liste aller Variablen liest. Gefragt wird [InvoiceKind], wo der Bezug nicht mehr optional ist.
 */
internal class InvoiceVariableResolver(private val accepted: AcceptedInvoice) : VariableResolver<InvoiceVariable> {
    override fun resolve(variable: InvoiceVariable): TextValue =
        when (variable) {
            InvoiceVariable.URSPRUNGSNUMMER -> TextValue.Text(cancelled(variable).invoiceNumber)
            InvoiceVariable.URSPRUNGSDATUM -> TextValue.Date(cancelled(variable).issueDate)
        }

    private fun cancelled(variable: InvoiceVariable): CancelledInvoice =
        when (val kind = accepted.kind) {
            is InvoiceKind.Cancellation -> {
                kind.cancelled
            }
            is InvoiceKind.Ordinary, is InvoiceKind.PrepaymentInvoice, is InvoiceKind.FinalInvoice -> {
                error(
                    "<<$variable>> gibt es nur auf einer Stornorechnung, " +
                        "nicht auf einer ${accepted.calculated.invoice.type.documentName}.",
                )
            }
        }
}
