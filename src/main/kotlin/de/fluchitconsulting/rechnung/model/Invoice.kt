package de.fluchitconsulting.rechnung.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

// Die rechnungsspezifischen Daten – eine YAML-Datei pro Rechnung.
// Enthält bewusst nur Eingaben; alles Berechenbare (Summen, USt., Fälligkeit)
// entsteht im InvoiceCalculator, damit es genau eine Quelle der Wahrheit gibt.
@Serializable
data class Invoice(
    @SerialName("nummer") val number: String,
    @SerialName("datum") val issueDate: IsoDate,
    // Leistungszeitraum: Pflichtangabe nach §14 UStG, EN 16931 BT-73/BT-74.
    @SerialName("leistungszeitraum") val servicePeriod: ServicePeriod,
    @SerialName("kunde") val customer: Customer,
    @SerialName("positionen") val items: List<LineItem>,
    // Überschreibt bei Bedarf das Zahlungsziel aus den Stammdaten.
    @SerialName("zahlungsziel-tage") val paymentTermDaysOverride: Int? = null,
    @SerialName("bemerkung") val note: String? = null,
)

@Serializable
data class ServicePeriod(
    @SerialName("von") val start: IsoDate,
    @SerialName("bis") val end: IsoDate,
)

@Serializable
data class Customer(
    @SerialName("name") val name: String,
    @SerialName("anschrift") val address: Address,
    // EN 16931 BT-10: z. B. Bestellnummer oder Leitweg-ID des Kunden.
    @SerialName("referenz") val buyerReference: String? = null,
)

@Serializable
data class LineItem(
    @SerialName("beschreibung") val description: String,
    @SerialName("menge") val quantity: Money,
    @SerialName("einheit") val unit: String = "Std.",
    @SerialName("einzelpreis") val unitPrice: Money,
    // Pro Position, damit gemischte Sätze (19 %/7 %/0 %) möglich sind – EN 16931 BT-152.
    @SerialName("ust-satz") val vatRatePercent: Money = BigDecimal("19"),
)
