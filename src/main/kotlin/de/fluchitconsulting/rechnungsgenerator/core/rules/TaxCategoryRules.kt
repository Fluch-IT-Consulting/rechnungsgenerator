package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory

/** Vor dem Versand geprüft: Eine Rechnung mit AE und 19 % fiele sonst erst dem Empfänger auf. */
internal object TaxCategoryRules {
    fun check(calculated: CalculatedInvoice): List<Finding> =
        buildList {
            val invoice = calculated.invoice

            invoice.items.forEachIndexed { index, item ->
                val position = Subject.Item(index, item.name)
                val category = item.taxCategory

                if (category.requiresZeroRate && item.vatRate.valueAsMultiplier.signum() != 0) {
                    add(
                        Finding(
                            Rule.SATZ_MUSS_NULL_SEIN,
                            position,
                            "Kategorie ${category.code} (${category.label}) verlangt einen Steuersatz von 0%, " +
                                "angegeben ist ${item.vatRate}.",
                        ),
                    )
                }
                if (category == TaxCategory.STANDARD && item.vatRate.valueAsMultiplier.signum() == 0) {
                    add(
                        Finding(
                            Rule.REGELSATZ_MIT_NULL,
                            position,
                            "Kategorie S (Regelsatz) mit 0% ist widersprüchlich. Gemeint ist vermutlich " +
                                "Z (Nullsatz), E (steuerbefreit) oder AE (Steuerschuldnerschaft des Leistungsempfängers).",
                        ),
                    )
                }
                if (!category.requiresReason && item.taxExemptionReason != null) {
                    add(
                        Finding(
                            Rule.BEFREIUNGSGRUND_UNZULAESSIG,
                            position,
                            "Kategorie ${category.code} trägt keinen Steuerbefreiungsgrund.",
                        ),
                    )
                }
                if (category.requiresReason && item.taxExemptionReason == null && !category.hasDefaultReason) {
                    add(
                        Finding(
                            Rule.BEFREIUNGSGRUND_FEHLT,
                            position,
                            "Kategorie ${category.code} (${category.label}) verlangt einen " +
                                "Steuerbefreiungsgrund – bitte 'steuerbefreiungsgrund' angeben.",
                        ),
                    )
                }
            }

            // BT-48
            val requiringCustomerVatId = calculated.taxCategories.filter { it.requiresCustomerVatId }
            if (requiringCustomerVatId.isNotEmpty() && invoice.customer.vatId == null) {
                add(
                    Finding(
                        Rule.KUNDEN_UST_ID_FEHLT,
                        Subject.WholeInvoice,
                        "Kategorie ${requiringCustomerVatId.first().code} verlangt die USt-IdNr. des Kunden – " +
                            "bitte 'kunde: umsatzsteuer-id' angeben.",
                    ),
                )
            }

            // BG-23 trägt einen Grund je Kategorie und Satz. Nur bei Kategorien mit Grund, sonst meldet
            // BEFREIUNGSGRUND_UNZULAESSIG schon jede Position.
            calculated.vatGroups.filter { it.category.requiresReason }.forEach { group ->
                if (group.reasons.size > 1) {
                    add(
                        Finding(
                            Rule.BEFREIUNGSGRUENDE_UNEINHEITLICH,
                            Subject.WholeInvoice,
                            "Kategorie ${group.category.code} hat zwei verschiedene Befreiungsgründe: " +
                                group.reasons.joinToString(" / ") +
                                ". Die Steueraufschlüsselung trägt nur einen je Kategorie.",
                        ),
                    )
                }
            }
        }
}
