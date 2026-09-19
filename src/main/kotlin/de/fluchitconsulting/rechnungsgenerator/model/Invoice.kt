package de.fluchitconsulting.rechnungsgenerator.model

import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.values.Money
import de.fluchitconsulting.rechnungsgenerator.values.Percent
import de.fluchitconsulting.rechnungsgenerator.values.Price
import de.fluchitconsulting.rechnungsgenerator.values.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.values.Unit
import de.fluchitconsulting.rechnungsgenerator.values.VatId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Die rechnungsspezifischen Daten – eine YAML-Datei pro Rechnung.
// Enthält bewusst nur Eingaben; alles Berechenbare (Summen, USt., Fälligkeit)
// entsteht in der CalculatedInvoice, damit es genau eine Quelle der Wahrheit gibt.
@Serializable
data class Invoice(
    @SerialName("nummer") val number: String,
    @SerialName("datum") val issueDate: IsoDate,
    // BT-3. Ohne Angabe die gewöhnliche Rechnung – bestehende Rechnungsdateien
    // kennen den Schlüssel nicht und sollen sich weiter verhalten wie bisher.
    @SerialName("art") val type: InvoiceType = InvoiceType.INVOICE,
    // Leistungszeitraum: Pflichtangabe nach §14 UStG, EN 16931 BT-73/BT-74.
    @SerialName("leistungszeitraum") val servicePeriod: ServicePeriod,
    // EN 16931 BT-14, die Auftragsreferenz, vom Verkäufer vergeben: die eigene
    // Auftragsnummer aus Angebot oder Auftragsbestätigung. Nicht die
    // Bestellnummer des Kunden (BT-13) und nicht kunde: referenz (BT-10).
    @SerialName("auftragsnummer") val salesOrderReference: String? = null,
    @SerialName("kunde") val customer: Customer,
    @SerialName("positionen") val items: List<LineItem>,
    // BT-7, §14 Abs. 4 Nr. 6 UStG: der Zeitpunkt der Vereinnahmung. Nur auf
    // einer Abschlagsrechnung und nur, wenn er feststeht – war das Geld bei
    // Rechnungsstellung noch nicht da, entfällt die Angabe.
    @SerialName("vereinnahmt-am") val receivedOn: IsoDate? = null,
    // Überschreibt bei Bedarf das Zahlungsziel aus den Stammdaten.
    @SerialName("zahlungsziel-tage") val paymentTermDaysOverride: Int? = null,
    // §14 Abs. 4 Satz 1 Nr. 7 UStG: ein im Voraus vereinbartes Skonto. Nur in
    // der Rechnung und nie in den Stammdaten – vereinbart wird es mit dem
    // Kunden, und eine Vorgabe landete still auf jeder Rechnung.
    @SerialName("skonto") val skonto: List<SkontoTier> = emptyList(),
    // Die vereinnahmten Abschläge, die diese Schlussrechnung absetzt. Nur dort;
    // sobald abgesetzt wird, *ist* das Dokument eine Schlussrechnung.
    @SerialName("abschlaege") val prepayments: List<Prepayment> = emptyList(),
    // Die Ursprungsrechnung, die eine Stornorechnung aufhebt. Nur dort, und
    // dort Pflicht – ein Storno ohne Angabe, was es storniert, ist wertlos.
    // Geprüft wird beides im Calculator, nicht hier: Ein Pflichtfeld je nach
    // Rechnungsart kennt kotlinx.serialization nicht, und die Meldung soll von
    // der Rechnungsart sprechen statt von einem fehlenden YAML-Schlüssel.
    @SerialName("storniert") val cancelled: CancelledInvoice? = null,
    // Der Satz über den Positionen, der sagt, worum es geht (#209). Nur in der
    // Rechnung und ohne Vorgabe in den Stammdaten: Er hängt am Auftrag, eine
    // Vorgabe landete still auf jeder Rechnung.
    @SerialName("einleitung") val introduction: String? = null,
    @SerialName("bemerkung") val note: String? = null,
)

/**
 * Der Bezug einer Stornorechnung auf ihre Ursprungsrechnung – EN 16931 BG-3
 * mit BT-25 (Nummer) und BT-26 (Datum).
 *
 * Die Felder heißen wie beim abgesetzten Abschlag: Es sind dieselben zwei
 * Angaben über dieselbe Sache, eine fremde Rechnung. Nachgeschlagen wird auch
 * hier nichts (ADR-0001) – die Nummer steht in der Datei, nicht in einem
 * Verzeichnis.
 */
@Serializable
data class CancelledInvoice(
    @SerialName("rechnungsnummer") val invoiceNumber: String,
    @SerialName("rechnungsdatum") val issueDate: IsoDate,
)

