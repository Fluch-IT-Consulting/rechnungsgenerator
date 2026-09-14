package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.model.LineItem
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType

/**
 * Positionstabelle, Summenblock und die Hinweise darunter: Steuerbefreiungen
 * und der Pflichtsatz der Rechnungsart. Artabhängig sind hier nur
 * Beschriftungen und der Pflichtsatz, beides über [InvoiceType] – ein `when`
 * über die Rechnungsart gibt es in diesen Blöcken nicht.
 */
internal object ItemBlocks {
    // Die Zeilen werden mit \positionstrenner VERBUNDEN, nicht damit
    // abgeschlossen: Ein Umbruch hinter der letzten Zeile erzeugt in LaTeX eine
    // leere Extrazeile.
    fun lineItems(calculatedInvoice: CalculatedInvoice): Latex =
        calculatedInvoice.lines
            .map { line ->
                val item = line.item
                Latex.macro(
                    TemplateMacro.POSITIONSZEILE,
                    listOf(
                        describeItem(item, calculatedInvoice.invoice.type),
                        GermanFormat.quantity(item.quantity),
                        item.unit.label.escapeLatex(),
                        GermanFormat.priceWithCurrency(item.unitPrice),
                        GermanFormat.amountWithCurrency(line.net),
                    ),
                )
            }.joinToLatex(rowSeparator(TemplateMacro.POSITIONSTRENNER))

    /**
     * Die Beschreibungsspalte einer Position: Bezeichnung, darunter – falls
     * vorhanden – die Zusatzangaben klein und gedämpft. Das ergibt die
     * zweizeilige Position, die die bisherigen Rechnungen hatten, nur dass die
     * zweite Zeile hier aus benannten Feldern entsteht statt aus einem
     * Textzusatz.
     *
     * \newline statt \\ für den Umbruch: Die Spalte ist eine X-Spalte, also
     * ein Absatz. Ein \\ darin würde die Tabellenzeile beenden statt die
     * Textzeile.
     */
    private fun describeItem(item: LineItem, type: InvoiceType): Latex {
        val zusatz =
            buildList {
                item.description?.let { add(it.escapeLatex()) }
                item.note?.let { add(it.escapeLatex()) }
                // Auch hier die Beschriftung der Rechnungsart: Auf einer
                // Abschlagsrechnung ist der Zeitraum einer Position genauso eine
                // Prognose wie der Zeitraum der ganzen Rechnung.
                item.servicePeriod?.let { add("${type.itemPeriodLabel}: ".escapeLatex() + servicePeriod(it, type)) }
            }
        val bezeichnung = item.name.escapeLatex()
        if (zusatz.isEmpty()) return bezeichnung
        return bezeichnung + Latex.macro(TemplateMacro.POSITIONSDETAIL, listOf(zusatz.joinToLatex(" · ")))
    }

    /**
     * Der Summenblock. Gruppen ohne Steuerbetrag erscheinen hier NICHT als
     * "zzgl. 0 % USt. 0,00 €" – das sähe aus wie ein Rechenfehler oder eine
     * vergessene Zeile. Warum keine Steuer anfällt, sagt [taxNotes] darunter.
     */
    fun totalsBlock(calculatedInvoice: CalculatedInvoice): Latex =
        buildList {
            add("Nettobetrag".escapeLatex() to GermanFormat.amountWithCurrency(calculatedInvoice.netTotal))
            calculatedInvoice.vatGroups.filterNot { it.amount.isZero() }.forEach { group ->
                // Das Prozentzeichen ist maskiert, bevor es hier ankommt –
                // GermanFormat.percent liefert ein fertiges Fragment.
                val label = "zzgl. ".escapeLatex() + GermanFormat.percent(group.rate) + " USt.".escapeLatex()
                add(label to GermanFormat.amountWithCurrency(group.amount))
            }
        }.map { (label, value) -> Latex.raw("  ") + Latex.macro(TemplateMacro.SUMMENZEILE, listOf(label, value)) }
            .joinToLatex(rowSeparator(TemplateMacro.SUMMENTRENNER)) + summaryRow("Gesamtbetrag", calculatedInvoice.grossTotal)

    /**
     * Der Pflichtsatz der Rechnungsart, unter den Summen. Ob eine Art einen hat
     * und wie er lautet, trägt [InvoiceType.mandatoryNote], den Abstand die
     * Vorlage: \hinweisabsatz bringt ihn mit, sodass bei den übrigen Arten der
     * leere Platzhalter auch wirklich nichts einnimmt.
     */
    fun typeNote(invoice: Invoice): Latex =
        invoice.type.mandatoryNote
            ?.let { Latex.macro(TemplateMacro.HINWEISABSATZ, listOf(it.escapeLatex())) }
            ?: Latex.EMPTY

    /**
     * Die Begründungen für Positionen ohne Umsatzsteuer – EN 16931 BT-120.
     *
     * Bei Reverse Charge ist das keine Höflichkeit: §14a Abs. 5 UStG verlangt
     * den Hinweis auf die Steuerschuldnerschaft des Leistungsempfängers
     * ausdrücklich auf der Rechnung.
     */
    fun taxNotes(calculatedInvoice: CalculatedInvoice): Latex =
        calculatedInvoice.vatGroups
            .filter { it.amount.isZero() && it.exemptionReason != null }
            .map { group ->
                "Auf ".escapeLatex() + GermanFormat.amountWithCurrency(group.base) +
                    " entfällt keine Umsatzsteuer: ".escapeLatex() + group.exemptionReason!!.escapeLatex() +
                    ".".escapeLatex()
            }.joinToLatex(BR)
}
