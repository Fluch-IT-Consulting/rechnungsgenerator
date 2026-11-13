package de.fluchitconsulting.rechnungsgenerator.core.localization

import de.fluchitconsulting.rechnungsgenerator.core.serialization.InvalidValueException
import de.fluchitconsulting.rechnungsgenerator.core.serialization.stringSerializer
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class InvalidLanguageRangeException(message: String) : InvalidValueException(message)

/**
 * `{de-DE: …, en-GB: …}`. Die Schlüssel sind Sprachbereiche, keine Sprachen: `en` gilt für jedes Englisch ohne
 * eigenen Eintrag. Zwei Schlüssel, die normalisiert derselbe sind, sind ein Widerspruch.
 */
class LanguageTable(raw: Map<String, String>) {
    private val texts: Map<String, String> = Ranges().let { ranges -> raw.mapKeys { ranges.add(it.key) } }

    /** Der Text für [language]: unter ihrem genauen Code, sonst unter ihrer Hauptsprache, sonst keiner. */
    operator fun get(language: Language): String? = language.ranges.firstNotNullOfOrNull { texts[it] }

    internal fun toMap(): Map<String, String> = texts

    override fun equals(other: Any?): Boolean = other is LanguageTable && texts == other.texts

    override fun hashCode(): Int = texts.hashCode()

    override fun toString(): String = texts.toString()

    internal class Ranges {
        private val seen = mutableMapOf<String, String>()

        fun add(key: String): String {
            val range = canonical(key)
            seen.put(range, key)?.let { first ->
                throw InvalidLanguageRangeException("'$first' und '$key' meinen denselben Sprachbereich $range.")
            }
            return range
        }
    }

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

/** Prüft jeden Schlüssel schon beim Lesen, damit die Meldung seine Fundstelle nennt (#548). */
object LanguageTableSerializer : KSerializer<LanguageTable> {
    private val map = MapSerializer(String.serializer(), String.serializer())

    override val descriptor: SerialDescriptor = map.descriptor

    override fun serialize(encoder: Encoder, value: LanguageTable) = map.serialize(encoder, value.toMap())

    override fun deserialize(decoder: Decoder): LanguageTable {
        // Je Tabelle neu: Ranges merkt sich die gelesenen Bereiche.
        val ranges = LanguageTable.Ranges()
        val key = stringSerializer("LanguageRange", { it }) { raw -> raw.also { ranges.add(it) } }
        return LanguageTable(MapSerializer(key, String.serializer()).deserialize(decoder))
    }
}
