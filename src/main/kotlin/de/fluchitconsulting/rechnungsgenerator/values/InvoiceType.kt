package de.fluchitconsulting.rechnungsgenerator.values

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Die Rechnungsart – EN 16931 BT-3, Codeliste UNTDID 1001.
 *
 * Sie trennt die fünf Dokumente, die dieses Programm kennt (die Begriffe stehen
 * in `CONTEXT.md`). Der Unterschied ist keine Formsache: Wer einen Abschlag wie
 * eine gewöhnliche Rechnung abrechnet und später noch einmal die Gesamtleistung,
 * weist die Umsatzsteuer zweimal aus und schuldet sie nach §14c Abs. 1 UStG
 * auch zweimal.
 *
 * [label] ist die Schreibweise in der YAML, [code] die Kennung fürs spätere
 * ZUGFeRD-XML.
 *
 * Die drei Eigenschaften danach haben bewusst **keine** Vorgabewerte (ADR-0009):
 * Eine neue Rechnungsart – etwa die Rechnungskorrektur aus #64 – muss sagen, ob
 * ihr Zeitraum eine Prognose ist, ob sie Geld fordert und welcher Satz auf ihr
 * stehen muss. Mit Vorgaben erbte sie still die Antworten der gewöhnlichen
 * Rechnung, und Renderer und Calculator folgten ihnen, ohne dass je jemand
 * darüber entschieden hätte. Ohne Vorgaben übersetzt der neue Eintrag erst,
 * wenn alle drei dastehen.
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
    val servicePeriodIsForecast: Boolean,
    /**
     * Rechnet dieses Dokument eine Leistung ab?
     *
     * So steht die Rechnung im Glossar, und das Storno tut es nicht: Es nimmt
     * eine Abrechnung zurück und kündigt eine Erstattung an. Daraus folgen die
     * Sechs-Monats-Frist des §14 Abs. 2 UStG (die gilt dem Ausstellen einer
     * Rechnung, nicht dem Aufheben) und der Hinweis auf eine alternde
     * Bestätigungsabfrage (die musste zur Ursprungsrechnung passen, nicht zum
     * Storno).
     *
     * Ob das Dokument auch zur Zahlung auffordert – mit Zahlungsziel und
     * Bankverbindung –, sagt erst `CalculatedInvoice.requestsPayment`: Dafür
     * muss zusätzlich ein positiver Restbetrag bleiben. Eine voll abgesetzte
     * Schlussrechnung rechnet ab, ohne Geld zu fordern (#94), und ein negativer
     * Betrag fordert nie etwas (#130).
     *
     * Nicht dasselbe wie "ist ein Storno": Das Vorzeichen des Gesamtbetrags
     * hängt an der Rechnungsart selbst und fragt deshalb nach ihr.
     */
    val chargesForService: Boolean,
    /**
     * Ein Satz, der auf diesem Dokument stehen muss, damit es als das erkennbar
     * ist, was es ist. Hier steht der Wortlaut, nicht seine Gestalt: Wo er auf
     * der Seite landet, entscheidet die Vorlage.
     */
    val mandatoryNote: String?,
) {
    // Benannte Argumente, obwohl die Reihenfolge eindeutig ist: Ein nacktes
    // `false, true, null` ließe sich nur mit der Parameterliste daneben lesen.
    INVOICE(
        "rechnung",
        "380",
        servicePeriodIsForecast = false,
        chargesForService = true,
        mandatoryNote = null,
    ),

    // Der Code heißt in der Liste "Prepayment invoice"; das Wort dieses
    // Projekts ist trotzdem die Abschlagsrechnung – "Anzahlung" steht in
    // CONTEXT.md unter Vermeiden.
    PREPAYMENT(
        "abschlagsrechnung",
        "386",
        servicePeriodIsForecast = true,
        chargesForService = true,
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
    FINAL(
        "schlussrechnung",
        "380",
        servicePeriodIsForecast = false,
        chargesForService = true,
        mandatoryNote = null,
    ),

    // Ändert nur den Code und nichts an der Rechnung. Aufgenommen, damit die
    // Unterscheidung zur Abschlagsrechnung im Modell sichtbar ist statt nur im
    // Kopf des Rechnungsstellers: Nicht die Höhe trennt beide, sondern ob die
    // Leistung erbracht ist.
    PARTIAL(
        "teilrechnung",
        "326",
        servicePeriodIsForecast = false,
        chargesForService = true,
        mandatoryNote = null,
    ),

    // 384 ("Corrected invoice") und nicht 381: Die KoSIT führt 384 für Korrektur
    // und Storno mit Bezug auf die Ursprungsrechnung, 381 für die kaufmännische
    // Gutschrift ohne Bezug und 389 für die Gutschrift im Sinne des §14 Abs. 2
    // Satz 2 UStG (ADR-0006). Der Pflichtsatz nennt Nummer und Datum der
    // Ursprungsrechnung nicht: Sie stehen als eigene Angabe auf dem Blatt, weil
    // EN 16931 sie als Struktur führt (BG-3) und nicht als Prosa. Er sagt auch
    // nicht, *wo* sie stehen – das entscheidet die Vorlage.
    CANCELLATION(
        "stornorechnung",
        "384",
        servicePeriodIsForecast = false,
        chargesForService = false,
        mandatoryNote =
            "Diese Stornorechnung hebt die genannte Ursprungsrechnung vollständig auf. " +
                "Ein bereits gezahlter Betrag wird erstattet.",
    ),
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
}

object InvoiceTypeSerializer :
    KSerializer<InvoiceType> by enumSerializer(
        name = "InvoiceType",
        values = InvoiceType.entries,
        unknown = "Unbekannte Rechnungsart",
        normalize = { it.trim().lowercase() },
        keyOf = InvoiceType::label,
    )
