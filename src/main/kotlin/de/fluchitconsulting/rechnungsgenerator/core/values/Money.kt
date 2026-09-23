package de.fluchitconsulting.rechnungsgenerator.core.values

import de.fluchitconsulting.rechnungsgenerator.core.serialization.parsedNumber
import de.fluchitconsulting.rechnungsgenerator.core.serialization.stringSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
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

    operator fun unaryMinus(): Money = Money(value.negate())

    // Der Betrag ohne Vorzeichen. Steht hier, weil das ein Begriff jeder Zahl ist
    // und nicht einer dessen, der ihn braucht.
    fun abs(): Money = Money(value.abs())

    // Betrag × Steuersatz: Das Ergebnis ist selbst wieder ein Betrag (BT-117, die
    // USt. je Steuersatz) und darf deshalb gerundet werden.
    operator fun times(percent: Percent): Money = Money(value.multiply(percent.valueAsMultiplier))

    // --- Prädikate ------------------------------------------------------------

    // Privat: Draußen wird nach isNegative, isZero oder isPositive gefragt,
    // nicht nach einem Vorzeichen als Zahl.
    private fun signum(): Int = value.signum()

    fun isNegative(): Boolean = signum() < 0

    fun isZero(): Boolean = signum() == 0

    fun isPositive(): Boolean = signum() > 0

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
        decode = { raw -> parsedNumber(raw, "Geldbetrag", ::readMoney) },
    )

/**
 * Gelesen wird nur, was sich auf Cent genau schreiben lässt (#232).
 *
 * Der Konstruktor rundet HALF_UP, und das ist für *gerechnete* Werte richtig:
 * `base * percent` liefert zwangsläufig mehr Stellen, und die Rundungsregel
 * soll im Typ wohnen. Eine dritte Nachkommastelle in der *Eingabe* verschwände
 * damit aber ohne ein Wort – und die ist ein Tippfehler, keine Genauigkeit, die
 * jemand ausdrücken wollte. Abgewiesen wird deshalb, was jemand geschrieben
 * hat, nicht, was das Programm rechnet.
 *
 * `stripTrailingZeros` davor, weil `200.500` nichts verliert: Geprüft wird, ob
 * das Runden den Wert ändern würde, nicht, wie viele Nullen jemand getippt hat.
 * [Price] kennt die Prüfung nicht – der Einzelpreis (BT-146) darf beliebig
 * viele Stellen tragen.
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
