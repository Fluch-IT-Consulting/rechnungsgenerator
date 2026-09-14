package de.fluchitconsulting.rechnungsgenerator.values

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Geldbetrag als Value Object. Intern immer auf 2 Nachkommastellen normalisiert
 * (kaufmännisch, HALF_UP) – die Rundungsregel wohnt damit im Typ, nicht in den Aufrufern.
 * Das entspricht den EN-16931-Regeln BR-DEC-*, die für alle Betragsfelder genau zwei
 * Nachkommastellen vorschreiben.
 *
 * Für Werte, die *nicht* gerundet werden dürfen – allen voran der Einzelpreis
 * (BT-146) – gibt es [Price]. Der Weg von dort hierher heißt [Price.roundToAmount].
 *
 * Übernommen aus dem CreditCalculator2-Projekt und dort inhaltlich unverändert:
 * derselbe Name bedeutet in beiden Projekten dasselbe.
 */
@Serializable(with = MoneySerializer::class)
class Money(value: BigDecimal) : Comparable<Money> {
    val value: BigDecimal = value.setScale(2, RoundingMode.HALF_UP)

    constructor(value: String) : this(BigDecimal(value))

    // --- Arithmetik (Operatoren: +, -, *) -------------------------------------
    // Zwei Operationen fehlen hier bewusst und sollen nicht kompilieren:
    //   Betrag × Betrag  – fachlich sinnlos.
    //   Betrag × Menge   – wäre der Schleichweg an [Price] vorbei: Der Einzelpreis
    //                      wäre schon auf Cent gerundet, bevor die Menge ihn
    //                      vervielfacht. Bei 0,335 € × 1000 sind das 5 € Fehler.

    operator fun plus(other: Money): Money = Money(value.add(other.value))

    operator fun minus(other: Money): Money = Money(value.subtract(other.value))

    // Betrag × Steuersatz: Das Ergebnis ist selbst wieder ein Betrag (BT-117, die
    // USt. je Steuersatz) und darf deshalb gerundet werden.
    operator fun times(percent: Percent): Money = Money(value.multiply(percent.valueAsMultiplier))

    // --- Prädikate ------------------------------------------------------------

    fun isZero(): Boolean = signum() == 0

    // Privat: Draußen wird nach isZero oder isNegative gefragt, nicht nach
    // einem Vorzeichen als Zahl.
    private fun signum(): Int = value.signum()

    fun isNegative(): Boolean = signum() < 0

    // Ohne Sonderfall für die Null: BigDecimal kennt kein negatives Vorzeichen der Null,
    override operator fun compareTo(other: Money): Int = value.compareTo(other.value)

    // Wertgleichheit statt Identität – bewusst über compareTo, damit die Gleichheit
    // rein numerisch ist und NICHT an der scale-sensitiven BigDecimal.equals hängt.
    // hashCode muss dazu passen: stripTrailingZeros() liefert eine kanonische,
    // scale-unabhängige Form, sodass gleiche Beträge denselben HashCode ergeben.
    override fun equals(other: Any?): Boolean = other is Money && compareTo(other) == 0

    override fun hashCode(): Int = value.stripTrailingZeros().hashCode()

    // Debug-Darstellung: locale-unabhängig und ohne Währung. Deutsches Zahlenformat
    // und "€" sind Sache der Ausgabeschicht (LaTeX-Renderer), nicht des Domänentyps.
    override fun toString(): String = value.toPlainString()

    companion object {
        val ZERO = Money(BigDecimal.ZERO)
    }
}

// Verlängert die stdlib-Konvention (sumOf/sum gibt es dort nur für eingebaute
// Zahlentypen) auf Money. `inline` kopiert das Lambda in die Aufrufstelle –
// stdlib-Gewohnheit bei kleinen Helfern mit Funktionsparameter.
inline fun <T> Iterable<T>.sumOf(selector: (T) -> Money): Money = fold(Money.ZERO) { acc, element -> acc + selector(element) }

// YAML ↔ Money: "95.00" ↔ Money("95.00"). Als String-Primitive serialisiert,
// damit nie Fließkomma-Parsing dazwischenfunkt.
object MoneySerializer :
    KSerializer<Money> by stringSerializer(
        name = "Money",
        encode = { it.value.toPlainString() },
        decode = { raw -> parsedNumber(raw, "Geldbetrag", ::Money) },
    )
