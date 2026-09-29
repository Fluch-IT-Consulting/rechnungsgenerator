package de.fluchitconsulting.rechnungsgenerator.core.localization

import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class InvalidLanguageRangeException(message: String) : IllegalArgumentException(message)

/**
 * `{de-DE: …, en-GB: …}`. Die Schlüssel sind Sprachbereiche, keine Sprachen: `en` gilt für jedes Englisch ohne
 * eigenen Eintrag. Zwei Schlüssel, die normalisiert derselbe sind, sind ein Widerspruch.
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

    internal fun toMap(): Map<String, String> = texts

    override fun equals(other: Any?): Boolean = other is LanguageTable && texts == other.texts

    override fun hashCode(): Int = texts.hashCode()

    override fun toString(): String = texts.toString()

    companion object {
        /** Vom genauesten an, `en-GB` dann `en`, ohne seitwärts zu suchen: `en-US` fiele nie auf `en-GB`. */
        private val Language.ranges: List<String> get() = listOf(tag, tag.substringBefore('-'))

        /** Ein anderer Bereich wie `fr` gälte für keine Rechnung, ein stiller Fehler wie ein vertippter Schlüssel. */
        private val RANGES: List<String> = Language.entries.flatMap { it.ranges }.distinct()

        val EMPTY = LanguageTable(emptyMap())

        private fun canonical(key: String): String =
            RANGES.firstOrNull { it.equals(key.trim(), ignoreCase = true) }
                ?: throw InvalidLanguageRangeException(
                    "Unbekannte Sprache '$key'. Zulässig sind: ${RANGES.joinToString()}",
                )
    }
}

/** Nennt den Schlüssel selbst, weil kaml es bei einem abgelehnten Wert nicht tut. */
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
