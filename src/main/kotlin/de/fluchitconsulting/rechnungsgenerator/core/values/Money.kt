package de.fluchitconsulting.rechnungsgenerator.core.values

import de.fluchitconsulting.rechnungsgenerator.core.serialization.parsedNumber
import de.fluchitconsulting.rechnungsgenerator.core.serialization.stringSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Ein Betrag, auf zwei Nachkommastellen HALF_UP normalisiert (BR-DEC-*). Für den Einzelpreis gibt es [Price], der
 * einzige Weg von dort heißt [Price.roundToAmount].
 */
@Serializable(with = MoneySerializer::class)
class Money(value: BigDecimal) : Comparable<Money> {
    val value: BigDecimal = value.setScale(2, RoundingMode.HALF_UP)

    constructor(value: String) : this(BigDecimal(value))

    // Bewusst fehlen Betrag × Betrag, fachlich sinnlos, und Betrag × Menge: Das wäre der Schleichweg an [Price]
    // vorbei, bei 0,335 € × 1000 ein Fehler von 5 €.

    operator fun plus(other: Money): Money = Money(value.add(other.value))

    operator fun minus(other: Money): Money = Money(value.subtract(other.value))

    operator fun unaryMinus(): Money = Money(value.negate())

    fun abs(): Money = Money(value.abs())

    // BT-117: Das Ergebnis ist wieder ein Betrag und darf gerundet werden.
    operator fun times(percent: Percent): Money = Money(value.multiply(percent.valueAsMultiplier))

    private fun signum(): Int = value.signum()

    fun isNegative(): Boolean = signum() < 0

    fun isZero(): Boolean = signum() == 0

    fun isPositive(): Boolean = signum() > 0

    override operator fun compareTo(other: Money): Int = value.compareTo(other.value)

    // Gleichheit über compareTo, nicht über BigDecimal.equals, das die Skala mitvergleicht; hashCode passend dazu.
    override fun equals(other: Any?): Boolean = other is Money && compareTo(other) == 0

    override fun hashCode(): Int = value.stripTrailingZeros().hashCode()

    // Nur zum Debuggen: Schreibweise und Währung setzt die Vorlage (ADR-0021).
    override fun toString(): String = value.toPlainString()

    companion object {
        val ZERO = Money(BigDecimal.ZERO)
    }
}

inline fun <T> Iterable<T>.sumOf(selector: (T) -> Money): Money = fold(Money.ZERO) { acc, element -> acc + selector(element) }

// Als String, damit kein Fließkomma dazwischenkommt.
object MoneySerializer :
    KSerializer<Money> by stringSerializer(
        name = "Money",
        encode = { it.value.toPlainString() },
        decode = { raw -> parsedNumber(raw, "Geldbetrag", ::readMoney) },
    )

/**
 * Die Eingabe muss sich auf Cent genau schreiben lassen (#232): Der Konstruktor rundet, und eine dritte
 * Nachkommastelle in der Datei ist ein Tippfehler, der sonst still verschwände. `200.500` verliert nichts.
 */
private fun readMoney(raw: String): Money {
    val value = BigDecimal(raw)
    if (value.stripTrailingZeros().scale() > 2) {
        throw SerializationException(
            "Der Geldbetrag '$raw' hat mehr als zwei Nachkommastellen – Beträge stehen auf Cent genau.",
        )
    }
    return Money(value)
}
