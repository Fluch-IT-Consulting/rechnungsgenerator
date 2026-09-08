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
 * Die Umsatzsteuerkategorie einer Position – EN 16931 BT-151, Codeliste UNCL 5305.
 *
 * Die Angabe ist Pflicht (Regel BR-CO-04), und der Code muss aus der Liste
 * stammen (BR-CL-18). Der Satz allein genügt nicht: Ohne Kategorie lässt sich
 * "0 % Umsatzsteuer" nicht von "steuerbefreit" und nicht von
 * "Steuerschuldnerschaft des Leistungsempfängers" unterscheiden – drei Fälle,
 * die auf der Rechnung dasselbe Nullkomma­null ergeben, steuerlich aber grund-
 * verschieden sind.
 *
 * Der ermäßigte deutsche Satz von 7 % ist **keine** eigene Kategorie, sondern
 * ebenfalls [STANDARD] mit Satz 7.
 *
 * Nicht aufgenommen sind die regionalen Codes L (Kanarische Inseln), M (Ceuta
 * und Melilla) und B (Italien). Sie sind Teil der Codeliste, kommen bei einer
 * deutschen Beratungsrechnung aber nicht vor; eine Zeile genügte, sie zu ergänzen.
 */
@Serializable(with = TaxCategorySerializer::class)
enum class TaxCategory(
    val code: String,
    val label: String,
    /** Verlangt die Norm für diese Kategorie einen Befreiungsgrund (BT-120)? */
    val requiresReason: Boolean,
    /** Muss der Steuersatz null sein? */
    val requiresZeroRate: Boolean,
    /** Vorschlag für den Grund, wenn keiner angegeben wird. */
    val defaultReason: String? = null,
) {
    STANDARD("S", "Regelsatz", requiresReason = false, requiresZeroRate = false),

    // Z ist der Nullsatz für Waren – für eine Beratungsrechnung praktisch ohne
    // Bedeutung, der Vollständigkeit halber aber vorhanden. Nach den Regeln
    // BR-Z-* trägt er keinen Befreiungsgrund; hier bin ich mir am wenigsten
    // sicher, vor einem produktiven Einsatz von Z bitte nachschlagen.
    ZERO_RATED("Z", "Nullsatz", requiresReason = false, requiresZeroRate = true),

    EXEMPT("E", "steuerbefreit", requiresReason = true, requiresZeroRate = true),

    REVERSE_CHARGE(
        "AE",
        "Steuerschuldnerschaft des Leistungsempfängers",
        requiresReason = true,
        requiresZeroRate = true,
        // §14a Abs. 5 UStG verlangt genau diese Angabe im Wortlaut.
        defaultReason = "Steuerschuldnerschaft des Leistungsempfängers (Reverse Charge)",
    ),

    INTRA_COMMUNITY(
        "K",
        "innergemeinschaftliche Lieferung",
        requiresReason = true,
        requiresZeroRate = true,
        defaultReason = "Steuerfreie innergemeinschaftliche Lieferung",
    ),

    EXPORT(
        "G",
        "Ausfuhrlieferung",
        requiresReason = true,
        requiresZeroRate = true,
        defaultReason = "Steuerfreie Ausfuhrlieferung",
    ),

    OUT_OF_SCOPE("O", "nicht steuerbar", requiresReason = true, requiresZeroRate = true),
    ;

    override fun toString(): String = code

    companion object {
        private val BY_CODE = entries.associateBy { it.code }

        fun fromCode(code: String): TaxCategory =
            BY_CODE[code.trim().uppercase()]
                ?: throw SerializationException(
                    "Unbekannte Steuerkategorie '$code'. Zulässig sind: " +
                        entries.joinToString { "${it.code} (${it.label})" },
                )
    }
}

object TaxCategorySerializer : KSerializer<TaxCategory> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TaxCategory", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TaxCategory) = encoder.encodeString(value.code)

    override fun deserialize(decoder: Decoder): TaxCategory = TaxCategory.fromCode(decoder.decodeString())
}
