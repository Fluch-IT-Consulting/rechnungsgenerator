package de.fluchitconsulting.rechnungsgenerator.core.model

import de.fluchitconsulting.rechnungsgenerator.core.values.Currency
import de.fluchitconsulting.rechnungsgenerator.core.values.ExchangeRate
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent
import de.fluchitconsulting.rechnungsgenerator.core.values.Price
import de.fluchitconsulting.rechnungsgenerator.core.values.Unit
import de.fluchitconsulting.rechnungsgenerator.core.values.VatId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Nur Eingaben; alles Berechenbare entsteht in der CalculatedInvoice.
@Serializable
data class Invoice(
    @SerialName("nummer") val number: String,
    @SerialName("datum") val issueDate: IsoDate,
    // BT-3
    @SerialName("art") val type: InvoiceType = InvoiceType.INVOICE,
    // In der Rechnung und nicht in den Stammdaten: Die Sprache gehört zum Kunden (ADR-0025).
    @SerialName("sprache") val language: Language = Language.GERMAN,
    // BT-5 (ADR-0026)
    @SerialName("waehrung") val currency: Currency = Currency.EUR,
    // ADR-0027
    @SerialName("umrechnung") val conversion: CurrencyConversion? = null,
    // BT-73/BT-74
    @SerialName("leistungszeitraum") val servicePeriod: ServicePeriod,
    // BT-14, die eigene Auftragsnummer – nicht die Bestellnummer des Kunden (BT-13) und nicht `referenz` (BT-10).
    @SerialName("auftragsnummer") val salesOrderReference: String? = null,
    @SerialName("kunde") val customer: Customer,
    @SerialName("positionen") val items: List<LineItem>,
    // BT-7, nur auf der Abschlagsrechnung und nur, wenn er feststeht.
    @SerialName("vereinnahmt-am") val receivedOn: IsoDate? = null,
    @SerialName("zahlungsziel-tage") val paymentTermDaysOverride: Int? = null,
    // Keine Vorgabe in den Stammdaten: Vereinbart wird mit dem Kunden, eine Vorgabe landete still auf jeder Rechnung.
    @SerialName("skonto") val skonto: List<SkontoTier> = emptyList(),
    // Nur auf der Schlussrechnung (ADR-0001).
    @SerialName("abschlaege") val prepayments: List<Prepayment> = emptyList(),
    // Nur auf der Stornorechnung und dort Pflicht. Geprüft in den Regeln: Die Meldung soll von der
    // Rechnungsart sprechen statt von einem fehlenden YAML-Schlüssel.
    @SerialName("storniert") val cancelled: CancelledInvoice? = null,
    // Keine Vorgabe in den Stammdaten: Der Satz hängt am Auftrag (#209).
    @SerialName("einleitung") val introduction: String? = null,
    @SerialName("bemerkung") val note: String? = null,
)

/** BG-3 mit BT-25 und BT-26. Die Felder heißen wie beim Abschlag: dieselben Angaben über eine fremde Rechnung. */
@Serializable
data class CancelledInvoice(
    @SerialName("rechnungsnummer") val invoiceNumber: String,
    @SerialName("rechnungsdatum") val issueDate: IsoDate,
)

/** §16 Abs. 6 UStG. Der Monat rechnet nichts und wird nicht geprüft, er macht den Kurs nachprüfbar (ADR-0027). */
@Serializable
data class CurrencyConversion(
    @SerialName("kurs") val rate: ExchangeRate,
    @SerialName("monat") val month: IsoMonth,
)

/**
 * Ein abgesetzter Abschlag, ausgeschrieben statt nachgeschlagen (ADR-0001). [receivedOn] ist fachlich Pflicht und
 * hier optional, damit eine Regel den Fall erklärt statt einer Meldung über einen fehlenden YAML-Schlüssel.
 */
@Serializable
data class Prepayment(
    @SerialName("rechnungsnummer") val invoiceNumber: String,
    @SerialName("rechnungsdatum") val issueDate: IsoDate,
    @SerialName("vereinnahmt-am") val receivedOn: IsoDate? = null,
    @SerialName("steueranteile") val taxShares: List<TaxShare>,
)

