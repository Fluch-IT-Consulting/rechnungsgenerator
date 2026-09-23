package de.fluchitconsulting.rechnungsgenerator.core.values

import de.fluchitconsulting.rechnungsgenerator.core.serialization.enumSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Die Maßeinheit einer Position – EN 16931 BT-130.
 *
 * Bewusst eine geschlossene Menge statt Freitext: Die Norm verlangt einen Code
 * aus UN/ECE-Empfehlung 20 (Regel BR-CL-23), und die Angabe ist Pflicht
 * (BR-23). Ein Freitextfeld würde den Fehler erst beim Empfänger zeigen; so
 * fällt ein Tippfehler wie "Stnd." schon beim Einlesen auf.
 *
 * [label] ist die Schreibweise in der YAML und im PDF, [code] die Kennung fürs
 * spätere ZUGFeRD-XML. Die Liste deckt ab, was in einer Beratungsrechnung
 * vorkommt; weitere Einheiten sind eine Zeile.
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
