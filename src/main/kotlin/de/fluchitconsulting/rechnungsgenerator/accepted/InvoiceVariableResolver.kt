package de.fluchitconsulting.rechnungsgenerator.accepted

import de.fluchitconsulting.rechnungsgenerator.model.CancelledInvoice
import de.fluchitconsulting.rechnungsgenerator.rules.InvoiceKind
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceVariable
import de.fluchitconsulting.rechnungsgenerator.values.TextValue
import de.fluchitconsulting.rechnungsgenerator.values.VariableResolver

/**
 * Die Werte einer angenommenen Rechnung, die ein Text mit Platzhaltern
 * `<<NAME>>` nennen kann – für jeden Text, der zu dieser Rechnung gehört.
 *
 * Er sagt, woher ein Wert kommt, und nicht, wie er aussieht: Ein Datum geht
 * als Datum hinaus, gesetzt wird es erst im Format. Deshalb liegt er hier bei
 * der [AcceptedInvoice] und nicht beim LaTeX-Satz; ein anderes Ausgabeformat
 * fragt ihn genauso.
 *
 * Außen steht die Variable, nicht die Rechnungsart: So liest sich das `when`
 * als Liste aller Variablen, und wer eine neue einträgt, sieht die übrigen
 * daneben. Zu welcher Art ein Wert gehört, klärt der Zugriff darunter – und
 * wirft, wenn die Rechnung nicht diese Art ist.
 *
 * Gefragt wird [InvoiceKind] und nicht das flache Modell: Dort ist der Bezug
 * optional, hier ist er es auf einer Stornorechnung nicht (ADR-0009).
 */
internal class InvoiceVariableResolver(private val accepted: AcceptedInvoice) : VariableResolver<InvoiceVariable> {
    override fun resolve(variable: InvoiceVariable): TextValue =
        when (variable) {
            InvoiceVariable.URSPRUNGSNUMMER -> TextValue.Text(cancelled(variable).invoiceNumber)
            InvoiceVariable.URSPRUNGSDATUM -> TextValue.Date(cancelled(variable).issueDate)
        }

    // Ein `when` ohne `else` über alle Arten: Eine neue Art muss hier entscheiden,
    // ob sie eine Ursprungsrechnung hat – etwa eine Korrekturrechnung mit Bezug.
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
