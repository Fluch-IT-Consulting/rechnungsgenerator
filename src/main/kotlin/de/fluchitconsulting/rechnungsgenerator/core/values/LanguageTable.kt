package de.fluchitconsulting.rechnungsgenerator.core.values

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Wird geworfen, wenn ein Schlüssel einer [LanguageTable] kein zulässiger Sprachbereich ist. */
class InvalidLanguageRangeException(message: String) : IllegalArgumentException(message)

/**
 * Ein Text je Sprache, wie ihn der Rechnungssteller schreibt:
 * `{de-DE: …, en-GB: …}`.
 *
 * Die Schlüssel sind Sprachbereiche und keine Sprachen. `sprache:` sagt, in
 * welcher Sprache eine Rechnung *ist*, und das ist genau eine; ein Schlüssel
 * hier sagt, für welche Rechnungen ein Text *gilt*. Deshalb darf auch die
 * Hauptsprache allein stehen, `en`, und [get] sucht entlang
 * [Language.ranges]. Stehen `en` und `en-GB` beide da, ist das kein Fehler,
 * sondern der Zweck: britisch dies, jedes andere Englisch das.
 *
 * Normalisiert wird im Konstruktor, auf die Schreibweise aus
 * [Language.RANGES]. Zwei Schlüssel, die danach derselbe sind, sind ein
 * Widerspruch und kein Vorrang.
 */
class LanguageTable(raw: Map<String, String>) {
    private val texts: Map<String, String> =
        raw.entries
            .groupBy({ canonical(it.key) }, { it.key })
            .onEach { (range, keys) ->
                if (keys.size > 1) {
                    throw InvalidLanguageRangeException(
                        "${keys.joinToString(" und ") { "'$it'" }} meinen denselben Sprachbereich $range.",
                    )
                }
            }.mapValues { (_, keys) -> raw.getValue(keys.single()) }

    /** Der Text für [language]: unter ihrem genauen Code, sonst unter ihrer Hauptsprache, sonst keiner. */
    operator fun get(language: Language): String? = language.ranges.firstNotNullOfOrNull { texts[it] }

    /** Die Einträge unter ihren normalisierten Schlüsseln, für den Serializer. */
    internal fun toMap(): Map<String, String> = texts

    override fun equals(other: Any?): Boolean = other is LanguageTable && texts == other.texts

    override fun hashCode(): Int = texts.hashCode()

    override fun toString(): String = texts.toString()

    companion object {
        val EMPTY = LanguageTable(emptyMap())

        private fun canonical(key: String): String =
            Language.RANGES.firstOrNull { it.equals(key.trim(), ignoreCase = true) }
                ?: throw InvalidLanguageRangeException(
                    "Unbekannte Sprache '$key'. Zulässig sind: ${Language.RANGES.joinToString()}",
                )
    }
}

/**
 * Der Serializer einer [LanguageTable] unter dem Schlüssel [key].
 *
 * Er nennt den Schlüssel selbst, weil kaml es bei einem abgelehnten Wert nicht
 * tut. Hier ist der Wert ein Schlüssel der Tabelle – ohne den Namen darüber
 * stünde in der Meldung nur „Unbekannte Sprache 'fr'“, und bei zwei Tabellen
 * wüsste niemand, in welcher.
 */
fun languageTableSerializer(key: String): KSerializer<LanguageTable> =
    object : KSerializer<LanguageTable> {
        private val map = MapSerializer(String.serializer(), String.serializer())

        override val descriptor: SerialDescriptor = map.descriptor

        override fun serialize(encoder: Encoder, value: LanguageTable) = map.serialize(encoder, value.toMap())

        override fun deserialize(decoder: Decoder): LanguageTable =
            try {
                LanguageTable(map.deserialize(decoder))
            } catch (e: InvalidLanguageRangeException) {
                throw SerializationException("$key: ${e.message}", e)
            }
    }
