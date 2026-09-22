package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType

/**
 * Das Storno: sein Bezug auf die Ursprungsrechnung und das Vorzeichen des
 * Gesamtbetrags (ADR-0006) – und mit ihm, welcher Gesamtbetrag zu welcher Art
 * passt, auch null (#214).
 *
 * Ob ein Storno überhaupt einen Bezug trägt – und ein anderes Dokument keinen –,
 * prüft [InvoiceKindRules]: Ohne Bezug gibt es keine Stornorechnung als Typ
 * (ADR-0008). Hier steht, was am Bezug selbst falsch sein kann – und nur auf
 * einer Stornorechnung (#97).
 *
 * Das Vorzeichen steht hier und nicht bei den Positionen, weil es zur
 * Rechnungsart gehört: Ein Storno ist negativ, jedes andere Dokument nicht.
 * Die Summen, die es dafür braucht, liegen hier fertig vor.
 */
internal object CancellationRules {
    fun check(calculated: CalculatedInvoice): List<Finding> = reference(calculated.invoice) + sign(calculated)

    private fun reference(invoice: Invoice): List<Finding> =
        buildList {
            // Nur auf einer Stornorechnung; warum, steht bei InvoiceKindRules (#97).
            when (invoice.type) {
                InvoiceType.CANCELLATION -> {}
                InvoiceType.INVOICE, InvoiceType.PREPAYMENT, InvoiceType.FINAL, InvoiceType.PARTIAL -> {
                    return@buildList
                }
            }
            // Ohne Bezug gibt es nichts zu prüfen; dass er fehlt, meldet
            // InvoiceKindRules.
            val storniert = invoice.cancelled ?: return@buildList

            // Verglichen wird zeichengenau, wie bei den Abschlägen (#53): Über
            // die Schreibweise fremder Nummern weiß dieses Programm nichts.
            if (storniert.invoiceNumber == invoice.number) {
                add(
                    Finding(
                        Rule.STORNO_AUF_SICH_SELBST,
                        Subject.WholeInvoice,
                        "Die Stornorechnung nennt ihre eigene Nummer '${invoice.number}' als Ursprungsrechnung. " +
                            "Ein Storno bekommt eine eigene Nummer aus derselben Reihe.",
                    ),
                )
            }
            if (storniert.issueDate.isAfter(invoice.issueDate)) {
                add(
                    Finding(
                        Rule.URSPRUNGSRECHNUNG_SPAETER_DATIERT,
                        Subject.WholeInvoice,
                        "Die Ursprungsrechnung ist auf ${storniert.issueDate} datiert, nach dem Rechnungsdatum " +
                            "${invoice.issueDate} dieses Stornos. Aufheben lässt sich nur, was es schon gibt.",
                    ),
                )
            }
        }

    /**
     * Das Vorzeichen des Gesamtbetrags. Vor #47 schlug hier erst
     * RESTBETRAG_NEGATIV zu, mit einer Meldung über Abschläge, die eine
     * gewöhnliche Rechnung gar nicht hat.
     *
     * Null ist für die Abschlagsrechnung zu wenig (#214): Sie fordert eine
     * Vorauszahlung, und über null stünde sie später als leerer Abschlag in der
     * Schlussrechnung. Einer Rechnung über null gilt nur ein Hinweis
     * (GESAMTBETRAG_NULL), sie kann gewollt sein.
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

    /** Jede Art außer dem Storno: kein negativer Gesamtbetrag. */
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
