package de.fluchitconsulting.rechnung.values

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Die Maßeinheit einer Position – EN 16931 BT-130.
 *
 * Bewusst eine geschlossene Menge statt Freitext: Die Norm verlangt einen Code
 * aus UN/ECE-Empfehlung 20 (Regel BR-CL-23), und die Angabe ist Pflicht
 * (BR-23). Ein Freitextfeld würde den Fehler erst beim Empfänger zeigen; so
 * fällt ein Tippfehler wie "Stnd." schon beim Einlesen auf.
 *
 * [label] ist die Schreibweise in der YAML und im PDF, [code] die Kennung fürs
 * spätere ZUGFeRD-XML. Die Liste deckt ab, was in einer Beratungsrechnung
 * vorkommt; weitere Einheiten sind eine Zeile.
 */
@Serializable(with = UnitSerializer::class)
enum class Unit(val label: String, val code: String) {
    HOUR("Std.", "HUR"),
    DAY("Tage", "DAY"),
    WEEK("Wochen", "WEK"),
    MONTH("Monate", "MON"),
    PIECE("Stück", "H87"),

    // LS = lump sum. C62 ("one") wäre die neutrale Alternative, benennt aber
    // nicht, dass es sich um eine Pauschale handelt.
    FLAT_RATE("pauschal", "LS"),
    KILOMETRE("km", "KMT"),
    ;

    override fun toString(): String = label

    companion object {
        private val BY_LABEL = entries.associateBy { it.label.lowercase() }

        /** Löst die YAML-Schreibweise auf; unbekannte Einheiten nennen die zulässigen. */
        fun fromLabel(label: String): Unit =
            BY_LABEL[label.trim().lowercase()]
                ?: throw SerializationException(
                    "Unbekannte Einheit '$label'. Zulässig sind: ${entries.joinToString { it.label }}",
                )
    }
}

object UnitSerializer : KSerializer<Unit> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Unit", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Unit) = encoder.encodeString(value.label)

    override fun deserialize(decoder: Decoder): Unit = Unit.fromLabel(decoder.decodeString())
}
