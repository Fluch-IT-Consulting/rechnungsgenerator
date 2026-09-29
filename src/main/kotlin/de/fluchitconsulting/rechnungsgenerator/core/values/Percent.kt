package de.fluchitconsulting.rechnungsgenerator.core.values

import de.fluchitconsulting.rechnungsgenerator.core.serialization.parsedNumber
import de.fluchitconsulting.rechnungsgenerator.core.serialization.stringSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode

/** Intern als Multiplikator mit Skala 9: 19 % ist 0.190000000. */
@Serializable(with = PercentSerializer::class)
class Percent(value: BigDecimal) {
    private val value: BigDecimal = value.multiply(HUNDREDTH).setScale(9, RoundingMode.HALF_UP)

    constructor(value: String) : this(BigDecimal(value))

    val valueAsMultiplier: BigDecimal get() = value

    /** Der Wert in Prozent-Schreibweise: 19 % -> `19.0000000` (Scale 7 = Scale 9 im Multiplikator). */
    val valueAsPercentage: BigDecimal get() = value.movePointRight(2)

    // Gleichheit wie bei Money über compareTo.
    override fun equals(other: Any?): Boolean = other is Percent && value.compareTo(other.value) == 0

    override fun hashCode(): Int = value.stripTrailingZeros().hashCode()

    // stripTrailingZeros: „19.0000000%“ in einer Fehlermeldung lenkte vom eigentlichen Fehler ab.
    override fun toString(): String = "${valueAsPercentage.stripTrailingZeros().toPlainString()}%"

    companion object {
        private val HUNDREDTH = BigDecimal("0.01")
    }
}

// stripTrailingZeros, damit nicht „19.0000000“ in der YAML landet.
object PercentSerializer :
    KSerializer<Percent> by stringSerializer(
        name = "Percent",
        encode = { it.valueAsPercentage.stripTrailingZeros().toPlainString() },
        decode = { raw -> parsedNumber(raw, "Prozentwert", ::Percent) },
    )
