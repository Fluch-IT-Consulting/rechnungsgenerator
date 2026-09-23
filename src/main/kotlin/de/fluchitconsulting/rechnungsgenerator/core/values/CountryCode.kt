package de.fluchitconsulting.rechnungsgenerator.core.values

import de.fluchitconsulting.rechnungsgenerator.core.serialization.stringSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/** Wird geworfen, wenn ein Ländercode nicht die Form von ISO 3166-1 alpha-2 hat. */
class InvalidCountryCodeException(message: String) : IllegalArgumentException(message)

/**
 * Der Ländercode einer Anschrift nach ISO 3166-1 alpha-2 (BT-40 für den Kunden,
 * BT-55 für den Rechnungssteller).
 *
 * Wozu ein eigener Typ für zwei Buchstaben: Solange `land` ein String war,
 * entschied jede Vergleichsstelle selbst, ob Groß- und Kleinschreibung zählt –
 * und sie entschieden verschieden. Der Renderer verglich `!= "DE"`
 * zeichengenau, der Calculator mit `ignoreCase`. Bei `land: de` hielt der
 * Calculator die Rechnung deshalb für eine Inlandsrechnung, während der
 * Renderer eine Landzeile unter die Anschrift druckte. Beide taten, was
 * dastand.
 *
 * Hier ist `de` nicht *wie* `DE`, sondern derselbe Wert – normalisiert wird
 * einmal, im Konstruktor. Damit kann die Frage am Vergleichsort gar nicht mehr
 * auftauchen, und `EuMemberStates` braucht sein eigenes `uppercase()` nicht
 * mehr.
 *
 * Geprüft wird nur die **Form**, nicht gegen ein Verzeichnis der Staaten.
 * Eine solche Liste wäre die zweite, die still veraltet (siehe
 * [EuMemberStates] mit `AS_OF`, einem Test, der nach sechs Monaten rot wird,
 * und einem Laufzeit-Hinweis) – und ein falscher, aber existierender Code (AT
 * statt CH) käme ohnehin durch beide Prüfungen. Was die Formprüfung fängt, ist
 * das, was ein Mensch tatsächlich tippt, wenn er das Feld missversteht:
 * "Deutschland", "D", "GER".
 *
 * Nebenbei bleibt damit `EL` zulässig – Griechenland heißt in den
 * Umsatzsteuer-Systemen der EU so, in ISO 3166 dagegen GR.
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

    // equals/hashCode von Hand, wie bei den Kennungen in Identifiers.kt: Eine
    // data class verglich den Rohwert des Konstruktors, nicht den
    // normalisierten Wert – und genau auf den kommt es hier an.
    override fun equals(other: Any?): Boolean = other is CountryCode && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}

object CountryCodeSerializer :
    KSerializer<CountryCode> by stringSerializer("CountryCode", { it.value }, { parseOrFail(it) })

// Wie in Identifiers.kt: Eine fehlerhafte Angabe wird als SerializationException
// gemeldet, damit die CLI sie wie einen YAML-Fehler nennt und nicht wie einen
// Programmabsturz.
private fun parseOrFail(raw: String): CountryCode =
    try {
        CountryCode(raw)
    } catch (e: InvalidCountryCodeException) {
        throw SerializationException(e.message, e)
    }
