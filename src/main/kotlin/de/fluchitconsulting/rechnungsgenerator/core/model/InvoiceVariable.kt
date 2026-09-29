package de.fluchitconsulting.rechnungsgenerator.core.model

import de.fluchitconsulting.rechnungsgenerator.core.text.TextTemplate
import de.fluchitconsulting.rechnungsgenerator.core.text.VariableResolver

/**
 * Die Werte, die ein [TextTemplate] als `<<NAME>>` nennen kann. Nicht die Platzhalter der Vorlage: Diese löst
 * ein [VariableResolver] auf (ADR-0019).
 */
enum class InvoiceVariable {
    // BG-3: BT-25 und BT-26
    URSPRUNGSNUMMER,
    URSPRUNGSDATUM,
}
