package de.fluchitconsulting.rechnungsgenerator.core.values

import de.fluchitconsulting.rechnungsgenerator.core.serialization.enumSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * BT-130 als Code nach UN/ECE Rec. 20 (BR-CL-23): Ein Tippfehler wie „Stnd.“ fällt so schon beim Einlesen auf.
 * [label] ist die Schreibweise in der YAML, in jeder Sprache.
 */
@Serializable(with = UnitSerializer::class)
enum class Unit(val label: String, val code: String) {
    HOUR("Std.", "HUR"),
    DAY("Tage", "DAY"),
    WEEK("Wochen", "WEE"),
    MONTH("Monate", "MON"),
    PIECE("Stück", "H87"),

    // LS und nicht C62 („one“), das nicht sagt, dass es eine Pauschale ist.
    FLAT_RATE("pauschal", "LS"),
    KILOMETRE("km", "KMT"),
    ;

    /**
     * Einzahl nur bei genau einer Einheit, auch `0,5 Tage`. Nach dem Betrag, damit eine negative Menge dasselbe Wort
     * bekommt, und mit `compareTo`, weil `equals` bei `1.00` die Skala mitvergleicht.
     */
    fun nameFor(quantity: BigDecimal, language: Language): String {
        val one = quantity.abs().compareTo(BigDecimal.ONE) == 0
        return when (language) {
            Language.GERMAN -> {
                when (this) {
                    DAY -> if (one) "Tag" else label
                    WEEK -> if (one) "Woche" else label
                    MONTH -> if (one) "Monat" else label
                    HOUR, PIECE, FLAT_RATE, KILOMETRE -> label
                }
            }
            Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> {
                when (this) {
                    HOUR -> if (one) "hr" else "hrs"
                    DAY -> if (one) "day" else "days"
                    WEEK -> if (one) "week" else "weeks"
                    MONTH -> if (one) "month" else "months"
                    PIECE -> if (one) "pc" else "pcs"
                    FLAT_RATE -> "flat rate"
                    KILOMETRE -> "km"
                }
            }
        }
    }

    override fun toString(): String = label
}

object UnitSerializer :
    KSerializer<Unit> by enumSerializer(
        name = "Unit",
        values = Unit.entries,
        unknown = "Unbekannte Einheit",
        normalize = { it.trim().lowercase() },
        keyOf = Unit::label,
    )
