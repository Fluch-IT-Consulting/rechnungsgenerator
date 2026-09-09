package de.fluchitconsulting.rechnung.values

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Die Rechnungsart – EN 16931 BT-3, Codeliste UNTDID 1001.
 *
 * Sie trennt die vier Dokumente, die dieses Programm kennt (die Begriffe stehen
 * in `CONTEXT.md`). Der Unterschied ist keine Formsache: Wer einen Abschlag wie
 * eine gewöhnliche Rechnung abrechnet und später noch einmal die Gesamtleistung,
 * weist die Umsatzsteuer zweimal aus und schuldet sie nach §14c Abs. 1 UStG
 * auch zweimal.
 *
 * [label] ist die Schreibweise in der YAML, [code] die Kennung fürs spätere
 * ZUGFeRD-XML.
 */
@Serializable(with = InvoiceTypeSerializer::class)
enum class InvoiceType(
    val label: String,
    val code: String,
    /**
     * Ist der Leistungszeitraum eine Prognose statt einer Tatsache?
     *
     * Auf einer Abschlagsrechnung ist die Leistung noch nicht erbracht. Als
     * "Leistungszeitraum" beschriftet, behauptete der Zeitraum das Gegenteil –
     * und auf den Tag genau genannt eine Genauigkeit, die er nicht hat. UStAE
     * 14.8 Abs. 1 und 4 lassen dort den Kalendermonat genügen. Aus dieser einen
     * Eigenschaft folgen beide Beschriftungen und die Schreibweise des Datums.
     */
    val servicePeriodIsForecast: Boolean = false,
    /**
     * Ein Satz, der auf diesem Dokument stehen muss, damit es als das erkennbar
     * ist, was es ist. Hier steht der Wortlaut, nicht seine Gestalt: Wo er auf
     * der Seite landet, entscheidet die Vorlage.
     */
    val mandatoryNote: String? = null,
) {
    INVOICE("rechnung", "380"),

    // Der Code heißt in der Liste "Prepayment invoice"; das Wort dieses
    // Projekts ist trotzdem die Abschlagsrechnung – "Anzahlung" steht in
    // CONTEXT.md unter Vermeiden.
    PREPAYMENT(
        "abschlagsrechnung",
        "386",
        servicePeriodIsForecast = true,
        mandatoryNote =
            "Die Leistung ist noch nicht erbracht. Über die Gesamtleistung folgt eine " +
                "Schlussrechnung, in der dieser Abschlag abgesetzt wird.",
    ),

    // Für die Schlussrechnung nennt keine Quelle einen eigenen Code, deshalb
    // derselbe wie bei der gewöhnlichen Rechnung: Sie *ist* die Rechnung über
    // die Gesamtleistung, kenntlich an der Absetzung der Abschläge, nicht am
    // Dokumenttyp. Nicht 384 ("Corrected invoice" – eine Schlussrechnung
    // korrigiert nichts) und nicht die Bau-Codes 875/876/877, die für andere
    // Leistungen eine Falschangabe wären. Ein neutraler Code ist besser als
    // einer, der etwas Falsches behauptet.
    FINAL("schlussrechnung", "380"),

    // Ändert nur den Code und nichts an der Rechnung. Aufgenommen, damit die
    // Unterscheidung zur Abschlagsrechnung im Modell sichtbar ist statt nur im
    // Kopf des Rechnungsstellers: Nicht die Höhe trennt beide, sondern ob die
    // Leistung erbracht ist.
    PARTIAL("teilrechnung", "326"),
    ;

    /** Die Beschriftung des Zeitraums der ganzen Rechnung. */
    val servicePeriodLabel: String
        get() = if (servicePeriodIsForecast) "Voraussichtlicher Leistungszeitraum" else "Leistungszeitraum"

    /**
     * Dieselbe Beschriftung, kürzer: für den abweichenden Zeitraum einer
     * einzelnen Position (BG-26). Sie steht klein unter der Bezeichnung, wo die
     * lange Fassung die Zeile sprengte.
     */
    val itemPeriodLabel: String
        get() = if (servicePeriodIsForecast) "Voraussichtlicher Zeitraum" else "Zeitraum"

    /**
     * Der Name des Dokuments, wie CONTEXT.md ihn führt – für Titelbalken,
     * Dateiname und die XMP-Metadaten. Abgeleitet statt als eigenes Feld
     * geführt: Er ist das Label mit großem Anfangsbuchstaben, und zwei Felder
     * mit derselben Aussage laufen irgendwann auseinander.
     */
    val documentName: String get() = label.replaceFirstChar { it.uppercase() }

    override fun toString(): String = label

    companion object {
        private val LOOKUP = CodeLookup("Unbekannte Rechnungsart", entries, { it.trim().lowercase() }, InvoiceType::label)

        /** Löst die YAML-Schreibweise auf; unbekannte Arten nennen die zulässigen. */
        fun fromLabel(label: String): InvoiceType = LOOKUP.of(label)
    }
}

object InvoiceTypeSerializer :
    KSerializer<InvoiceType> by stringSerializer("InvoiceType", InvoiceType::label, InvoiceType::fromLabel)
