package de.fluchitconsulting.rechnung.rules

import de.fluchitconsulting.rechnung.CalculatedInvoice
import de.fluchitconsulting.rechnung.Finding
import de.fluchitconsulting.rechnung.Rule
import de.fluchitconsulting.rechnung.Subject
import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.values.InvoiceType

/**
 * Das Storno: sein Bezug auf die Ursprungsrechnung und das Vorzeichen des
 * Gesamtbetrags (ADR-0006).
 *
 * Das Vorzeichen steht hier und nicht bei den Positionen, weil es zur
 * Rechnungsart gehört: Ein Storno ist negativ, jedes andere Dokument nicht.
 * Früher stand es mitten in `calculate`, weil es die Summen braucht – jetzt
 * liegen die ohnehin fertig vor.
 */
internal object CancellationRules {
    fun check(calculated: CalculatedInvoice): List<Finding> = reference(calculated.invoice) + sign(calculated)

    /**
     * Der Bezug ist im Modell optional, fachlich aber Pflicht – geprüft wird er
     * hier, damit die Meldung von der Rechnungsart spricht statt von einem
     * fehlenden YAML-Schlüssel. In beide Richtungen: Ein Bezug ohne Storno ist
     * genauso falsch wie ein Storno ohne Bezug, so wie 'vereinnahmt-am' nur auf
     * eine Abschlagsrechnung gehört.
     */
    private fun reference(invoice: Invoice): List<Finding> =
        buildList {
            val storniert = invoice.cancelled
            if (invoice.type == InvoiceType.CANCELLATION && storniert == null) {
                add(
                    Finding(
                        Rule.STORNO_OHNE_BEZUG,
                        Subject.WholeInvoice,
                        "Eine Stornorechnung nennt die Rechnung, die sie aufhebt – 'storniert' fehlt. " +
                            "Ohne Nummer und Datum der Ursprungsrechnung ist sie wertlos (EN 16931 BG-3).",
                    ),
                )
            }
            if (invoice.type != InvoiceType.CANCELLATION && storniert != null) {
                add(
                    Finding(
                        Rule.BEZUG_OHNE_STORNO,
                        Subject.WholeInvoice,
                        "'storniert' gehört nur auf eine Stornorechnung (art: stornorechnung); " +
                            "dieses Dokument ist eine ${invoice.type.documentName}.",
                    ),
                )
            }
            // Die beiden folgenden Regeln prüfen den Bezug selbst; ohne ihn
            // haben sie nichts zu prüfen.
            if (storniert == null) return@buildList

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
     */
    private fun sign(calculated: CalculatedInvoice): List<Finding> {
        val invoice = calculated.invoice
        val grossTotal = calculated.grossTotal
        if (invoice.type == InvoiceType.CANCELLATION) {
            if (grossTotal.isNegative()) return emptyList()
            return listOf(
                Finding(
                    Rule.STORNO_NICHT_NEGATIV,
                    Subject.WholeInvoice,
                    "Der Gesamtbetrag wäre $grossTotal. Eine Stornorechnung hebt eine Rechnung auf; ihre " +
                        "Positionen tragen deshalb die Mengen der Ursprungsrechnung mit negativem Vorzeichen.",
                ),
            )
        }
        if (!grossTotal.isNegative()) return emptyList()
        return listOf(
            Finding(
                Rule.GESAMTBETRAG_NEGATIV,
                Subject.WholeInvoice,
                "Der Gesamtbetrag wäre $grossTotal. Ein negativer Betrag gehört in eine Stornorechnung " +
                    "(art: stornorechnung), nicht in eine ${invoice.type.documentName}.",
            ),
        )
    }
}
