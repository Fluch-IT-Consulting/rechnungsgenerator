package de.fluchitconsulting.rechnungsgenerator.core.values

import de.fluchitconsulting.rechnungsgenerator.core.serialization.stringSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

class InvalidCountryCodeException(message: String) : IllegalArgumentException(message)

/**
 * ISO 3166-1 alpha-2 (BT-40, BT-55), normalisiert im Konstruktor: `de` ist derselbe Wert wie `DE`, und kein
 * Vergleich braucht ein eigenes `uppercase()`. Geprüft wird nur die Form, eine Liste der Staaten veraltete still;
 * deshalb bleibt auch `EL` zulässig.
 */
@Serializable(with = CountryCodeSerializer::class)
class CountryCode(raw: String) {
    val value: String = raw.trim().uppercase()

    init {
        if (!Regex("^[A-Z]{2}$").matches(value)) {
            throw InvalidCountryCodeException(
                "Kein gültiger Ländercode: '$raw' (erwartet zwei Buchstaben nach ISO 3166-1 alpha-2, etwa DE oder AT)",
            )
        }
    }

    // Von Hand: Eine data class verglich den Rohwert, nicht den normalisierten.
    override fun equals(other: Any?): Boolean = other is CountryCode && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}

object CountryCodeSerializer :
    KSerializer<CountryCode> by stringSerializer("CountryCode", { it.value }, { parseOrFail(it) })

// Als SerializationException, damit die CLI sie wie einen YAML-Fehler meldet.
private fun parseOrFail(raw: String): CountryCode =
    try {
        CountryCode(raw)
    } catch (e: InvalidCountryCodeException) {
        throw SerializationException(e.message, e)
    }
