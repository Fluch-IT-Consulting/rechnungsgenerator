package de.fluchitconsulting.rechnungsgenerator.core.values

import de.fluchitconsulting.rechnungsgenerator.core.serialization.stringSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/** Wird geworfen, wenn eine Farbe nicht aus sechs Hexadezimalziffern besteht. */
class InvalidRgbColorException(message: String) : IllegalArgumentException(message)

/**
 * Eine Farbe in RGB, geschrieben als sechs Hexadezimalziffern: `31AE34`.
 *
 * Ohne `#` davor, obwohl man Farben so aus Webseiten kennt: In YAML beginnt
 * mit einem `#` nach einem Leerzeichen ein Kommentar, und `farbe: #31AE34`
 * ist ein Schlüssel ohne Wert. Wer das `#` in Anführungszeichen schreibt,
 * bekommt deshalb eine Meldung statt einer stillen Annahme – sonst wäre die
 * Schreibweise mit Anführungszeichen richtig und dieselbe ohne falsch, und
 * niemand sähe den Unterschied.
 *
 * Normalisiert auf Großbuchstaben, damit `31ae34` und `31AE34` derselbe Wert
 * sind. Geprüft wird nur die Form; jede Farbe aus sechs Ziffern ist eine.
 */
@Serializable(with = RgbColorSerializer::class)
class RgbColor(raw: String) {
    val value: String = raw.trim().uppercase()

    init {
        if (value.startsWith("#")) {
            throw InvalidRgbColorException("Keine gültige Farbe: '$raw' (bitte ohne # schreiben, etwa 31AE34)")
        }
        if (!Regex("^[0-9A-F]{6}$").matches(value)) {
            throw InvalidRgbColorException("Keine gültige Farbe: '$raw' (erwartet sechs Hexadezimalziffern, etwa 31AE34)")
        }
    }

    override fun equals(other: Any?): Boolean = other is RgbColor && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}

object RgbColorSerializer :
    KSerializer<RgbColor> by stringSerializer(
        "RgbColor",
        { it.value },
        {
            try {
                RgbColor(it)
            } catch (e: InvalidRgbColorException) {
                throw SerializationException(e.message, e)
            }
        },
    )
