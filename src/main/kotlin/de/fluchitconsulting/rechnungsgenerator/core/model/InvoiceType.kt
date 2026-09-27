package de.fluchitconsulting.rechnungsgenerator.core.model

import de.fluchitconsulting.rechnungsgenerator.core.serialization.enumSerializer
import de.fluchitconsulting.rechnungsgenerator.core.text.TextTemplate
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
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
 * Eine neue Rechnungsart muss sagen, ob ihr Zeitraum eine Prognose ist, ob sie
 * Geld fordert und welcher Satz auf ihr stehen muss. Mit Vorgaben erbte sie
 * still die Antworten der gewöhnlichen Rechnung, und Renderer und Calculator
 * folgten ihnen, ohne dass je jemand darüber entschieden hätte. Ohne Vorgaben
 * übersetzt der neue Eintrag erst, wenn alle drei dastehen.
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
     *
     * Werte der Rechnung nennt er als Platzhalter `<<NAME>>`, aus
     * [InvoiceVariable]. Zerlegt wird der Satz schon hier, sodass ein
     * vertippter Name wirft, sobald diese Klasse geladen wird; eingesetzt
     * werden die Werte erst beim Setzen, und der Satz selbst bleibt ohne LaTeX.
     *
     * Der deutsche Wortlaut; welcher auf der Rechnung steht, sagt
     * [mandatoryNote] mit ihrer Sprache.
     */
    private val germanMandatoryNote: TextTemplate<InvoiceVariable>?,
) {
    // Benannte Argumente, obwohl die Reihenfolge eindeutig ist: Ein nacktes
    // `false, true, null` ließe sich nur mit der Parameterliste daneben lesen.
    INVOICE(
        "rechnung",
        "380",
        servicePeriodIsForecast = false,
        chargesForService = true,
        germanMandatoryNote = null,
    ),

    // Der Code heißt in der Liste "Prepayment invoice"; das Wort dieses
    // Projekts ist trotzdem die Abschlagsrechnung – "Anzahlung" steht in
    // CONTEXT.md unter Vermeiden.
    PREPAYMENT(
        "abschlagsrechnung",
        "386",
        servicePeriodIsForecast = true,
        chargesForService = true,
        germanMandatoryNote =
            TextTemplate.parse(
                "Die Leistung ist noch nicht erbracht. Über die Gesamtleistung folgt eine " +
                    "Schlussrechnung, in der dieser Abschlag abgesetzt wird.",
            ),
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
        germanMandatoryNote = null,
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
        germanMandatoryNote = null,
    ),

    // 384 ("Corrected invoice") und nicht 381: Die KoSIT führt 384 für Korrektur
    // und Storno mit Bezug auf die Ursprungsrechnung, 381 für die kaufmännische
    // Gutschrift ohne Bezug und 389 für die Gutschrift im Sinne des §14 Abs. 2
    // Satz 2 UStG (ADR-0006). Der Pflichtsatz nennt Nummer und Datum der
    // Ursprungsrechnung als Platzhalter, eingesetzt beim Setzen. Im Modell
    // bleibt der Bezug trotzdem Struktur (BG-3): Ein XML-Export liest ihn dort
    // und nicht aus diesem Satz.
    CANCELLATION(
        "stornorechnung",
        "384",
        servicePeriodIsForecast = false,
        chargesForService = false,
        germanMandatoryNote =
            TextTemplate.parse(
                "Diese Stornorechnung hebt die Rechnung <<URSPRUNGSNUMMER>> vom <<URSPRUNGSDATUM>> vollständig auf. " +
                    "Ein bereits gezahlter Betrag wird erstattet.",
            ),
    ),
    ;

    /**
     * Der Pflichtsatz in [language], oder `null`, wenn diese Art keinen hat.
     *
     * Ohne `else`, über Sprache und Art: Eine neue Sprache übersetzt erst,
     * wenn jede Art ihren Satz hat oder ausdrücklich keinen. Englisch hat
     * den Abschlag noch nicht – sein Wortlaut sagt etwas Rechtliches und
     * wird in einem eigenen Schritt entschieden (#405).
     */
    fun mandatoryNote(language: Language): TextTemplate<InvoiceVariable>? =
        when (language) {
            Language.GERMAN -> {
                germanMandatoryNote
            }
            Language.BRITISH_ENGLISH -> {
                when (this) {
                    INVOICE, FINAL, PARTIAL -> null
                    CANCELLATION -> ENGLISH_CANCELLATION_NOTE
                    PREPAYMENT -> language.notYet("Den Pflichtsatz der $documentName")
                }
            }
        }

    /** Die Beschriftung des Zeitraums der ganzen Rechnung. */
    fun servicePeriodLabel(language: Language): String =
        when (language) {
            Language.GERMAN -> {
                if (servicePeriodIsForecast) "Voraussichtlicher Leistungszeitraum" else "Leistungszeitraum"
            }
            // "Service period" und nicht "Period of supply" oder "Tax point":
            // Beide sind im britischen VAT eigene Begriffe.
            Language.BRITISH_ENGLISH -> {
                if (servicePeriodIsForecast) language.notYet("Den voraussichtlichen Leistungszeitraum") else "Service period"
            }
        }

    /**
     * Dieselbe Beschriftung, kürzer: für den abweichenden Zeitraum einer
     * einzelnen Position (BG-26). Sie steht klein unter der Bezeichnung, wo die
     * lange Fassung die Zeile sprengte.
     */
    fun itemPeriodLabel(language: Language): String =
        when (language) {
            Language.GERMAN -> {
                if (servicePeriodIsForecast) "Voraussichtlicher Zeitraum" else "Zeitraum"
            }
            Language.BRITISH_ENGLISH -> {
                if (servicePeriodIsForecast) language.notYet("Den voraussichtlichen Zeitraum") else "Period"
            }
        }

    /**
     * Der Name des Dokuments, wie CONTEXT.md ihn führt – für die Meldungen an
     * den Rechnungssteller und den Jobnamen. Deutsch in jeder Sprache: Die
     * Meldungen sind es, und am Jobnamen hängt die DocumentID (ADR-0005), die
     * nicht an der Sprache hängen soll.
     *
     * Abgeleitet statt als eigenes Feld geführt: Er ist das Label mit großem
     * Anfangsbuchstaben, und zwei Felder mit derselben Aussage laufen
     * irgendwann auseinander.
     */
    val documentName: String get() = label.replaceFirstChar { it.uppercase() }

    /** Der Name des Dokuments auf dem Blatt und in den Metadaten, in [language]. */
    fun documentName(language: Language): String =
        when (language) {
            Language.GERMAN -> {
                documentName
            }
            // "Invoice" und nicht "Bill" (CONTEXT.md). "Partial invoice", wie
            // Code 326 in der Codeliste heißt – nicht "Interim invoice" oder
            // "Progress invoice", die beide nach einem Abschlag klingen.
            // "Cancellation invoice" und nicht "Credit note": So heißt Code
            // 381, den das Storno bewusst nicht trägt (ADR-0006). Die übrigen
            // Arten bekommen ihren Namen mit ihrem Schritt.
            Language.BRITISH_ENGLISH -> {
                when (this) {
                    INVOICE -> "Invoice"
                    PARTIAL -> "Partial invoice"
                    CANCELLATION -> "Cancellation invoice"
                    PREPAYMENT, FINAL -> language.notYet("Den Namen der $documentName")
                }
            }
        }

    override fun toString(): String = label

    companion object {
        // Derselbe Satz wie der deutsche, Glied für Glied: vollständig
        // aufgehoben, und nur ein bereits gezahlter Betrag wird erstattet.
        // "dated" ist die britische Wendung für "vom". Im Begleitobjekt und
        // nicht beim Aufruf zerlegt, damit ein vertippter Name wirft, sobald
        // die Klasse geladen wird – wie beim deutschen Satz.
        private val ENGLISH_CANCELLATION_NOTE =
            TextTemplate.parse<InvoiceVariable>(
                "This cancellation invoice cancels invoice <<URSPRUNGSNUMMER>> dated <<URSPRUNGSDATUM>> in full. " +
                    "Any amount already paid will be refunded.",
            )
    }
}

object InvoiceTypeSerializer :
    KSerializer<InvoiceType> by enumSerializer(
        name = "InvoiceType",
        values = InvoiceType.entries,
        unknown = "Unbekannte Rechnungsart",
        normalize = { it.trim().lowercase() },
        keyOf = InvoiceType::label,
    )
