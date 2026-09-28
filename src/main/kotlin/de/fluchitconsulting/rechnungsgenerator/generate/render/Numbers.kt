package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.values.ExchangeRate
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent
import de.fluchitconsulting.rechnungsgenerator.core.values.Price
import java.math.BigDecimal

/**
 * Zahlen für die Vorlage: die Ziffern, die auf das Blatt kommen, als Aufruf
 * von `\betrag`, `\menge` oder `\prozent` (ADR-0021).
 *
 * Wie sie aussehen – Dezimalkomma, Tausenderpunkt, Minuszeichen, Währungs- und
 * Prozentzeichen –, entscheidet die Vorlage mit siunitx. Hier entscheidet sich
 * nur, welche Ziffern es sind, und daran hängt, dass sich eine Rechnung
 * nachrechnen lässt (#14): Ein Einzelpreis von 0,00001 € kommt mit allen fünf
 * Nachkommastellen an und nicht als 0,00 €.
 */
object Numbers {
    /** Ein Betrag mit genau zwei Nachkommastellen: `\betrag{8337.50}` */
    fun amount(money: Money): Latex = Latex.macro(TemplateMacro.BETRAG, listOf(digits(money.value, minFraction = 2)))

    /**
     * Ein Einzelpreis mit mindestens zwei Nachkommastellen und allen, die der
     * Wert trägt (EN 16931 BT-146): `\betrag{0.335}`. Dasselbe Makro wie ein
     * Betrag – den Unterschied tragen schon die Ziffern.
     */
    fun price(price: Price): Latex = Latex.macro(TemplateMacro.BETRAG, listOf(digits(price.value, minFraction = 2)))

    /** Ein Betrag in Euro, gleich in welcher Währung die Rechnung steht: `\eurobetrag{1355.70}` */
    fun euroAmount(money: Money): Latex = Latex.macro(TemplateMacro.EUROBETRAG, listOf(digits(money.value, minFraction = 2)))

    /**
     * Ein Kurs, wie BMF und EZB ihn notieren: `\eurobetrag{1} = \betrag{1.1685}`,
     * gesetzt 1 € = 1,1685 $. Mit allen Stellen, die er trägt, und ohne
     * aufgefüllte Nullen – er ist kein Betrag, auch wenn er das Zeichen der
     * Währung trägt.
     */
    fun exchangeRate(rate: ExchangeRate): Latex =
        Latex.macro(TemplateMacro.EUROBETRAG, listOf(digits(BigDecimal.ONE, minFraction = 0))) + " = ".escapeLatex() +
            Latex.macro(TemplateMacro.BETRAG, listOf(digits(rate.value, minFraction = 0)))

    /** Eine Menge ohne überflüssige Nullen: `\menge{86.5}`, `\menge{1000}` */
    fun quantity(quantity: BigDecimal): Latex = Latex.macro(TemplateMacro.MENGE, listOf(digits(quantity, minFraction = 0)))

    /** Ein Satz in Prozent, ohne überflüssige Nullen: `\prozent{19}`, `\prozent{2.5}` */
    fun percent(percent: Percent): Latex =
        Latex.macro(TemplateMacro.PROZENT, listOf(digits(percent.valueAsPercentage, minFraction = 0)))

    /**
     * Die Ziffern in der Form, die siunitx liest: Punkt als Dezimalzeichen,
     * keine Gruppierung, `-` vor einer negativen Zahl.
     *
     * `toPlainString` und nicht `toString`: Nach `stripTrailingZeros` ist
     * 1000 ein `1E+3`, und siunitx setzte daraus 1 × 10³.
     *
     * `setScale` ohne Rundungsart kann hier nur Nullen anhängen, nie Stellen
     * streichen – die Zielskala ist nie kleiner als die des Werts. Müsste doch
     * eine fallen, würfe es, statt still zu runden (#330).
     */
    private fun digits(value: BigDecimal, minFraction: Int): Latex {
        val stripped = value.stripTrailingZeros()
        return stripped.setScale(maxOf(stripped.scale(), minFraction)).toPlainString().escapeLatex()
    }
}
