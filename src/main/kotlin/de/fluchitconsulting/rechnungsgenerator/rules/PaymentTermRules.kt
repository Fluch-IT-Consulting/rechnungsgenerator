package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice

/**
 * Das Zahlungsziel.
 *
 * Bestimmt wird es an genau einer Stelle, [CalculatedInvoice.paymentTermDays],
 * und nicht für diese Regel noch einmal: Zwei Stellen mit derselben Zeile
 * stimmten nur zufällig überein (#71).
 */
internal object PaymentTermRules {
    fun check(calculated: CalculatedInvoice): List<Finding> {
        val termDays = calculated.paymentTermDays
        // Negativ setzte es die Fälligkeit vor das Rechnungsdatum (#47). Die
        // Meldung nennt, woher der Wert kommt; 0 heißt "sofort" und bleibt.
        if (termDays >= 0) return emptyList()
        val quelle = if (calculated.invoice.paymentTermDaysOverride != null) "in der Rechnung" else "in den Stammdaten"
        return listOf(
            Finding(
                Rule.ZAHLUNGSZIEL_NEGATIV,
                Subject.WholeInvoice,
                "Das Zahlungsziel $quelle ist $termDays Tage, die Fälligkeit läge also vor dem " +
                    "Rechnungsdatum. Bitte 'zahlungsziel-tage' prüfen; 0 heißt sofort.",
            ),
        )
    }
}
