package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.values.ExchangeRate
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent
import de.fluchitconsulting.rechnungsgenerator.core.values.Price
import java.math.BigDecimal

/**
 * Die Ziffern für `\betrag`, `\menge` und `\prozent`; wie sie aussehen, entscheidet die Vorlage (ADR-0021).
 * Jede Ziffer und keine weitere: Ein Einzelpreis von 0,00001 € kommt nicht als 0,00 € an (#14).
 */
object Numbers {
    fun amount(money: Money): Latex = Latex.macro(TemplateMacro.BETRAG, listOf(digits(money.value, minFraction = 2)))

    /** Mit allen Nachkommastellen, die der Wert trägt (BT-146). */
    fun price(price: Price): Latex = Latex.macro(TemplateMacro.BETRAG, listOf(digits(price.value, minFraction = 2)))

    fun euroAmount(money: Money): Latex = Latex.macro(TemplateMacro.EUROBETRAG, listOf(digits(money.value, minFraction = 2)))

    /** `1 € = 1,1685 $`, ohne aufgefüllte Nullen: Ein Kurs ist kein Betrag, auch wenn er ein Währungszeichen trägt. */
    fun exchangeRate(rate: ExchangeRate): Latex =
        Latex.macro(TemplateMacro.EUROBETRAG, listOf(digits(BigDecimal.ONE, minFraction = 0))) + " = ".escapeLatex() +
            Latex.macro(TemplateMacro.BETRAG, listOf(digits(rate.value, minFraction = 0)))

    fun quantity(quantity: BigDecimal): Latex = Latex.macro(TemplateMacro.MENGE, listOf(digits(quantity, minFraction = 0)))

    fun percent(percent: Percent): Latex =
        Latex.macro(TemplateMacro.PROZENT, listOf(digits(percent.valueAsPercentage, minFraction = 0)))

    /**
     * `toPlainString` und nicht `toString`: Nach `stripTrailingZeros` ist 1000 ein `1E+3`, und siunitx setzte
     * 1 × 10³. `setScale` ohne Rundungsart hängt nur Nullen an und würfe, statt still zu runden (#330).
     */
    private fun digits(value: BigDecimal, minFraction: Int): Latex {
        val stripped = value.stripTrailingZeros()
        return stripped.setScale(maxOf(stripped.scale(), minFraction)).toPlainString().escapeLatex()
    }
}
