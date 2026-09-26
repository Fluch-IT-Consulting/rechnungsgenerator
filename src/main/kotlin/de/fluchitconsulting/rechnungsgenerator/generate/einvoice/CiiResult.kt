package de.fluchitconsulting.rechnungsgenerator.generate.einvoice

import de.fluchitconsulting.rechnungsgenerator.core.rules.Finding

/**
 * Was [CiiWriter.write] liefert: das XML, oder die Gründe, warum es noch
 * keines gibt.
 *
 * ZUGFeRD kommt in Schritten (ADR-0023). Was die Rechnung verlangt und der
 * Writer noch nicht schreibt, wird nicht still weggelassen: Das XML ist die
 * maßgebliche Fassung der E-Rechnung, und eine fehlende Angabe fiele dort
 * niemandem auf.
 */
sealed interface CiiResult {
    data class Written(val xml: String) : CiiResult

    /** Alle Gründe auf einmal, als Befunde mit `ZUGFERD_NOCH_NICHT_MOEGLICH` (ADR-0004). */
    data class NotYetPossible(val findings: List<Finding>) : CiiResult
}
