package de.fluchitconsulting.rechnung.values

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Steuersatz/Prozentwert. Intern als Multiplikator (Prozent / 100) mit Scale 9 abgelegt,
 * d. h. 19 % -> 0.190000000. Damit ist der Rohwert direkt für Money.times() nutzbar.
 *
 * Übernommen aus dem CreditCalculator2-Projekt, um die dortige Kreditrechner-Fachlichkeit
 * (subdivide, of) gekürzt und um Percent × Percent sowie Serialisierung ergänzt.
 */
@Serializable(with = PercentSerializer::class)
class Percent(value: BigDecimal) {
    private val value: BigDecimal = value.multiply(HUNDREDTH).setScale(9, RoundingMode.HALF_UP)

    constructor(value: String) : this(BigDecimal(value))

    // Zwei Lesarten desselben Werts, beide benannt: der Multiplikator ist das, was
    // Money.times() konsumiert; die Prozent-Schreibweise ist die fachliche Form.
    val valueAsMultiplier: BigDecimal get() = value

    /** Der Wert in Prozent-Schreibweise: 19 % -> `19.0000000` (Scale 7 = Scale 9 im Multiplikator). */
    val valueAsPercentage: BigDecimal get() = value.movePointRight(2)

    // Value-Object-Hygiene wie bei Money: numerische Gleichheit über compareTo,
    // scale-unabhängiger hashCode.
    override fun equals(other: Any?): Boolean = other is Percent && value.compareTo(other.value) == 0

    override fun hashCode(): Int = value.stripTrailingZeros().hashCode()

    // Debug-Darstellung: locale-unabhängig, Prozentzeichen direkt am Wert.
    // Die deutsche Report-Form ("19 %") macht die Ausgabeschicht.
    //
    // stripTrailingZeros wie beim Serializer daneben: Intern rechnet Percent
    // mit sieben Nachkommastellen, in einer Fehlermeldung liest das aber
    // niemand als Genauigkeit. Wer "angegeben ist 19.0000000%" liest, sucht den
    // Fehler zuerst bei den Nullen statt bei seinem Steuersatz – die Meldung
    // lenkt dann von sich selbst ab.
    override fun toString(): String = "${valueAsPercentage.stripTrailingZeros().toPlainString()}%"

    companion object {
        private val HUNDREDTH = BigDecimal("0.01")
    }
}

// YAML ↔ Percent in Prozent-Schreibweise: "19" ↔ Percent("19").
// stripTrailingZeros beim Schreiben, damit nicht "19.0000000" in der YAML landet.
object PercentSerializer :
    KSerializer<Percent> by stringSerializer(
        name = "Percent",
        encode = { it.valueAsPercentage.stripTrailingZeros().toPlainString() },
        decode = { raw -> parsedNumber(raw, "Prozentwert", ::Percent) },
    )
