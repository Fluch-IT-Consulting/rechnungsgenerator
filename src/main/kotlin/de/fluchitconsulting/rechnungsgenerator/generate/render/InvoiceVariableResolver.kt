package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.model.CancelledInvoice
import de.fluchitconsulting.rechnungsgenerator.rules.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.rules.InvoiceKind

/**
 * Die Werte einer angenommenen Rechnung, die ein Text mit Platzhaltern
 * `<<NAME>>` nennen kann – für jeden Text, der zu dieser Rechnung gehört.
 *
 * Außen steht der Name, nicht die Rechnungsart: So liest sich das `when` als
 * Liste aller Variablen, und wer eine neue einträgt, sieht die übrigen daneben.
 * Zu welcher Art ein Wert gehört, klärt der Zugriff darunter – und wirft, wenn
 * die Rechnung nicht diese Art ist.
 *
 * Gefragt wird [InvoiceKind] und nicht das flache Modell: Dort ist der Bezug
 * optional, hier ist er es auf einer Stornorechnung nicht (ADR-0009).
 *
 * Jeder Wert steht in \zusammen: Mitten im Satz dürfte "15. Juli 2026" sonst
 * hinter dem Tag umbrechen und eine Nummer wie "R26-042" am Bindestrich.
 */
internal class InvoiceVariableResolver(private val accepted: AcceptedInvoice) : VariableResolver {
    override fun resolve(variable: String): Latex =
        when (variable) {
            "URSPRUNGSNUMMER" -> together(cancelled(variable).invoiceNumber.escapeLatex())
            "URSPRUNGSDATUM" -> together(GermanFormat.date(cancelled(variable).issueDate))
            else -> error("Unbekannte Variable <<$variable>>.")
        }

    // Ein `when` ohne `else` über alle Arten: Eine neue Art muss hier entscheiden,
    // ob sie eine Ursprungsrechnung hat – etwa eine Korrekturrechnung mit Bezug.
    private fun cancelled(variable: String): CancelledInvoice =
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

    private fun together(value: Latex): Latex = Latex.macro(TemplateMacro.ZUSAMMEN, listOf(value))
}
