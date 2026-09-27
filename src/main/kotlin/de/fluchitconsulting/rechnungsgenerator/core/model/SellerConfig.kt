package de.fluchitconsulting.rechnungsgenerator.core.model

import de.fluchitconsulting.rechnungsgenerator.core.serialization.valueWhenPresent
import de.fluchitconsulting.rechnungsgenerator.core.values.Bic
import de.fluchitconsulting.rechnungsgenerator.core.values.CountryCode
import de.fluchitconsulting.rechnungsgenerator.core.values.Design
import de.fluchitconsulting.rechnungsgenerator.core.values.Iban
import de.fluchitconsulting.rechnungsgenerator.core.values.InvoiceNumberPattern
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import de.fluchitconsulting.rechnungsgenerator.core.values.LanguageTable
import de.fluchitconsulting.rechnungsgenerator.core.values.RgbColor
import de.fluchitconsulting.rechnungsgenerator.core.values.RgbColorSerializer
import de.fluchitconsulting.rechnungsgenerator.core.values.TaxNumber
import de.fluchitconsulting.rechnungsgenerator.core.values.VatId
import de.fluchitconsulting.rechnungsgenerator.core.values.languageTableSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

// Stammdaten des Rechnungsstellers – auf jeder Rechnung gleich, kommen aus der
// Konfigurationsdatei.
// @SerialName mappt die deutschen YAML-Schlüssel auf englische Property-Namen.
@Serializable
data class SellerConfig(
    @SerialName("firma") val companyName: String,
    @SerialName("inhaber") val ownerName: String,
    @SerialName("anschrift") val address: Address,
    @SerialName("email") val email: String,
    // Nullable Typ (`String?`) + Default `null` = optionaler YAML-Schlüssel.
    @SerialName("telefon") val phone: String? = null,
    @SerialName("webseite") val website: String? = null,
    @SerialName("steuernummer") val taxNumber: TaxNumber? = null,
    // Pflicht für EN 16931 (BT-31) und §14 UStG bei B2B: die USt-IdNr.
    @SerialName("umsatzsteuer-id") val vatId: VatId,
    @SerialName("bank") val bank: BankDetails,
    // Welche Rechnungsnummern gültig sind, weiß nur der Rechnungssteller
    // (ADR-0005). Ohne Eintrag wird die Form nicht geprüft.
    @SerialName("rechnungsnummer-muster") val invoiceNumberPattern: InvoiceNumberPattern? = null,
    @SerialName("zahlungsziel-tage") val paymentTermDays: Int = 14,
    // Handelsregister-Angaben: für Kapitalgesellschaften (UG, GmbH) nach §35a GmbHG
    // Pflicht auf Geschäftsbriefen, für Einzelunternehmer gegenstandslos – deshalb optional.
    @SerialName("registergericht") val registerCourt: String? = null,
    @SerialName("handelsregister-nummer") val registerNumber: String? = null,
    @SerialName("geschaeftsfuehrer") val managingDirector: String? = null,
    // Das Design der Rechnung (ADR-0011). In den Stammdaten und nicht je
    // Rechnung: Alle Rechnungen eines Rechnungsstellers sehen gleich aus.
    @SerialName("design") val design: Design = Design.HELSINKI,
    // Die Akzentfarbe des Designs (#371), ohne Eintrag die der Vorlage. Ob und
    // wo ein Design sie einsetzt, entscheidet seine Vorlage (ADR-0017).
    @SerialName("akzentfarbe")
    @Serializable(with = AccentColorSerializer::class)
    val accentColor: RgbColor? = null,
    // Das Logo (#198): in der YAML nur der Pfad, wie er dasteht. Gelesen wird
    // die Datei in YamlLoader.loadSellerConfig, das allein weiß, wo die
    // Stammdaten liegen und wogegen ein relativer Pfad gilt. Ohne Eintrag kein Logo.
    @SerialName("logo") val logoFile: String? = null,
    // `@Transient`: Dieses Feld kennt die Serialisierung nicht, in der YAML gibt
    // es keinen Schlüssel dafür. Es braucht deshalb einen Vorgabewert.
    @Transient val logo: Logo? = null,
    // Der Schlusssatz über der Grußformel je Sprache (#377), überschreibbar pro
    // Rechnung. Privat wie der Rabatt der Position: Nach draußen geht allein
    // closingLine, das die Vorgabe kennt.
    @SerialName("schlusssatz")
    @Serializable(with = ClosingLineSerializer::class)
    private val closingLines: LanguageTable = LanguageTable.EMPTY,
) {
    /**
     * Der Schlusssatz für eine Rechnung in [language]: der aus den Stammdaten,
     * sonst die Vorgabe der Sprache.
     *
     * Fehlt er, kein Abbruch: „Leer scheitert laut“ gilt für Angaben, die
     * falsch schaden, etwa die Bankverbindung. Ein allgemeiner Dank schadet
     * nicht, und in der falschen Sprache steht er so nie da.
     *
     * Ohne `else`: Eine neue Sprache bringt ihre Vorgabe mit.
     */
    fun closingLine(language: Language): String =
        closingLines[language]
            ?: when (language) {
                Language.GERMAN -> "Ich bedanke mich für das Vertrauen und die angenehme Zusammenarbeit."
                Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> "Thank you for your trust and the pleasant collaboration."
            }
}

@Serializable
data class Address(
    @SerialName("strasse") val street: String,
    // String, nicht Int: führende Nullen ("01067 Dresden") gehen sonst verloren.
    @SerialName("plz") val postalCode: String,
    @SerialName("ort") val city: String,
    // ISO 3166-1 alpha-2, wie EN 16931 es verlangt (BT-40/BT-55).
    @SerialName("land") val countryCode: CountryCode = CountryCode("DE"),
)

@Serializable
data class BankDetails(
    @SerialName("kontoinhaber") val accountHolder: String,
    @SerialName("iban") val iban: Iban,
    @SerialName("bic") val bic: Bic,
    @SerialName("institut") val bankName: String,
)

// Steht `akzentfarbe` da, braucht der Schlüssel einen Wert. Farben schreibt man
// gewohnt mit `#`, und `akzentfarbe: #31AE34` ist in YAML ein Schlüssel mit
// Kommentar und ohne Wert: Ohne diesen Serializer käme `null` an, und die
// Rechnung erschiene still in der Farbe der Vorlage. Die anderen optionalen
// Schlüssel lassen einen leeren Wert als "keiner" gelten; nur bei diesem
// beginnt ein Wert üblicherweise mit `#`.
object AccentColorSerializer :
    KSerializer<RgbColor?> by valueWhenPresent(
        RgbColorSerializer,
        "akzentfarbe steht ohne Wert da. Ein # ohne Anführungszeichen beginnt in YAML einen Kommentar – " +
            "die Farbe bitte ohne # schreiben, etwa 31AE34, oder den Schlüssel weglassen.",
    )

object ClosingLineSerializer : KSerializer<LanguageTable> by languageTableSerializer("schlusssatz")
