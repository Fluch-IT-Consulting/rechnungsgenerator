package de.fluchitconsulting.rechnungsgenerator.core.model

import de.fluchitconsulting.rechnungsgenerator.core.values.Bic
import de.fluchitconsulting.rechnungsgenerator.core.values.CountryCode
import de.fluchitconsulting.rechnungsgenerator.core.values.Design
import de.fluchitconsulting.rechnungsgenerator.core.values.Iban
import de.fluchitconsulting.rechnungsgenerator.core.values.InvoiceNumberPattern
import de.fluchitconsulting.rechnungsgenerator.core.values.TaxNumber
import de.fluchitconsulting.rechnungsgenerator.core.values.VatId
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
    // Das Logo (#198): in der YAML nur der Pfad, wie er dasteht. Gelesen wird
    // die Datei in YamlLoader.loadSellerConfig, das allein weiß, wo die
    // Stammdaten liegen und wogegen ein relativer Pfad gilt. Ohne Eintrag kein Logo.
    @SerialName("logo") val logoFile: String? = null,
    // `@Transient`: Dieses Feld kennt die Serialisierung nicht, in der YAML gibt
    // es keinen Schlüssel dafür. Es braucht deshalb einen Vorgabewert.
    @Transient val logo: Logo? = null,
    // Schlusssatz über der Grußformel; überschreibbar pro Rechnung.
    @SerialName("schlusssatz") val closingLine: String =
        "Ich bedanke mich für das Vertrauen und die angenehme Zusammenarbeit.",
)

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
