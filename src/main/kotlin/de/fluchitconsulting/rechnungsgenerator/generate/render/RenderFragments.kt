package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.model.ServicePeriod
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.values.Money

// Kleine Satzbausteine, die mehrere Blöcke teilen – Positionen und Summen
// brauchen dieselben Trenner, Summen und Absetzung dieselbe Abschlusszeile.
// Top-Level wie escapeLatex und joinToLatex: Ein Objekt drumherum trüge nur
// einen Namen, nach dem niemand sucht.

// Zeilenumbruch in LaTeX. Als Konstante, weil "\\\\" im Kotlin-Quelltext
// schwer zu lesen ist: vier Backslashes für zwei im Ergebnis.
internal const val BR = "\\\\"

/**
 * Ein Trenner zwischen Tabellenzeilen, mit Leerzeichen davor und
 * Zeilenumbruch danach – beides nur, damit das erzeugte .tex lesbar bleibt.
 */
internal fun rowSeparator(macro: TemplateMacro): Latex = Latex.raw(" ") + Latex.macro(macro) + Latex.raw("\n")

/**
 * Die fette Zeile unter dem Strich, mit der ein Betragsblock endet – der
 * Gesamtbetrag der Summen, der Restbetrag der Absetzung.
 */
internal fun summaryRow(label: String, amount: Money): Latex =
    Latex.raw(" $BR\n  ") +
        Latex.macro(
            TemplateMacro.ABSCHLUSSZEILE,
            listOf(label.escapeLatex(), GermanFormat.amountWithCurrency(amount)),
        )

/**
 * Ein Zeitraum, geschrieben wie es die Rechnungsart verlangt: die Prognose
 * einer Abschlagsrechnung in Monaten, der tatsächliche Zeitraum auf den Tag.
 */
internal fun servicePeriod(period: ServicePeriod, type: InvoiceType): Latex =
    if (type.servicePeriodIsForecast) {
        GermanFormat.monthPeriod(period.start, period.end)
    } else {
        GermanFormat.servicePeriod(period.start, period.end)
    }
