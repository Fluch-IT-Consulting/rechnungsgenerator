package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice

/** Das Zahlungsziel kommt aus [CalculatedInvoice.paymentTermDays] und wird hier nicht noch einmal bestimmt (#71). */
internal object PaymentTermRules {
    fun check(calculated: CalculatedInvoice): List<Finding> {
        val termDays = calculated.paymentTermDays
        // Negativ läge die Fälligkeit vor dem Rechnungsdatum; 0 heißt „sofort“.
        if (termDays >= 0) return emptyList()
        val source = if (calculated.invoice.paymentTermDaysOverride != null) "in der Rechnung" else "in den Stammdaten"
        return listOf(
            Finding(
                Rule.ZAHLUNGSZIEL_NEGATIV,
                Subject.WholeInvoice,
                "Das Zahlungsziel $source ist $termDays Tage, die Fälligkeit läge also vor dem " +
                    "Rechnungsdatum. Bitte 'zahlungsziel-tage' prüfen; 0 heißt sofort.",
            ),
        )
    }
}
