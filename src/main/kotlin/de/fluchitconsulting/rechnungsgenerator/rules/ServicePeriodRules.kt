package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice

/**
 * Die Zeiträume der Rechnung und ihrer Positionen (#47).
 *
 * Kein Zeitraum endet vor seinem Beginn – EN 16931 BR-29 für die Rechnung,
 * BR-30 für die Position. Und eine Rechnung über eine erbrachte Leistung
 * entsteht nicht, bevor die Leistung zu Ende ist: So definiert das Glossar
 * den Leistungszeitraum.
 *
 * Zwei Arten sind davon ausgenommen, aus verschiedenen Gründen: die
 * Abschlagsrechnung, weil ihr Zeitraum eine Prognose ist, und die
 * Stornorechnung, weil sie gar nichts abrechnet (#151). Das Verdrehen eines
 * Zeitraums bleibt für beide verboten.
 */
internal object ServicePeriodRules {
    fun check(calculated: CalculatedInvoice): List<Finding> =
        buildList {
            val invoice = calculated.invoice
            // Je Zeitraum, wem er gehört und wie er in der Meldung heißt: Der
            // Zeitraum der Rechnung trägt seinen Namen selbst, der einer
            // Position bekommt ihn vom Gegenstand davor.
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
                // chargesForService schließt das Storno aus (#151): Sein
                // Zeitraum stammt aus der Ursprungsrechnung und wurde dort
                // geprüft. War die eine Abschlagsrechnung, ist er eine Prognose
                // und endet nach dem Rechnungsdatum – das brach hier ab, mit
                // dem Rat, es sei dann eine Abschlagsrechnung.
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
