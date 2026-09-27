package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.ServicePeriod
import de.fluchitconsulting.rechnungsgenerator.core.values.Money

// Kleine Satzbausteine, die mehrere Blöcke teilen – Positionen und Summen
// brauchen dieselben Trenner, Summen und Absetzung dieselbe Abschlusszeile.
// Top-Level wie escapeLatex und joinToLatex: Ein Objekt drumherum trüge nur
// einen Namen, nach dem niemand sucht.

/**
 * Ein Trenner zwischen Tabellenzeilen, mit Leerzeichen davor und
 * Zeilenumbruch danach – beides nur, damit das erzeugte .tex lesbar bleibt.
 */
internal fun rowSeparator(macro: TemplateMacro): Latex = Latex.raw(" ") + Latex.macro(macro) + Latex.raw("\n")

/**
 * Hält eine Angabe auf einer Zeile zusammen – ein Datum, einen Prozentsatz.
 */
internal fun together(value: Latex): Latex = Latex.macro(TemplateMacro.ZUSAMMEN, listOf(value))

/**
 * Die fette Zeile unter dem Strich, mit der ein Betragsblock endet – der
 * Gesamtbetrag der Summen, der Restbetrag der Absetzung.
 *
 * Sie steht immer hinter anderen Zeilen: Das Zeilenende davor setzt das Makro
 * selbst. Der Umbruch hier hält nur das erzeugte .tex lesbar.
 */
internal fun summaryRow(label: String, amount: Money): Latex =
    Latex.raw("\n  ") +
        Latex.macro(
            TemplateMacro.ABSCHLUSSZEILE,
            listOf(label.escapeLatex(), Numbers.amount(amount)),
        )

/**
 * Ein Zeitraum der Rechnung oder einer ihrer Positionen, geschrieben wie es
 * ihre Art verlangt – die Prognose einer Abschlagsrechnung in Monaten, der
 * tatsächliche Zeitraum auf den Tag – und in ihrer Sprache.
 */
internal fun servicePeriod(period: ServicePeriod, invoice: Invoice): Latex {
    val format = DateFormat(invoice.language)
    return if (invoice.type.servicePeriodIsForecast) {
        format.monthPeriod(period.start, period.end)
    } else {
        format.servicePeriod(period.start, period.end)
    }
}
