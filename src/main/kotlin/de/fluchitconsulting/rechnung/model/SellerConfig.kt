package de.fluchitconsulting.rechnung.model

import de.fluchitconsulting.rechnung.values.Bic
import de.fluchitconsulting.rechnung.values.Iban
import de.fluchitconsulting.rechnung.values.TaxNumber
import de.fluchitconsulting.rechnung.values.VatId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Stammdaten des Rechnungsstellers – auf jeder Rechnung gleich, kommen aus der
// Konfigurationsdatei. `data class` erzeugt equals/hashCode/toString/copy automatisch.
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
    @SerialName("zahlungsziel-tage") val paymentTermDays: Int = 14,
    // Handelsregister-Angaben: für Kapitalgesellschaften (UG, GmbH) nach §35a GmbHG
    // Pflicht auf Geschäftsbriefen, für Einzelunternehmer gegenstandslos – deshalb optional.
    @SerialName("registergericht") val registerCourt: String? = null,
    @SerialName("handelsregister-nummer") val registerNumber: String? = null,
    @SerialName("geschaeftsfuehrer") val managingDirector: String? = null,
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
    @SerialName("land") val countryCode: String = "DE",
)

@Serializable
data class BankDetails(
    @SerialName("kontoinhaber") val accountHolder: String,
    @SerialName("iban") val iban: Iban,
    @SerialName("bic") val bic: Bic,
    @SerialName("institut") val bankName: String,
)
