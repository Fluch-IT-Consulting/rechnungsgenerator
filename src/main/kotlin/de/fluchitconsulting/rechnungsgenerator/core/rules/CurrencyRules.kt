package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.values.Currency

/**
 * Steuer in Fremdwährung nur mit Umrechnung in Euro, eine Umrechnung nur, wo es etwas umzurechnen gibt (ADR-0027).
 * Gefragt wird nach Kategorie S und nicht nach dem Steuerbetrag, der zufällig null sein kann. Jeder Befund gilt
 * der Rechnung, nicht einer Position.
 */
internal object CurrencyRules {
    fun check(calculated: CalculatedInvoice): List<Finding> {
        val invoice = calculated.invoice
        if (invoice.currency == Currency.EUR) {
            return listOfNotNull(
                Finding(
                    Rule.UMRECHNUNG_BEI_EURO,
                    Subject.WholeInvoice,
                    "Eine Rechnung in EUR rechnet nichts um. Bitte 'umrechnung' weglassen.",
                ).takeIf { invoice.conversion != null },
            )
        }

        val taxed =
            invoice.items
                .withIndex()
                .filter { (_, item) -> item.taxCategory == TaxCategory.STANDARD }
                .map { (index, _) -> index + 1 }
        if (taxed.isEmpty()) {
            return listOfNotNull(
                Finding(
                    Rule.UMRECHNUNG_OHNE_UMSATZSTEUER,
                    Subject.WholeInvoice,
                    "Keine Position trägt Umsatzsteuer, umzurechnen ist nichts. " +
                        "Bitte 'umrechnung' weglassen oder die Kategorien prüfen.",
                ).takeIf { invoice.conversion != null },
            )
        }

        val positions = if (taxed.size == 1) "Position ${taxed.single()}" else "den Positionen ${taxed.joinToString()}"

        // Auf Abschlags- und Schlussrechnung steht der maßgebliche Kurs nicht fest (ADR-0027); dort bleibt es bei
        // diesem einen Befund.
        when (invoice.type) {
            InvoiceType.INVOICE, InvoiceType.PARTIAL, InvoiceType.CANCELLATION -> {}
            InvoiceType.PREPAYMENT, InvoiceType.FINAL -> {
                return listOf(
                    Finding(
                        Rule.WAEHRUNG_MIT_UMSATZSTEUER_AUF_DIESER_ART_NICHT_MOEGLICH,
                        Subject.WholeInvoice,
                        "Eine ${invoice.type.documentName} in ${invoice.currency} weist keine Umsatzsteuer aus, " +
                            "Kategorie S steht aber in $positions. Bitte in EUR abrechnen oder die Kategorie prüfen.",
                    ),
                )
            }
        }

        return listOfNotNull(
            Finding(
                Rule.WAEHRUNG_MIT_UMSATZSTEUER,
                Subject.WholeInvoice,
                "Kategorie S steht in $positions, die Umsatzsteuer einer Rechnung in ${invoice.currency} muss " +
                    "aber umgerechnet in Euro dastehen. Bitte 'umrechnung' mit Kurs und Monat ergänzen " +
                    "oder in EUR abrechnen.",
            ).takeIf { invoice.conversion == null },
        )
    }
}
