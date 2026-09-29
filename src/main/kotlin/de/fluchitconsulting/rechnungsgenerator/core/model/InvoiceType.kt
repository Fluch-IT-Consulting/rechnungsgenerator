package de.fluchitconsulting.rechnungsgenerator.core.model

import de.fluchitconsulting.rechnungsgenerator.core.serialization.enumSerializer
import de.fluchitconsulting.rechnungsgenerator.core.text.TextTemplate
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Die Rechnungsart, BT-3 nach UNTDID 1001; die Begriffe und ihre englischen Namen stehen in `CONTEXT.md`.
 * Bewusst ohne Vorgabewerte (ADR-0009): Eine neue Art erbte sonst still die Antworten der gewöhnlichen Rechnung.
 */
@Serializable(with = InvoiceTypeSerializer::class)
enum class InvoiceType(
    val label: String,
    val code: String,
    /** Auf einer Abschlagsrechnung ist die Leistung noch nicht erbracht (UStAE 14.8). */
    val servicePeriodIsForecast: Boolean,
    /**
     * Rechnet das Dokument eine Leistung ab? Das Storno nicht. Ob es auch Geld fordert, sagt erst
     * `CalculatedInvoice.requestsPayment`.
     */
    val chargesForService: Boolean,
    /** Werte als `<<NAME>>` aus [InvoiceVariable]; ein vertippter Name wirft beim Laden der Klasse (ADR-0019). */
    private val germanMandatoryNote: TextTemplate<InvoiceVariable>?,
    /** Derselbe Satz; im selben Eintrag, damit wer den einen ändert, den anderen vor Augen hat. */
    private val englishMandatoryNote: TextTemplate<InvoiceVariable>?,
) {
    INVOICE(
        "rechnung",
        "380",
        servicePeriodIsForecast = false,
        chargesForService = true,
        germanMandatoryNote = null,
        englishMandatoryNote = null,
    ),

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
        // „provided“, nicht „supplied“ (ADR-0025).
        englishMandatoryNote =
            TextTemplate.parse(
                "The service has not yet been provided. A final invoice for the complete service will follow, " +
                    "in which this advance payment will be deducted.",
            ),
    ),

    // 380 wie die Rechnung: Sie *ist* die Rechnung über die Gesamtleistung. Nicht 384, sie korrigiert nichts,
    // und nicht die Bau-Codes 875 bis 877, die für andere Leistungen falsch wären.
    FINAL(
        "schlussrechnung",
        "380",
        servicePeriodIsForecast = false,
        chargesForService = true,
        germanMandatoryNote = null,
        englishMandatoryNote = null,
    ),

    // Ändert nur den Code. Sie trennt sichtbar von der Abschlagsrechnung: nicht die Höhe, sondern ob erbracht ist.
    PARTIAL(
        "teilrechnung",
        "326",
        servicePeriodIsForecast = false,
        chargesForService = true,
        germanMandatoryNote = null,
        englishMandatoryNote = null,
    ),

    // 384 und nicht 381 (ADR-0006).
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
        // Das Datum am Satzende: Mitten im Satz verlangte das Amerikanische nach dem Jahr ein Komma (#407).
        englishMandatoryNote =
            TextTemplate.parse(
                "This cancellation invoice fully cancels invoice <<URSPRUNGSNUMMER>> dated <<URSPRUNGSDATUM>>. " +
                    "Any amount already paid will be refunded.",
            ),
    ),
    ;

    fun mandatoryNote(language: Language): TextTemplate<InvoiceVariable>? =
        when (language) {
            Language.GERMAN -> germanMandatoryNote
            Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> englishMandatoryNote
        }

    /** Die Beschriftung des Zeitraums der ganzen Rechnung. */
    fun servicePeriodLabel(language: Language): String =
        when (language) {
            Language.GERMAN -> {
                if (servicePeriodIsForecast) "Voraussichtlicher Leistungszeitraum" else "Leistungszeitraum"
            }
            Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> {
                if (servicePeriodIsForecast) "Expected service period" else "Service period"
            }
        }

    /** Kürzer, für den Zeitraum einer Position (BG-26). */
    fun itemPeriodLabel(language: Language): String =
        when (language) {
            Language.GERMAN -> {
                if (servicePeriodIsForecast) "Voraussichtlicher Zeitraum" else "Zeitraum"
            }
            Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> {
                if (servicePeriodIsForecast) "Expected period" else "Period"
            }
        }

    /** Deutsch in jeder Sprache: für die Meldungen und den Jobnamen, an dem die DocumentID hängt (ADR-0005). */
    val documentName: String get() = label.replaceFirstChar { it.uppercase() }

    /** Auf dem Blatt und in den Metadaten. */
    fun documentName(language: Language): String =
        when (language) {
            Language.GERMAN -> {
                documentName
            }
            // Die Begriffe und was sie meiden, stehen im Glossar.
            Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> {
                when (this) {
                    INVOICE -> "Invoice"
                    PARTIAL -> "Partial invoice"
                    CANCELLATION -> "Cancellation invoice"
                    PREPAYMENT -> "Advance payment invoice"
                    FINAL -> "Final invoice"
                }
            }
        }

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
