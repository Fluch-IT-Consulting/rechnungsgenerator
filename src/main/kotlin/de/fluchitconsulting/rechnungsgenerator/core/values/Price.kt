package de.fluchitconsulting.rechnungsgenerator.core.values

import de.fluchitconsulting.rechnungsgenerator.core.serialization.parsedNumber
import de.fluchitconsulting.rechnungsgenerator.core.serialization.stringSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * Ein exakter Preis, der nie rundet: BT-146, BT-147, BT-148 lassen beliebig viele Nachkommastellen zu, anders als
 * die Beträge (BR-DEC-*). Keine Verwandtschaft zu [Money]: Eine Unterklasse bräche die Symmetrie von `equals`.
 */
@Serializable(with = PriceSerializer::class)
class Price(val value: BigDecimal) : Comparable<Price> {
    constructor(value: String) : this(BigDecimal(value))

    // Keine einzige Rundung in diesem Typ.

    operator fun times(quantity: BigDecimal): Price = Price(value.multiply(quantity))

    /** Der einzige Übergang zu [Money]; gerundet wird dort. */
    fun roundToAmount(): Money = Money(value)

    override operator fun compareTo(other: Price): Int = value.compareTo(other.value)

    // Gleichheit wie bei Money über compareTo.
    override fun equals(other: Any?): Boolean = other is Price && compareTo(other) == 0

    override fun hashCode(): Int = value.stripTrailingZeros().hashCode()

    override fun toString(): String = value.toPlainString()
}

object PriceSerializer :
    KSerializer<Price> by stringSerializer(
        name = "Price",
        encode = { it.value.toPlainString() },
        decode = { raw -> parsedNumber(raw, "Preis", ::Price) },
    )
