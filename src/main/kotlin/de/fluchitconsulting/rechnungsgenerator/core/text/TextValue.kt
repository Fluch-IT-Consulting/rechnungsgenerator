package de.fluchitconsulting.rechnungsgenerator.core.text

import java.time.LocalDate

/** Noch ohne Format: Wie ein Datum aussieht, entscheidet, wer ihn einsetzt. */
sealed interface TextValue {
    data class Text(val text: String) : TextValue

    data class Date(val date: LocalDate) : TextValue
}
