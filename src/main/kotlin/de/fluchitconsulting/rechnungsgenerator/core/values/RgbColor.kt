package de.fluchitconsulting.rechnungsgenerator.core.values

import de.fluchitconsulting.rechnungsgenerator.core.serialization.stringSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

class InvalidRgbColorException(message: String) : IllegalArgumentException(message)

/**
 * Sechs Hexadezimalziffern ohne `#`: In YAML beginnt mit `#` ein Kommentar. Deshalb meldet sich auch ein `#` in
 * Anführungszeichen, sonst wäre dieselbe Schreibweise mal richtig und mal still falsch.
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
