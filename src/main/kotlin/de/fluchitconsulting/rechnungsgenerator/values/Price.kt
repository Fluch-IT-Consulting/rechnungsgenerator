package de.fluchitconsulting.rechnungsgenerator.values

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * Exakter Geldwert, der nie von sich aus rundet – EN 16931 BT-146 (Einzelpreis),
 * BT-147 (Preisnachlass), BT-148 (Bruttopreis).
 *
 * Die Norm lässt bei Preisen beliebig viele Nachkommastellen zu; erst die daraus
 * berechneten *Beträge* sind auf zwei beschränkt (Regeln BR-DEC-*). Genau diese
 * Grenze bilden Price und [Money] ab.
 *
 * Bewusst KEIN Verwandtschaftsverhältnis zu [Money]: Bei Value Objects bricht eine
 * Unterklasse die Symmetrie von equals – a.equals(b) wäre dann nicht mehr dasselbe
 * wie b.equals(a). Die beiden Typen sind gleichrangig, verbunden nur durch den
 * einen ausdrücklichen Übergang [roundToAmount].
 */
@Serializable(with = PriceSerializer::class)
class Price(val value: BigDecimal) : Comparable<Price> {
    constructor(value: String) : this(BigDecimal(value))

    // --- Arithmetik -----------------------------------------------------------
    // Alles bleibt exakt: Es gibt in diesem Typ keine einzige Rundung.

    // Menge × Einzelpreis. Ergibt wieder einen exakten Wert, keinen Betrag –
    // gerundet wird erst beim Übergang, siehe roundToAmount().
    operator fun times(quantity: BigDecimal): Price = Price(value.multiply(quantity))

    /**
     * Der einzige Weg von einem exakten Preis zu einem Betrag. Hier – und nur hier –
     * wird kaufmännisch auf Cent gerundet, weil [Money] das in seinem Konstruktor tut.
     *
     * Dass diese Umwandlung explizit dasteht, ist der Sinn der beiden Typen: Der
     * Compiler lässt keinen Preis dort durch, wo ein Betrag verlangt ist.
     */
    fun roundToAmount(): Money = Money(value)

    fun isZero(): Boolean = value.signum() == 0

    override operator fun compareTo(other: Price): Int = value.compareTo(other.value)

    // Wertgleichheit wie bei Money: numerisch über compareTo, damit "0.10" und
    // "0.100" gleich sind; hashCode über die kanonische Form dazu passend.
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
