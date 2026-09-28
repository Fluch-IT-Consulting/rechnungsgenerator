package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.values.Currency

/**
 * Die Währung (#409, #418): Eine Rechnung in Fremdwährung weist Umsatzsteuer
 * nur mit einer Umrechnung in Euro aus, und eine Umrechnung steht nur dort,
 * wo es etwas umzurechnen gibt.
 *
 * Die geschuldete Steuer muss in Euro dastehen (Art. 230 MwStSystRL),
 * umgerechnet nach §16 Abs. 6 UStG. Kurs und Monat nennt die Rechnungsdatei;
 * welcher Monat maßgeblich ist, prüft der Generator nicht, er hängt an der
 * Versteuerung des Rechnungsstellers.
 *
 * Gefragt wird nach der Kategorie S und nicht nach dem Steuerbetrag: Sie ist
 * die einzige mit einem Satz über null (REGELSATZ_MIT_NULL), und ein Betrag
 * von null – etwa eine Position über 0,00 – machte die Rechnung sonst nur
 * zufällig zulässig. Jeder Befund gilt der Rechnung und nicht einer Position:
 * Der Grund ist die Währung, und bei einer versehentlichen Inlandsrechnung in
 * USD stünde derselbe Satz sonst für jede Position da.
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

        // Ohne `else`, wie bei den übrigen Gruppen (#121): Eine neue Art
        // übersetzt hier erst, wenn entschieden ist, ob sie Steuer in
        // Fremdwährung tragen darf. Auf der Abschlagsrechnung zählt der Monat
        // der Vereinnahmung, der beim Erzeugen oft nicht feststeht; die
        // Schlussrechnung setzte Abschläge ab, die zu verschiedenen Kursen
        // versteuert wurden. Dort bleibt es bei diesem einen Befund – ein Satz
        // über die fehlende Umrechnung lenkte nur ab.
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
