package de.fluchitconsulting.rechnung.model

import de.fluchitconsulting.rechnung.values.Percent
import de.fluchitconsulting.rechnung.values.Price
import de.fluchitconsulting.rechnung.values.TaxCategory
import de.fluchitconsulting.rechnung.values.Unit
import de.fluchitconsulting.rechnung.values.VatId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    // BT-48: Pflicht, sobald die Steuerschuld übergeht (Kategorie AE) oder
    // innergemeinschaftlich geliefert wird (K) – dann muss der Empfänger
    // umsatzsteuerlich identifizierbar sein.
    @SerialName("umsatzsteuer-id") val vatId: VatId? = null,
)

/**
 * Eine Rechnungsposition.
 *
 * Die drei Textfelder bilden die Aufteilung ab, die EN 16931 vorgibt – sie
 * unterscheidet danach, *worüber* der Text spricht:
 *
 *  - [name] (BT-153, Pflicht): Wie heißt die Leistung? Kurz, ein Bezeichner.
 *  - [description] (BT-154): Was umfasst sie? Angaben über die Leistung selbst.
 *  - [note] (BT-127): Was ist zu dieser Abrechnungszeile anzumerken? Kein
 *    Merkmal der Leistung, sondern Kontext – etwa "Abschlag September 2026".
 *
 * Zusammen decken [name] und [description] die Forderung aus §14 Abs. 4 Nr. 5
 * UStG ab: die *Art* der Leistung im Namen, ihren *Umfang* in der Beschreibung.
 *
 * Ein einziges Freitextfeld hätte für das PDF gereicht, würde aber beim
 * späteren ZUGFeRD-Export zum Raten zwingen – ein Programm, das
 * "IT-Beratung, Arbeitspaket X" an einem Komma zerteilt, liegt falsch, sobald
 * der Name selbst ein Komma enthält.
 */
@Serializable
data class LineItem(
    @SerialName("bezeichnung") val name: String,
    @SerialName("beschreibung") val description: String? = null,
    @SerialName("hinweis") val note: String? = null,
    // BG-26: Zeitraum dieser Position, falls er vom Zeitraum der Rechnung
    // abweicht – so definiert es die Norm, deshalb hier optional.
    @SerialName("positions-leistungszeitraum") val servicePeriod: ServicePeriod? = null,
    @SerialName("menge") val quantity: Quantity,
    @SerialName("einheit") val unit: Unit = Unit.HOUR,
    // Price statt Money: EN 16931 lässt beim Einzelpreis (BT-146) beliebig viele
    // Nachkommastellen zu. Gerundet wird erst das Positionsnetto (BT-131).
    @SerialName("einzelpreis") val unitPrice: Price,
    // Pro Position, damit gemischte Sätze (19 %/7 %/0 %) möglich sind – EN 16931 BT-152.
    @SerialName("ust-satz") val vatRate: Percent = Percent("19"),
    // BT-151, Pflicht nach BR-CO-04. Der ermäßigte Satz von 7 % ist ebenfalls
    // STANDARD – die Kategorie sagt nicht, wie hoch besteuert wird, sondern ob.
    @SerialName("steuerkategorie") val taxCategory: TaxCategory = TaxCategory.STANDARD,
    // BT-120: Warum keine Steuer anfällt. Für die meisten Kategorien Pflicht;
    // fehlt er, setzt der Calculator den vorgesehenen Wortlaut ein.
    @SerialName("steuerbefreiungsgrund") val taxExemptionReason: String? = null,
)
