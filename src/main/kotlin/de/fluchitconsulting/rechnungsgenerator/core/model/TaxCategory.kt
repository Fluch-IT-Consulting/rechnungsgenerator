package de.fluchitconsulting.rechnungsgenerator.core.model

import de.fluchitconsulting.rechnungsgenerator.core.serialization.enumSerializer
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Wann für eine Kategorie die Bestätigungsabfrage der USt-IdNr. nachgewiesen
 * sein muss.
 *
 * Rechtsstand August 2026. Die Abstufung ist keine Bequemlichkeit, sondern
 * bildet ab, dass die Rechtslage selbst verschieden streng ist – und dass es
 * Fälle gibt, in denen die Abfrage gar nicht existiert.
 */
enum class VatIdConfirmation {
    /**
     * Immer, unabhängig davon, wo der Kunde sitzt. §6a UStG verlangt für die
     * innergemeinschaftliche Lieferung die geprüfte USt-IdNr.; kein Spielraum.
     */
    ALWAYS,

    /**
     * Nur grenzüberschreitend in die EU.
     *
     * Seit Januar 2024 ist die gültige USt-IdNr. keine zwingende Voraussetzung
     * des §13b mehr – entscheidend ist die Unternehmereigenschaft. Ohne
     * dokumentierte Abfrage entfällt aber der Vertrauensschutz, deshalb hier
     * trotzdem Pflicht.
     *
     * Im **Inland** dagegen kein Nachweis (§13b Abs. 2, etwa Bauleistungen):
     * Das BZSt bestätigt *ausländische* USt-IdNrn.; für die Nummer eines
     * deutschen Kunden gibt es diese Abfrage nicht.
     *
     * Und im **Drittland** ebenfalls keiner: VIES und die Abfrage beim BZSt
     * decken nur EU-Mitgliedstaaten ab. Für die Schweiz oder das Vereinigte
     * Königreich gibt es dort nichts abzufragen; einen Nachweis zu verlangen,
     * den es nicht geben kann, wäre keine Strenge, sondern ein Fehler.
     * Stattdessen ein Hinweis – siehe `VatIdCheckRules`, KEIN_VIES_MOEGLICH.
     */
    IN_EU,

    /** Nie – die Kategorie stützt sich nicht auf die USt-IdNr. des Kunden. */
    NEVER,
}

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
    /**
     * Verlangt die Norm die USt-IdNr. des Kunden auf der Rechnung (BT-48)?
     *
     * Getrennt von [vatIdConfirmation], obwohl heute beide bei denselben zwei
     * Kategorien anschlagen: Das eine ist eine Angabe auf dem Papier, das
     * andere ein Nachweis in der eigenen Akte. Sie fallen zusammen, weil es
     * gerade so ist, nicht weil das eine aus dem anderen folgte – und eine
     * Kategorie, die das trennt, würde sonst zwei Regeln auf einmal ändern.
     */
    val requiresCustomerVatId: Boolean,
    /** Wann die Bestätigungsabfrage nachgewiesen sein muss. */
    val vatIdConfirmation: VatIdConfirmation,
    /**
     * Vorschlag für den Grund, wenn keiner angegeben wird, auf Deutsch.
     * Welcher auf der Rechnung steht, sagt [defaultReason] mit ihrer Sprache.
     */
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

    // Z ist der Nullsatz für Waren – für eine Beratungsrechnung praktisch ohne
    // Bedeutung, der Vollständigkeit halber aber vorhanden. Nach den Regeln
    // BR-Z-* trägt er keinen Befreiungsgrund; hier bin ich mir am wenigsten
    // sicher, vor einem produktiven Einsatz von Z bitte nachschlagen.
    //
    // Auf dem Blatt steht er als gewöhnliche Steuerzeile, "0 % auf X: 0,00 €"
    // (#218): Der Nullsatz ist keine Befreiung, sondern eine Besteuerung mit
    // 0 %, und §14 Abs. 4 Nr. 8 UStG verlangt dafür Satz und Steuerbetrag.
    // Dass er keinen Grund trägt, ist genau das Unterscheidungsmerkmal.
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
        // §14a Abs. 1 und 5 UStG verlangen genau diese Angabe im Wortlaut.
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
        // Ausfuhr ins Drittland: Dort gibt es keine USt-IdNr. abzufragen, und
        // die Norm verlangt sie für BT-48 auch nicht. Der Nachweis der Ausfuhr
        // läuft über den Beleg des Zolls, nicht über VIES.
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

    /**
     * Bringt die Kategorie einen Grund mit, wenn die Position keinen nennt?
     *
     * Ohne Sprache: Ob eine Kategorie einen Grund mitbringt, ist eine
     * Frage der Kategorie und nicht der Sprache, nur sein Wortlaut hängt an
     * ihr. Ein Test hält [defaultReason] in jeder Sprache daran fest.
     */
    val hasDefaultReason: Boolean get() = germanDefaultReason != null

    /**
     * Der vorgesehene Grund in [language], oder `null`, wenn die Kategorie
     * keinen hat.
     *
     * Bei AE "Reverse charge" und nichts dazu: Statt des deutschen Wortlauts
     * lässt die Verwaltung den Begriff zu, den Art. 226 Nr. 11a MwStSystRL in
     * der englischen Fassung verwendet (Abschnitt 14a.1 Abs. 6 und Anlage 8
     * UStAE, BMF vom 17.09.2025). Er gilt damit auch im XML (BT-120), und ein
     * deutscher Zusatz ist nicht nötig. Eine eigene Übersetzung wäre ein
     * Wortlaut, den keine Liste der Verwaltung kennt.
     *
     * Bei K und G nennt Anlage 8 nichts, und einen Wortlaut schreibt auch
     * sonst niemand vor: Der Grund darf umgangssprachlich stehen (Abschnitt
     * 14.5 Abs. 20 UStAE). Er sagt deshalb mit den Wörtern der Richtlinie,
     * was der deutsche sagt – "exempt" aus Art. 226 Nr. 11, "intra-Community
     * supply of goods" aus Art. 138. "of goods", weil beide eine Lieferung
     * sind: Eine Dienstleistung ins Drittland ist O und nicht G. Nicht
     * "zero-rated", wie das britische VAT beide nennt: Das ist hier die
     * Kategorie Z, von der sich K und G gerade unterscheiden.
     *
     * Ohne `else`: Eine neue Kategorie sagt, ob sie einen hat.
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
        // Über den Code und in Großschreibung, anders als bei Unit und
        // InvoiceType: Die Codeliste UNCL 5305 schreibt "S", "AE", "K".
        normalize = { it.trim().uppercase() },
        keyOf = TaxCategory::code,
        // Der Code allein sagt niemandem etwas, "S (Regelsatz)" schon.
        describe = { "${it.code} (${it.label})" },
    )
