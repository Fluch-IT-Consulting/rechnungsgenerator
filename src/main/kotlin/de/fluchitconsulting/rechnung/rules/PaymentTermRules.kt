package de.fluchitconsulting.rechnung.rules

import de.fluchitconsulting.rechnung.CalculatedInvoice
import de.fluchitconsulting.rechnung.Finding
import de.fluchitconsulting.rechnung.Rule
import de.fluchitconsulting.rechnung.Subject

/**
 * Das Zahlungsziel.
 *
 * Bestimmt wird es an genau einer Stelle, [CalculatedInvoice.paymentTermDays].
 * Vorher rechnete `calculate` es für diese Regel noch einmal selbst aus, mit
 * derselben Zeile – zwei Stellen, die nur zufällig übereinstimmten (#71).
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
