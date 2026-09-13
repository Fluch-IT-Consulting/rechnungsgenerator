package de.fluchitconsulting.rechnung.rules

import de.fluchitconsulting.rechnung.CalculatedInvoice
import de.fluchitconsulting.rechnung.Finding
import de.fluchitconsulting.rechnung.Rule
import de.fluchitconsulting.rechnung.Subject

/**
 * Die Zeiträume der Rechnung und ihrer Positionen (#47).
 *
 * Kein Zeitraum endet vor seinem Beginn – EN 16931 BR-29 für die Rechnung,
 * BR-30 für die Position. Und eine Rechnung über eine erbrachte Leistung
 * entsteht nicht, bevor die Leistung zu Ende ist: So definiert das Glossar
 * den Leistungszeitraum. Ausgenommen ist nur ein Zeitraum, der eine
 * Prognose ist – der der Abschlagsrechnung.
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
                if (!invoice.type.servicePeriodIsForecast && zeitraum.end.isAfter(invoice.issueDate)) {
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
