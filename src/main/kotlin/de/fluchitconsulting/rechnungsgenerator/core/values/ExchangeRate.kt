package de.fluchitconsulting.rechnungsgenerator.core.values

import de.fluchitconsulting.rechnungsgenerator.core.serialization.parsedNumber
import de.fluchitconsulting.rechnungsgenerator.core.serialization.stringSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.math.BigDecimal
import java.math.RoundingMode

/** Wird geworfen, wenn ein Kurs nicht über null liegt. */
class InvalidExchangeRateException(message: String) : IllegalArgumentException(message)

/**
 * Ein Umrechnungskurs, notiert als Einheiten der Fremdwährung je Euro:
 * `1.1685` heißt 1 € = 1,1685 $.
 *
 * In dieser Richtung, weil BMF und EZB ihre Kurse so veröffentlichen – der
 * Wert wird abgeschrieben, und eine umgekehrte Notierung verlangte, ihn vorher
 * umzurechnen. Exakt wie [Price] und ohne Rundung: Gerundet wird erst der
 * umgerechnete Betrag.
 */
@Serializable(with = ExchangeRateSerializer::class)
class ExchangeRate(val value: BigDecimal) {
    init {
        if (value.signum() <= 0) {
            throw InvalidExchangeRateException("Kein gültiger Kurs: '${value.toPlainString()}' (erwartet eine Zahl über null)")
        }
    }

    constructor(value: String) : this(BigDecimal(value))

    /**
     * Ein Betrag der Fremdwährung in Euro, kaufmännisch auf Cent gerundet.
     * Geteilt wird mit genau einer Rundung: Ein Zwischenergebnis mit
     * begrenzter Stellenzahl rundete ein zweites Mal.
     */
    fun toEuro(amount: Money): Money = Money(amount.value.divide(value, 2, RoundingMode.HALF_UP))

    override fun equals(other: Any?): Boolean = other is ExchangeRate && value.compareTo(other.value) == 0

    override fun hashCode(): Int = value.stripTrailingZeros().hashCode()

    override fun toString(): String = value.toPlainString()
}

object ExchangeRateSerializer :
    KSerializer<ExchangeRate> by stringSerializer(
        name = "ExchangeRate",
        encode = { it.value.toPlainString() },
        decode = { raw ->
            try {
                parsedNumber(raw, "Kurs", ::ExchangeRate)
            } catch (e: InvalidExchangeRateException) {
                throw SerializationException(e.message, e)
            }
        },
    )