/** Netto und Steuer getrennt, weil §14 Abs. 5 Satz 2 UStG beide absetzt (ADR-0002). */
@Serializable
data class TaxShare(
    @SerialName("steuerkategorie") val taxCategory: TaxCategory = TaxCategory.STANDARD,
    @SerialName("ust-satz") val vatRate: Percent,
    @SerialName("netto") val net: Money,
    // Redundant zu netto × satz, damit die Prüfsumme einen Zahlendreher findet (ABSCHLAG_PRUEFSUMME).
    @SerialName("ust") val vat: Money,
)

/** Die Frist in Tagen ab Rechnungsdatum und nicht als Datum: So bleibt sie richtig, wenn sich das Datum verschiebt. */
@Serializable
data class SkontoTier(
    @SerialName("satz") val rate: Percent,
    @SerialName("tage") val days: Int,
)

@Serializable
data class ServicePeriod(
    @SerialName("von") val start: IsoDate,
    @SerialName("bis") val end: IsoDate,
)

@Serializable
data class Customer(
    @SerialName("name") val name: String,
    // BT-56. Das Programm setzt nichts davor, kein „z. Hd.“ und keine Anrede.
    @SerialName("ansprechpartner") val contactPoint: String? = null,
    @SerialName("anschrift") val address: Address,
    // BT-46
    @SerialName("kundennummer") val buyerIdentifier: String? = null,
    // BT-10, etwa Bestellnummer oder Leitweg-ID.
    @SerialName("referenz") val buyerReference: String? = null,
    // BT-48
    @SerialName("umsatzsteuer-id") val vatId: VatId? = null,
    @SerialName("ust-id-geprueft") val vatIdCheck: VatIdCheck? = null,
)

/**
 * Drei Textfelder nach EN 16931: [name] (BT-153), [description] (BT-154) und [note] (BT-127). Ein einziges Feld
 * zwänge die E-Rechnung, es zu zerteilen.
 */
@Serializable
data class LineItem(
    @SerialName("bezeichnung") val name: String,
    @SerialName("beschreibung") val description: String? = null,
    @SerialName("hinweis") val note: String? = null,
    // BG-26, nur wenn er vom Zeitraum der Rechnung abweicht.
    @SerialName("positions-leistungszeitraum") val servicePeriod: ServicePeriod? = null,
    @SerialName("menge") val quantity: Quantity,
    @SerialName("einheit") val unit: Unit = Unit.HOUR,
    // Price, nicht Money: BT-146 erlaubt beliebig viele Nachkommastellen, gerundet wird erst BT-131.
    @SerialName("einzelpreis") val unitPrice: Price,
    // BT-152
    @SerialName("ust-satz") val vatRate: Percent = Percent("19"),
    // BT-151. Auch 7 % ist STANDARD: Die Kategorie sagt, ob besteuert wird, nicht wie hoch.
    @SerialName("steuerkategorie") val taxCategory: TaxCategory = TaxCategory.STANDARD,
    // BT-120; ohne Eintrag gilt der Vorgabegrund der Kategorie.
    @SerialName("steuerbefreiungsgrund") val taxExemptionReason: String? = null,
    // BG-27 (ADR-0013). Privat: Nach draußen geht nur [discount], als Typ.
    @SerialName("rabatt") private val discountRate: Percent? = null,
    @SerialName("rabatt-betrag") private val discountAmount: Money? = null,
) {
    /**
     * `get()` ohne Feld: Sonst schriebe kotlinx.serialization `discount` als eigenen Schlüssel mit. Stehen beide
     * Schlüssel da, geht der Satz vor; sichtbar wird das nie, RABATT_DOPPELT bricht vorher ab.
     */
    val discount: Discount? get() =
        discountRate?.let(Discount::Rate)
            ?: discountAmount?.let(Discount::Amount)

    /** Für RABATT_DOPPELT; [discount] trägt genau einen Rabatt. */
    val hasBothDiscounts: Boolean get() = discountRate != null && discountAmount != null
}
