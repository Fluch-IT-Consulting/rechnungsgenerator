package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.Finding
import de.fluchitconsulting.rechnungsgenerator.Rule
import de.fluchitconsulting.rechnungsgenerator.Subject

/**
 * Die Positionen selbst: dass es welche gibt und dass kein Einzelpreis negativ
 * ist. Was an ihrer Steuerkategorie hängt, prüft [TaxCategoryRules].
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
            }
        }
}
