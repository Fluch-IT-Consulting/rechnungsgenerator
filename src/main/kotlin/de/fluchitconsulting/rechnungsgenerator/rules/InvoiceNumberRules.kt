package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice

/**
 * Die Form der Rechnungsnummer (ADR-0005).
 *
 * Warum hier und nicht beim Einlesen: Ob eine Nummer gültig ist, hängt an
 * zwei Dateien – der Rechnung und den Stammdaten. Ein Serialisierer sieht
 * immer nur eine.
 *
 * Warum nicht auch die Nummern der abgesetzten Abschläge: Die stammen aus
 * früheren Rechnungen, womöglich aus der Zeit vor einem Wechsel des Musters.
 * Gegen das heutige Muster geprüft, hielte eine korrekte alte Nummer die
 * Schlussrechnung auf.
 *
 * Eine Regel erübrigt die nächste: Eine fehlende Nummer passt zu keinem Muster,
 * und das zu melden hieße, denselben Fehler zweimal zu nennen. `return` statt
 * eines Mechanismus zur Unterdrückung – dieselbe Wahl wie bei den Abschlägen.
 */
internal object InvoiceNumberRules {
    fun check(calculated: CalculatedInvoice): List<Finding> {
        val number = calculated.invoice.number

        // Leer, Leerraum und der Dateiname gelten unabhängig vom Muster, also
        // auch ohne Eintrag in den Stammdaten: Aus der Nummer entsteht der
        // Dateiname des PDF, und ein gequotetes ' R26-009' ergäbe still eine
        // andere Datei.
        if (number.isBlank()) {
            return listOf(
                Finding(
                    Rule.RECHNUNGSNUMMER_FEHLT,
                    Subject.WholeInvoice,
                    "Die Rechnungsnummer fehlt – 'nummer' ist leer. Aus ihr entsteht auch der Dateiname des PDF.",
                ),
            )
        }
        if (number != number.trim()) {
            return listOf(
                Finding(
                    Rule.RECHNUNGSNUMMER_MIT_LEERRAUM,
                    Subject.WholeInvoice,
                    "Die Rechnungsnummer '$number' beginnt oder endet mit Leerraum. Nur ein gequoteter " +
                        "YAML-Wert kann ihn tragen; gemeint war vermutlich '${number.trim()}'.",
                ),
            )
        }
        // Vor dem Muster, weil es die Ursache nennt: "///" passt zu keinem
        // vernünftigen Muster, aber ändern muss man die Zeichen (#150).
        if (calculated.fileBaseName == null) {
            return listOf(
                Finding(
                    Rule.RECHNUNGSNUMMER_OHNE_DATEINAMEN,
                    Subject.WholeInvoice,
                    "Von der Rechnungsnummer '$number' bleibt kein Dateiname übrig – stehen bleiben nur " +
                        "Buchstaben A–Z, Ziffern, Punkt, Unterstrich und Bindestrich.",
                ),
            )
        }

        // Ohne Muster in den Stammdaten wird die Form nicht geprüft: Welche
        // Nummern gültig sind, weiß nur der Rechnungssteller.
        val muster = calculated.config.invoiceNumberPattern ?: return emptyList()
        if (!muster.matches(number)) {
            return listOf(
                Finding(
                    Rule.RECHNUNGSNUMMER_PASST_NICHT,
                    Subject.WholeInvoice,
                    "Die Rechnungsnummer '$number' passt nicht zum Muster '$muster' aus den Stammdaten " +
                        "('rechnungsnummer-muster').",
                ),
            )
        }
        return emptyList()
    }
}
