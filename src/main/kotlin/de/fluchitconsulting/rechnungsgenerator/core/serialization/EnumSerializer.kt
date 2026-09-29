package de.fluchitconsulting.rechnungsgenerator.core.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/**
 * Normalisiert Eingabe und Schlüssel mit derselben [normalize], sonst wiche eine Kopie unbemerkt ab.
 *
 * @param unknown der Satzanfang samt Artikel: „Unbekanntes Prüfverfahren“ ließe sich nicht raten.
 */
class CodeLookup<T>(
    private val unknown: String,
    private val values: List<T>,
    private val normalize: (String) -> String,
    keyOf: (T) -> String,
    private val describe: (T) -> String = keyOf,
) {
    private val byKey = values.associateBy { normalize(keyOf(it)) }

    fun of(raw: String): T =
        byKey[normalize(raw)]
            ?: throw SerializationException(
                "$unknown '$raw'. Zulässig sind: ${values.joinToString { describe(it) }}",
            )
}

/** Die Unterschiede bleiben sichtbar: `TaxCategory` normalisiert groß wie UNCL 5305, `Unit` und `InvoiceType` klein. */
fun <T> enumSerializer(
    name: String,
    values: List<T>,
    unknown: String,
    normalize: (String) -> String,
    keyOf: (T) -> String,
    describe: (T) -> String = keyOf,
): KSerializer<T> {
    val lookup = CodeLookup(unknown, values, normalize, keyOf, describe)
    return stringSerializer(name, keyOf, lookup::of)
}
