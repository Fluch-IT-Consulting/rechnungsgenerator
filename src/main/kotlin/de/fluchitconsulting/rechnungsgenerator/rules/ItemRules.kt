package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.values.Discount
import java.math.BigDecimal

/**
 * Die Positionen selbst: dass es welche gibt, dass kein Einzelpreis negativ
 * ist und dass der Rabatt in seinem Bereich liegt – der Satz über 0 bis 100 %,
 * der Betrag über 0,00 bis zur Basis der Position. Was an ihrer
 * Steuerkategorie hängt, prüft [TaxCategoryRules].
 */
internal object ItemRules {
    fun check(calculated: CalculatedInvoice): List<Finding> =
        buildList {
            val items = calculated.invoice.items

            // EN 16931 BR-16. Ohne Position entstünde eine Rechnung über 0,00 €.
            if (items.isEmpty()) {
                add(
                    Finding(
                        Rule.KEINE_POSITION,
                        Subject.WholeInvoice,
                        "Eine Rechnung braucht mindestens eine Position – 'positionen' ist leer.",
                    ),
                )
            }

            // Über die gerechneten Zeilen und nicht über die Positionen: Der
            // Rabattbetrag misst sich an der Basis, und die rechnet die Zeile.
            calculated.lines.forEachIndexed { index, line ->
                val item = line.item
                // EN 16931 BR-27: Der Einzelpreis ist nie negativ.
                if (item.unitPrice.value.signum() < 0) {
                    add(
                        Finding(
                            Rule.EINZELPREIS_NEGATIV,
                            Subject.Item(index, item.name),
                            "Der Einzelpreis ${item.unitPrice.value.toPlainString()} ist negativ. " +
                                "EN 16931 lässt das nicht zu (BR-27).",
                        ),
                    )
                }
                // Über 100 % würde der Positionsbetrag negativ, und ein negativer
                // Rabatt wäre ein Aufschlag. Null lehnen wir ab, weil er die
                // Spalte einschaltet, ohne etwas zu mindern.
                // Ein Rabatt wird als Satz oder als Betrag vereinbart. Welcher
                // der beiden gemeint war, weiß bei zweien niemand – und der
                // Typ trägt nur einen.
                if (item.hasBothDiscounts) {
                    add(
                        Finding(
                            Rule.RABATT_DOPPELT,
                            Subject.Item(index, item.name),
                            "Die Position trägt 'rabatt' und 'rabatt-betrag'. " +
                                "Ein Rabatt wird als Satz oder als Betrag vereinbart, nicht als beides.",
                        ),
                    )
                }
                when (val discount = item.discount) {
                    null -> {}
                    is Discount.Rate -> {
                        val rate = discount.rate.valueAsPercentage
                        if (rate <= BigDecimal.ZERO || rate > HUNDRED) {
                            add(
                                Finding(
                                    Rule.RABATT_AUSSERHALB_BEREICH,
                                    Subject.Item(index, item.name),
                                    "Der Rabatt ${discount.rate} liegt außerhalb des zulässigen Bereichs. " +
                                        "Zulässig sind Sätze über 0 % bis einschließlich 100 %.",
                                ),
                            )
                        }
                    }
                    // Der Betrag mindert die Basis und kann sie nicht
                    // übersteigen – darüber würde der Positionsbetrag negativ,
                    // wie beim Satz über 100 %. Bei Menge 0 ist die Basis
                    // 0,00, und damit bricht jeder Betrag ab. Geprüft wird
                    // gegen den Betrag der Basis: Im Storno ist sie negativ,
                    // der Rabatt steht aber positiv da.
                    is Discount.Amount -> {
                        if (!discount.amount.isPositive() || discount.amount > line.base.abs()) {
                            add(
                                Finding(
                                    Rule.RABATT_BETRAG_AUSSERHALB_BEREICH,
                                    Subject.Item(index, item.name),
                                    "Der Rabattbetrag ${discount.amount} liegt außerhalb des zulässigen Bereichs. " +
                                        "Zulässig ist mehr als 0,00 und höchstens die Basis der Position " +
                                        "(${line.base.abs()} = Menge × Einzelpreis).",
                                ),
                            )
                        }
                    }
                }
            }
        }

    private val HUNDRED = BigDecimal(100)
}
