package de.fluchitconsulting.rechnungsgenerator.core.values

/**
 * Die Werte einer Rechnung, die ein [TextTemplate] nennen kann, als
 * `<<URSPRUNGSNUMMER>>` und so weiter. Der Name des Eintrags ist der des
 * Platzhalters.
 *
 * Nicht dieselbe Menge wie die Platzhalter der Design-Vorlage: Die füllt der
 * Renderer, diese löst ein [VariableResolver] auf.
 */
enum class InvoiceVariable {
    // Die Rechnung, die ein Storno aufhebt (BG-3): BT-25 und BT-26.
    URSPRUNGSNUMMER,
    URSPRUNGSDATUM,
}
