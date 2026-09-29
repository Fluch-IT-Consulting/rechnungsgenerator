package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType

/** Zulässig, aber selten gemeint. Die Hinweise zur Bestätigungsabfrage stehen in [VatIdCheckRules]. */
internal object PlausibilityHints {
    fun check(calculated: CalculatedInvoice): List<Finding> =
        buildList {
            val invoice = calculated.invoice

            invoice.items.forEachIndexed { index, item ->
                if (item.quantity.signum() == 0) {
                    add(
                        Finding(
                            Rule.MENGE_NULL,
                            Subject.Item(index, item.name),
                            "Menge 0 – die Position steht mit 0.00 auf der Rechnung. Ist das gewollt?",
                        ),
                    )
                }
            }

            // Meist ein vergessener Einzelpreis oder ein Rabatt von 100 statt 10 (#214). Die übrigen Arten brechen
            // schon an ihren eigenen Regeln zum Betrag ab.
            val hintsOnZero =
                when (invoice.type) {
                    InvoiceType.INVOICE, InvoiceType.PARTIAL -> true
                    InvoiceType.PREPAYMENT, InvoiceType.FINAL, InvoiceType.CANCELLATION -> false
                }
            if (hintsOnZero && calculated.grossTotal.isZero()) {
                add(
                    Finding(
                        Rule.GESAMTBETRAG_NULL,
                        Subject.WholeInvoice,
                        "Gesamtbetrag 0.00 – das Dokument fordert nichts. Ist das gewollt?",
                    ),
                )
            }

            // §14 Abs. 2 UStG. Gegen das Ende der Leistung, nicht gegen heute: So ergibt dieselbe Datei immer dieselben
            // Hinweise. Nicht bei der Abschlagsrechnung, deren Zeitraum eine Prognose ist, und nicht beim Storno.
            val serviceEnd = invoice.servicePeriod.end
            if (invoice.type.chargesForService &&
                !invoice.type.servicePeriodIsForecast &&
                invoice.issueDate.isAfter(serviceEnd.plusMonths(6))
            ) {
                add(
                    Finding(
                        Rule.RECHNUNG_SPAET,
                        Subject.WholeInvoice,
                        "Die Rechnung vom ${invoice.issueDate} kommt mehr als sechs Monate nach dem Ende der " +
                            "Leistung am $serviceEnd. §14 Abs. 2 UStG verlangt sie innerhalb von sechs Monaten – " +
                            "ist das Rechnungsdatum richtig?",
                    ),
                )
            }
        }
}
