package de.fluchitconsulting.rechnungsgenerator.values

import java.time.LocalDate

/**
 * Der Wert einer Variablen in einem [TextTemplate], noch ohne Format: Wie ein
 * Datum oder eine Nummer aussieht, entscheidet erst, wer ihn einsetzt.
 *
 * Versiegelt, damit jedes Format in einem `when` ohne `else` entscheidet – und
 * eine weitere Art von Wert jedes Format zur Antwort zwingt.
 */
sealed interface TextValue {
    data class Text(val text: String) : TextValue

    data class Date(val date: LocalDate) : TextValue
}
