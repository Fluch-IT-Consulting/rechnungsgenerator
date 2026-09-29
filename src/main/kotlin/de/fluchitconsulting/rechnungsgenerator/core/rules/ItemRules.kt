package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Discount
import java.math.BigDecimal

internal object ItemRules {
    fun check(calculated: CalculatedInvoice): List<Finding> =
        buildList {
            val items = calculated.invoice.items

            // BR-16
            if (items.isEmpty()) {
                add(
                    Finding(
                        Rule.KEINE_POSITION,
                        Subject.WholeInvoice,
                        "Eine Rechnung braucht mindestens eine Position – 'positionen' ist leer.",
                    ),
                )
            }

            // Über die gerechneten Zeilen: Der Rabattbetrag misst sich an ihrer Basis.
            calculated.lines.forEachIndexed { index, line ->
                val item = line.item
                // BR-27
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
                // Null schaltet die Rabattspalte ein, ohne etwas zu mindern.
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
                                        "Zulässig sind Sätze über 0% bis einschließlich 100%.",
                                ),
                            )
                        }
                    }
                    // Gegen den Betrag der Basis: Im Storno ist sie negativ, der Rabatt steht positiv da.
                    is Discount.Amount -> {
                        if (!discount.amount.isPositive() || discount.amount > line.base.abs()) {
                            add(
                                Finding(
                                    Rule.RABATT_BETRAG_AUSSERHALB_BEREICH,
                                    Subject.Item(index, item.name),
                                    "Der Rabattbetrag ${discount.amount} liegt außerhalb des zulässigen Bereichs. " +
                                        "Zulässig ist mehr als 0.00 und höchstens die Basis der Position " +
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
