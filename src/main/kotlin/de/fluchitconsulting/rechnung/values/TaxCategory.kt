package de.fluchitconsulting.rechnung.values

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

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
        // Über den Code und in Großschreibung, anders als bei Unit und
        // InvoiceType: Die Codeliste UNCL 5305 schreibt "S", "AE", "K".
        private val LOOKUP =
            CodeLookup(
                unknown = "Unbekannte Steuerkategorie",
                values = entries,
                normalize = { it.trim().uppercase() },
                keyOf = TaxCategory::code,
                describe = { "${it.code} (${it.label})" },
            )

        fun fromCode(code: String): TaxCategory = LOOKUP.of(code)
    }
}

object TaxCategorySerializer :
    KSerializer<TaxCategory> by stringSerializer("TaxCategory", TaxCategory::code, TaxCategory::fromCode)
