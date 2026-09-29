package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType

/** Was am Bezug des Stornos falsch sein kann, und das Vorzeichen des Gesamtbetrags je Art (ADR-0006, #214). */
internal object CancellationRules {
    fun check(calculated: CalculatedInvoice): List<Finding> = reference(calculated.invoice) + sign(calculated)

    private fun reference(invoice: Invoice): List<Finding> =
        buildList {
            when (invoice.type) {
                InvoiceType.CANCELLATION -> {}
                InvoiceType.INVOICE, InvoiceType.PREPAYMENT, InvoiceType.FINAL, InvoiceType.PARTIAL -> {
                    return@buildList
                }
            }
            val cancelled = invoice.cancelled ?: return@buildList

            // Zeichengenau wie bei den Abschlägen (#53).
            if (cancelled.invoiceNumber == invoice.number) {
                add(
                    Finding(
                        Rule.STORNO_AUF_SICH_SELBST,
                        Subject.WholeInvoice,
                        "Die Stornorechnung nennt ihre eigene Nummer '${invoice.number}' als Ursprungsrechnung. " +
                            "Ein Storno bekommt eine eigene Nummer aus derselben Reihe.",
                    ),
                )
            }
            if (cancelled.issueDate.isAfter(invoice.issueDate)) {
                add(
                    Finding(
                        Rule.URSPRUNGSRECHNUNG_SPAETER_DATIERT,
                        Subject.WholeInvoice,
                        "Die Ursprungsrechnung ist auf ${cancelled.issueDate} datiert, nach dem Rechnungsdatum " +
                            "${invoice.issueDate} dieses Stornos. Aufheben lässt sich nur, was es schon gibt.",
                    ),
                )
            }
        }

    /**
     * Null ist für die Abschlagsrechnung zu wenig: Sie stünde später als leerer Abschlag in der Schlussrechnung
     * (#214). Einer Rechnung über null gilt nur ein Hinweis.
     */
    private fun sign(calculated: CalculatedInvoice): List<Finding> {
        val invoice = calculated.invoice
        val grossTotal = calculated.grossTotal
        return when (invoice.type) {
            InvoiceType.CANCELLATION -> {
                if (grossTotal.isNegative()) {
                    emptyList()
                } else {
                    listOf(
                        Finding(
                            Rule.STORNO_NICHT_NEGATIV,
                            Subject.WholeInvoice,
                            "Der Gesamtbetrag wäre $grossTotal. Eine Stornorechnung hebt eine Rechnung auf; ihre " +
                                "Positionen tragen deshalb die Mengen der Ursprungsrechnung mit negativem Vorzeichen.",
                        ),
                    )
                }
            }
            InvoiceType.PREPAYMENT -> {
                if (grossTotal.isZero()) {
                    listOf(
                        Finding(
                            Rule.ABSCHLAGSRECHNUNG_OHNE_BETRAG,
                            Subject.WholeInvoice,
                            "Der Gesamtbetrag ist 0.00. Eine Abschlagsrechnung über null fordert nichts und stünde " +
                                "später als leerer Abschlag in der Schlussrechnung.",
                        ),
                    )
                } else {
                    notNegative(calculated)
                }
            }
            InvoiceType.INVOICE, InvoiceType.FINAL, InvoiceType.PARTIAL -> {
                notNegative(calculated)
            }
        }
    }

    private fun notNegative(calculated: CalculatedInvoice): List<Finding> {
        val grossTotal = calculated.grossTotal
        if (!grossTotal.isNegative()) return emptyList()
        return listOf(
            Finding(
                Rule.GESAMTBETRAG_NEGATIV,
                Subject.WholeInvoice,
                "Der Gesamtbetrag wäre $grossTotal. Ein negativer Betrag gehört in eine Stornorechnung " +
                    "(art: stornorechnung), nicht in eine ${calculated.invoice.type.documentName}.",
            ),
        )
    }
}
