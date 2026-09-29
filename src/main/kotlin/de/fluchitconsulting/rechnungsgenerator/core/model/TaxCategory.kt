package de.fluchitconsulting.rechnungsgenerator.core.model

import de.fluchitconsulting.rechnungsgenerator.core.serialization.enumSerializer
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/** Wann die Bestätigungsabfrage der USt-IdNr. nachgewiesen sein muss, Rechtsstand August 2026. */
enum class VatIdConfirmation {
    /** §6a UStG, ohne Spielraum. */
    ALWAYS,

    /**
     * Seit 1/2024 keine Voraussetzung des §13b mehr, aber ohne Nachweis kein Vertrauensschutz. Im Inland und im
     * Drittland gibt es die Abfrage nicht: Das BZSt bestätigt nur ausländische Nummern aus der EU (KEIN_VIES_MOEGLICH).
     */
    IN_EU,

    /** Nie – die Kategorie stützt sich nicht auf die USt-IdNr. des Kunden. */
    NEVER,
}

/**
 * BT-151 nach UNCL 5305. Der Satz allein genügt nicht: 0 % kann Nullsatz, Befreiung oder Reverse Charge sein.
 * 7 % ist [STANDARD]. Die regionalen Codes L, M und B fehlen, weil sie hier nicht vorkommen.
 */
@Serializable(with = TaxCategorySerializer::class)
enum class TaxCategory(
    val code: String,
    val label: String,
    /** BT-120 */
    val requiresReason: Boolean,
    val requiresZeroRate: Boolean,
    /**
     * BT-48. Getrennt von [vatIdConfirmation], obwohl beide heute dieselben Kategorien treffen: Das eine ist eine
     * Angabe auf dem Papier, das andere ein Nachweis in der Akte.
     */
    val requiresCustomerVatId: Boolean,
    val vatIdConfirmation: VatIdConfirmation,
    private val germanDefaultReason: String? = null,
) {
    STANDARD(
        "S",
        "Regelsatz",
        requiresReason = false,
        requiresZeroRate = false,
        requiresCustomerVatId = false,
        vatIdConfirmation = VatIdConfirmation.NEVER,
    ),

    // Ohne Befreiungsgrund nach BR-Z-*, am wenigsten gesichert: vor einem Einsatz von Z nachschlagen. Kein Grund
    // heißt Steuerzeile auf dem Blatt, denn Z ist eine Besteuerung mit 0 % und keine Befreiung (#218).
    ZERO_RATED(
        "Z",
        "Nullsatz",
        requiresReason = false,
        requiresZeroRate = true,
        requiresCustomerVatId = false,
        vatIdConfirmation = VatIdConfirmation.NEVER,
    ),

    EXEMPT(
        "E",
        "steuerbefreit",
        requiresReason = true,
        requiresZeroRate = true,
        requiresCustomerVatId = false,
        vatIdConfirmation = VatIdConfirmation.NEVER,
    ),

    REVERSE_CHARGE(
        "AE",
        "Steuerschuldnerschaft des Leistungsempfängers",
        requiresReason = true,
        requiresZeroRate = true,
        requiresCustomerVatId = true,
        vatIdConfirmation = VatIdConfirmation.IN_EU,
        // §14a Abs. 1 und 5 UStG verlangen diesen Wortlaut.
        germanDefaultReason = "Steuerschuldnerschaft des Leistungsempfängers (Reverse Charge)",
    ),

    INTRA_COMMUNITY(
        "K",
        "innergemeinschaftliche Lieferung",
        requiresReason = true,
        requiresZeroRate = true,
        requiresCustomerVatId = true,
        vatIdConfirmation = VatIdConfirmation.ALWAYS,
        germanDefaultReason = "Steuerfreie innergemeinschaftliche Lieferung",
    ),

    EXPORT(
        "G",
        "Ausfuhrlieferung",
        requiresReason = true,
        requiresZeroRate = true,
        // Im Drittland gibt es keine USt-IdNr. abzufragen; die Ausfuhr belegt der Zoll.
        requiresCustomerVatId = false,
        vatIdConfirmation = VatIdConfirmation.NEVER,
        germanDefaultReason = "Steuerfreie Ausfuhrlieferung",
    ),

    OUT_OF_SCOPE(
        "O",
        "nicht steuerbar",
        requiresReason = true,
        requiresZeroRate = true,
        requiresCustomerVatId = false,
        vatIdConfirmation = VatIdConfirmation.NEVER,
    ),
    ;

    val hasDefaultReason: Boolean get() = germanDefaultReason != null

    /**
     * Bei AE „Reverse charge“, der Begriff aus Anlage 8 UStAE; bei K und G die Wörter der Richtlinie. Nie
     * „zero-rated“, das ist hier Z (ADR-0025).
     */
    fun defaultReason(language: Language): String? =
        when (language) {
            Language.GERMAN -> {
                germanDefaultReason
            }
            Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> {
                when (this) {
                    STANDARD, ZERO_RATED, EXEMPT, OUT_OF_SCOPE -> null
                    REVERSE_CHARGE -> "Reverse charge"
                    INTRA_COMMUNITY -> "Exempt intra-Community supply of goods"
                    EXPORT -> "Exempt export of goods"
                }
            }
        }

    override fun toString(): String = code
}

object TaxCategorySerializer :
    KSerializer<TaxCategory> by enumSerializer(
        name = "TaxCategory",
        values = TaxCategory.entries,
        unknown = "Unbekannte Steuerkategorie",
        // Über den Code in Großschreibung, wie UNCL 5305 ihn schreibt.
        normalize = { it.trim().uppercase() },
        keyOf = TaxCategory::code,
        describe = { "${it.code} (${it.label})" },
    )
