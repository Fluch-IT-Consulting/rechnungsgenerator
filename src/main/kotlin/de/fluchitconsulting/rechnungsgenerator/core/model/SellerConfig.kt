package de.fluchitconsulting.rechnungsgenerator.core.model

import de.fluchitconsulting.rechnungsgenerator.core.localization.LanguageTable
import de.fluchitconsulting.rechnungsgenerator.core.localization.languageTableSerializer
import de.fluchitconsulting.rechnungsgenerator.core.serialization.valueWhenPresent
import de.fluchitconsulting.rechnungsgenerator.core.values.Bic
import de.fluchitconsulting.rechnungsgenerator.core.values.CountryCode
import de.fluchitconsulting.rechnungsgenerator.core.values.Iban
import de.fluchitconsulting.rechnungsgenerator.core.values.InvoiceNumberPattern
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import de.fluchitconsulting.rechnungsgenerator.core.values.RgbColor
import de.fluchitconsulting.rechnungsgenerator.core.values.RgbColorSerializer
import de.fluchitconsulting.rechnungsgenerator.core.values.TaxNumber
import de.fluchitconsulting.rechnungsgenerator.core.values.VatId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class SellerConfig(
    @SerialName("firma") val companyName: String,
    @SerialName("inhaber") val ownerName: String,
    @SerialName("anschrift") val address: Address,
    @SerialName("email") val email: String,
    @SerialName("telefon") val phone: String? = null,
    @SerialName("webseite") val website: String? = null,
    @SerialName("steuernummer") val taxNumber: TaxNumber? = null,
    // BT-31
    @SerialName("umsatzsteuer-id") val vatId: VatId,
    @SerialName("bank") val bank: BankDetails,
    // Ohne Eintrag wird die Form nicht geprüft (ADR-0005).
    @SerialName("rechnungsnummer-muster") val invoiceNumberPattern: InvoiceNumberPattern? = null,
    @SerialName("zahlungsziel-tage") val paymentTermDays: Int = 14,
    // §35a GmbHG, nur für Kapitalgesellschaften.
    @SerialName("registergericht") val registerCourt: String? = null,
    @SerialName("handelsregister-nummer") val registerNumber: String? = null,
    @SerialName("geschaeftsfuehrer") val managingDirector: String? = null,
    // ADR-0011
    @SerialName("design") val design: Design = Design.HELSINKI,
    // Ohne Eintrag die Vorgabe der Vorlage (ADR-0024).
    @SerialName("akzentfarbe")
    @Serializable(with = AccentColorSerializer::class)
    val accentColor: RgbColor? = null,
    // Nur der Pfad; gelesen wird in YamlLoader.loadSellerConfig, das den Ort der Stammdaten kennt (ADR-0012).
    @SerialName("logo") val logoFile: String? = null,
    @Transient val logo: Logo? = null,
    // Privat: Nach draußen geht closingLine, das die Vorgabe kennt.
    @SerialName("schlusssatz")
    @Serializable(with = ClosingLineSerializer::class)
    private val closingLines: LanguageTable = LanguageTable.EMPTY,
) {
    /** Fehlt er, kein Abbruch wie bei der Bankverbindung: Ein allgemeiner Dank schadet nicht. */
    fun closingLine(language: Language): String =
        closingLines[language]
            ?: when (language) {
                Language.GERMAN -> "Ich bedanke mich für das Vertrauen und die angenehme Zusammenarbeit."
                Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> "Thank you for your trust and the pleasant collaboration."
            }
}

/** Was Blatt und XML von einer Anschrift lesen, gleich ob des Rechnungsstellers oder des Kunden. */
sealed interface PostalAddress {
    val street: String
    val postalCode: String?
    val city: String
    val countryCode: CountryCode
}

/** Die Anschrift des Rechnungsstellers. Die Postleitzahl ist Pflicht, anders als beim Kunden ([CustomerAddress]). */
@Serializable
data class Address(
    @SerialName("strasse") override val street: String,
    // String, nicht Int: Sonst ginge die führende Null von „01067“ verloren.
    @SerialName("plz") override val postalCode: String,
    @SerialName("ort") override val city: String,
    // BT-40
    @SerialName("land") override val countryCode: CountryCode = CountryCode("DE"),
) : PostalAddress

@Serializable
data class BankDetails(
    @SerialName("kontoinhaber") val accountHolder: String,
    @SerialName("iban") val iban: Iban,
    @SerialName("bic") val bic: Bic,
    @SerialName("institut") val bankName: String,
)

// `akzentfarbe: #31AE34` ist in YAML ein Schlüssel ohne Wert, die Farbe verschwände still. Nur hier, weil nur
// dieser Wert üblicherweise mit `#` beginnt.
object AccentColorSerializer :
    KSerializer<RgbColor?> by valueWhenPresent(
        RgbColorSerializer,
        "akzentfarbe steht ohne Wert da. Ein # ohne Anführungszeichen beginnt in YAML einen Kommentar – " +
            "die Farbe bitte ohne # schreiben, etwa 31AE34, oder den Schlüssel weglassen.",
    )

object ClosingLineSerializer : KSerializer<LanguageTable> by languageTableSerializer("schlusssatz")
