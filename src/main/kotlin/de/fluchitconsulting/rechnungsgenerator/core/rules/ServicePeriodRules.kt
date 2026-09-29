package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice

/**
 * BR-29 und BR-30, und eine erbrachte Leistung ist vor ihrer Rechnung zu Ende. Ausgenommen sind die
 * Abschlagsrechnung, deren Zeitraum eine Prognose ist, und das Storno, das nichts abrechnet (#151).
 */
internal object ServicePeriodRules {
    fun check(calculated: CalculatedInvoice): List<Finding> =
        buildList {
            val invoice = calculated.invoice
            val zeitraeume =
                listOf(Triple(Subject.WholeInvoice, "Der Leistungszeitraum", invoice.servicePeriod)) +
                    invoice.items.mapIndexedNotNull { index, item ->
                        item.servicePeriod?.let { Triple(Subject.Item(index, item.name), "Der Zeitraum", it) }
                    }

            zeitraeume.forEach { (subject, label, zeitraum) ->
                if (zeitraum.start.isAfter(zeitraum.end)) {
                    add(
                        Finding(
                            Rule.ZEITRAUM_VERDREHT,
                            subject,
                            "$label beginnt am ${zeitraum.start}, endet aber schon am ${zeitraum.end}.",
                        ),
                    )
                }
                // Der Zeitraum eines Stornos stammt aus der Ursprungsrechnung und kann eine Prognose sein (#151).
                if (invoice.type.chargesForService &&
                    !invoice.type.servicePeriodIsForecast &&
                    zeitraum.end.isAfter(invoice.issueDate)
                ) {
                    add(
                        Finding(
                            Rule.ZEITRAUM_NACH_RECHNUNGSDATUM,
                            subject,
                            "$label endet am ${zeitraum.end}, nach dem Rechnungsdatum ${invoice.issueDate}. Eine " +
                                "${invoice.type.documentName} rechnet eine erbrachte Leistung ab – steht die Leistung " +
                                "noch aus, ist es eine Abschlagsrechnung.",
                        ),
                    )
                }
            }
        }
}
