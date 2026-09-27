package de.fluchitconsulting.rechnungsgenerator.core.values

import de.fluchitconsulting.rechnungsgenerator.core.serialization.enumSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * Die Maßeinheit einer Position – EN 16931 BT-130.
 *
 * Bewusst eine geschlossene Menge statt Freitext: Die Norm verlangt einen Code
 * aus UN/ECE-Empfehlung 20 (Regel BR-CL-23), und die Angabe ist Pflicht
 * (BR-23). Ein Freitextfeld würde den Fehler erst beim Empfänger zeigen; so
 * fällt ein Tippfehler wie "Stnd." schon beim Einlesen auf.
 *
 * [label] ist die Schreibweise in der YAML, eine je Einheit und unabhängig von
 * der Menge; [nameFor] der Text auf dem Blatt, [code] die Kennung fürs
 * ZUGFeRD-XML. Die Liste deckt ab, was in einer Beratungsrechnung vorkommt.
 */
@Serializable(with = UnitSerializer::class)
enum class Unit(val label: String, val code: String) {
    HOUR("Std.", "HUR"),
    DAY("Tage", "DAY"),
    WEEK("Wochen", "WEK"),
    MONTH("Monate", "MON"),
    PIECE("Stück", "H87"),

    // LS = lump sum. C62 ("one") wäre die neutrale Alternative, benennt aber
    // nicht, dass es sich um eine Pauschale handelt.
    FLAT_RATE("pauschal", "LS"),
    KILOMETRE("km", "KMT"),
    ;

    /**
     * Der Name auf dem Blatt, in [language]: bei genau einer Einheit die
     * Einzahl, sonst die Mehrzahl, auch bei einem Bruchteil – `0.5 days` wie
     * `0,5 Tage`. Der Betrag zählt und nicht das Vorzeichen, damit eine
     * negative Menge dasselbe Wort bekommt; verglichen mit `compareTo`, weil
     * `equals` bei `1.00` die Skala mitvergleicht.
     *
     * Ein `when` ohne `else` über Sprache und Einheit: Eine neue Einheit muss
     * in jeder Sprache sagen, ob sie eine Einzahl hat. Die Schreibweise in der
     * YAML bleibt [label], auch auf einer englischen Rechnung.
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