/**
 * Ein vereinnahmter Abschlag, der in der Schlussrechnung abgesetzt wird.
 *
 * Die Werte stehen ausgeschrieben in der Schlussrechnungs-Datei und werden
 * nicht aus der Datei der Abschlagsrechnung nachgeschlagen (ADR-0001):
 * Zwischen beiden liegen Monate, in denen Dateien in Jahresordner wandern und
 * der Code sich ändert. Eine Rechnung, die sich beim Erzeugen auf fremde
 * Dateien verlässt, ist keine zweimal.
 *
 * [receivedOn] heißt hier wie auf der Abschlagsrechnung selbst: Es ist
 * dieselbe Tatsache, die Vereinnahmung. Fachlich ist die Angabe Pflicht, im
 * Modell aber optional – fehlt sie, soll der Calculator erklären dürfen, warum
 * ein nicht vereinnahmter Abschlag nicht abgesetzt wird, statt eine Meldung
 * über ein fehlendes YAML-Feld zu zeigen.
 */
@Serializable
data class Prepayment(
    @SerialName("rechnungsnummer") val invoiceNumber: String,
    @SerialName("rechnungsdatum") val issueDate: IsoDate,
    @SerialName("vereinnahmt-am") val receivedOn: IsoDate? = null,
    // Liste, weil ein Abschlag gemischt sein darf – etwa 19 % und 7 %.
    @SerialName("steueranteile") val taxShares: List<TaxShare>,
)

/**
 * Der Anteil eines Abschlags, der auf eine Steuerkategorie und einen Satz
 * entfällt – mit Bemessungsgrundlage und Steuerbetrag getrennt.
 *
 * Getrennt, weil die Schlussrechnung nach §14 Abs. 5 Satz 2 UStG das Entgelt
 * *und* die darauf entfallende Steuer absetzt. Ein Bruttobetrag allein ließe
 * sich nicht mehr auf die Steuerarten aufteilen (ADR-0002).
 */
@Serializable
data class TaxShare(
    @SerialName("steuerkategorie") val taxCategory: TaxCategory = TaxCategory.STANDARD,
    @SerialName("ust-satz") val vatRate: Percent,
    @SerialName("netto") val net: Money,
    // Redundant zu netto × satz – und genau deshalb da: Der Calculator rechnet
    // nach und findet den Zahlendreher (ADR-0001).
    @SerialName("ust") val vat: Money,
)

/**
 * Eine Skontostufe: Wer innerhalb von [days] Tagen ab Rechnungsdatum zahlt,
 * darf [rate] abziehen.
 *
 * Auf der Rechnung steht nur diese Bedingung, kein Skontobetrag. Die Frist in
 * Tagen und nicht als Datum, wie beim Zahlungsziel: Verschiebt sich das
 * Rechnungsdatum, bleibt sie richtig.
 */
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
    // EN 16931 BT-56, die Kontaktstelle des Käufers: eine Person oder eine
    // Abteilung ("Einkauf"). Freitext, und das Programm setzt nichts davor –
    // kein "z. Hd.", keine Anrede, denn das Geschlecht einer Person kennt die
    // Rechnungsdatei nicht.
    @SerialName("ansprechpartner") val contactPoint: String? = null,
    @SerialName("anschrift") val address: Address,
    // EN 16931 BT-46, die Kennung des Käufers, vom Verkäufer vergeben: die
    // Kundennummer. Freitext ohne Muster; eine Kundendatei gibt es nicht, die
    // Nummer steht in jeder Rechnungsdatei.
    @SerialName("kundennummer") val buyerIdentifier: String? = null,
    // EN 16931 BT-10: z. B. Bestellnummer oder Leitweg-ID des Kunden.
    @SerialName("referenz") val buyerReference: String? = null,
    // BT-48: Pflicht, sobald die Steuerschuld übergeht (Kategorie AE) oder
    // innergemeinschaftlich geliefert wird (K) – dann muss der Empfänger
    // umsatzsteuerlich identifizierbar sein.
    @SerialName("umsatzsteuer-id") val vatId: VatId? = null,
    // Der dokumentierte Nachweis der Bestätigungsabfrage. Pflicht, sobald ohne
    // Umsatzsteuer abgerechnet wird und der Kunde in der EU sitzt – siehe die
    // Regelgruppe VatIdCheckRules.
    @SerialName("ust-id-geprueft") val vatIdCheck: VatIdCheck? = null,
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
 *    Merkmal der Leistung, sondern Kontext – etwa "Nachtrag zu Bestellung 4711".
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
