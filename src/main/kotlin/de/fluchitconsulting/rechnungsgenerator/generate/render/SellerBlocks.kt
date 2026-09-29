package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice

/** Der einzige Block aus den Stammdaten, über dessen Erscheinen der Renderer entscheidet. */
internal object SellerBlocks {
    /**
     * Nur, wenn das Dokument Geld fordert ([CalculatedInvoice.requestsPayment]); die IBAN im Fuß bleibt. Überschrift
     * und Inhalt stecken im Makro, damit keine Überschrift ohne Inhalt stehen bleibt.
     */
    fun bankDetails(calculatedInvoice: CalculatedInvoice): Latex {
        if (!calculatedInvoice.requestsPayment) return Latex.EMPTY
        return Latex.macro(TemplateMacro.BANKBLOCK)
    }
}
