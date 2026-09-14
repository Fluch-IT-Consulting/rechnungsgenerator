package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice

/**
 * Hinweise, die den Lauf nicht abbrechen: Angaben, die zulässig sind, aber
 * selten gemeint. Die Hinweise zur Bestätigungsabfrage stehen bei ihr, in
 * [VatIdCheckRules].
 */
internal object PlausibilityHints {
    fun check(calculated: CalculatedInvoice): List<Finding> =
        buildList {
            val invoice = calculated.invoice

            // Menge 0 ist zulässig, aber fast immer ein Tippfehler (#47).
            invoice.items.forEachIndexed { index, item ->
                if (item.quantity.signum() == 0) {
                    add(
                        Finding(
                            Rule.MENGE_NULL,
                            Subject.Item(index, item.name),
                            "Menge 0 – die Position steht mit 0,00 € auf der Rechnung. Ist das gewollt?",
                        ),
                    )
                }
            }

            // §14 Abs. 2 UStG verlangt die Rechnung innerhalb von sechs Monaten nach
            // Ausführung der Leistung (#47). Verglichen mit dem Ende der Leistung,
            // nicht mit heute: So ergibt dieselbe Datei jederzeit dieselben
            // Hinweise, auch wenn sie ein Jahr später noch einmal erzeugt wird. Bei
            // der Abschlagsrechnung schweigt er, denn ihr Zeitraum ist eine Prognose.
            // Beim Storno auch: Die Frist gilt dem Ausstellen einer Rechnung, nicht
            // dem Aufheben – sonst forderte der Hinweis, ein richtiges Datum zu
            // ändern (siehe InvoiceType.chargesForService).
            val serviceEnd = invoice.servicePeriod.end
            if (invoice.type.chargesForService &&
                !invoice.type.servicePeriodIsForecast &&
                invoice.issueDate.isAfter(serviceEnd.plusMonths(6))
            ) {
                add(
                    Finding(
                        Rule.RECHNUNG_SPAET,
                        Subject.WholeInvoice,
                        "Die Rechnung vom ${invoice.issueDate} kommt mehr als sechs Monate nach dem Ende der " +
                            "Leistung am $serviceEnd. §14 Abs. 2 UStG verlangt sie innerhalb von sechs Monaten – " +
                            "ist das Rechnungsdatum richtig?",
                    ),
                )
            }
        }
}
