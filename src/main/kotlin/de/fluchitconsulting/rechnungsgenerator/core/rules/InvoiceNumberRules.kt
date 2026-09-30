package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice

/**
 * Hier und nicht beim Einlesen: Ob eine Nummer gültig ist, hängt an Rechnung und Stammdaten (ADR-0005). Die
 * Nummern der Abschläge bleiben ungeprüft, sie stammen womöglich aus der Zeit vor einem anderen Muster.
 */
internal object InvoiceNumberRules {
    fun check(calculated: CalculatedInvoice): List<Finding> {
        val number = calculated.invoice.number

        // Unabhängig vom Muster: Andere Dateien verweisen Zeichen für Zeichen auf die Nummer (#53), und aus ihr
        // entsteht der Jobname und damit die DocumentID (#297). Eine leere Nummer bricht schon beim Lesen ab (#514).
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
        // Vor dem Muster, weil es die Ursache nennt: Ändern muss man die Zeichen (#150).
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

        val pattern = calculated.config.invoiceNumberPattern ?: return emptyList()
        if (!pattern.matches(number)) {
            return listOf(
                Finding(
                    Rule.RECHNUNGSNUMMER_PASST_NICHT,
                    Subject.WholeInvoice,
                    "Die Rechnungsnummer '$number' passt nicht zum Muster '$pattern' aus den Stammdaten " +
                        "('rechnungsnummer-muster').",
                ),
            )
        }
        return emptyList()
    }
}
