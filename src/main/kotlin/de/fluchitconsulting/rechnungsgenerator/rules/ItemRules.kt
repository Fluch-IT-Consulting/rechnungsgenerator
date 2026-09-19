package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import java.math.BigDecimal

/**
 * Die Positionen selbst: dass es welche gibt, dass kein Einzelpreis negativ
 * ist und kein Rabatt außerhalb von 0 bis 100 % liegt. Was an ihrer
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

            items.forEachIndexed { index, item ->
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
                item.discount?.let { rabatt ->
                    val satz = rabatt.valueAsPercentage
                    if (satz <= BigDecimal.ZERO || satz > HUNDRED) {
                        add(
                            Finding(
                                Rule.RABATT_AUSSERHALB_BEREICH,
                                Subject.Item(index, item.name),
                                "Der Rabatt $rabatt liegt außerhalb des zulässigen Bereichs. " +
                                    "Zulässig sind Sätze über 0 % bis einschließlich 100 %.",
                            ),
                        )
                    }
                }
            }
        }

    private val HUNDRED = BigDecimal(100)
}
