package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.ServicePeriod
import de.fluchitconsulting.rechnungsgenerator.core.values.Money

/** Leerzeichen und Umbruch halten nur das erzeugte .tex lesbar. */
internal fun rowSeparator(macro: TemplateMacro): Latex = Latex.raw(" ") + Latex.macro(macro) + Latex.raw("\n")

/** Hält eine Angabe auf einer Zeile zusammen. */
internal fun together(value: Latex): Latex = Latex.macro(TemplateMacro.ZUSAMMEN, listOf(value))

/** Die letzte Zeile eines Betragsblocks. Das Zeilenende davor setzt das Makro selbst. */
internal fun summaryRow(label: String, amount: Money): Latex =
    Latex.raw("\n  ") +
        Latex.macro(
            TemplateMacro.ABSCHLUSSZEILE,
            listOf(label.escapeLatex(), Numbers.amount(amount)),
        )

internal fun servicePeriod(period: ServicePeriod, invoice: Invoice): Latex {
    val format = DateFormat(invoice.language)
    return if (invoice.type.servicePeriodIsForecast) {
        format.monthPeriod(period.start, period.end)
    } else {
        format.servicePeriod(period.start, period.end)
    }
}
