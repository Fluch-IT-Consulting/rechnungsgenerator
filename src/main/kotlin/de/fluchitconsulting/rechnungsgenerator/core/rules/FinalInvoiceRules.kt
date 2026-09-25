package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType

/**
 * Die Schlussrechnung und die Absetzung ihrer Abschläge.
 *
 * Ob abgesetzt wird, wo es hingehört, ob eine Schlussrechnung überhaupt Abschläge
 * trägt und ob jeder davon vereinnahmt ist, prüft [InvoiceKindRules] – ohne das
 * gibt es keine Schlussrechnung als Typ (ADR-0008). Hier steht, was an einem
 * vorhandenen Abschlag falsch sein kann – und nur auf einer Schlussrechnung
 * (#97).
 *
 * Zwei Teile: die Angaben je Abschlag, die sich ohne die Summen der Rechnung
 * prüfen lassen, und die Absetzung gegen die Rechnung selbst – für die liegt
 * das fertig gerechnete Ergebnis vor (#71).
 */
internal object FinalInvoiceRules {
    // Nur auf einer Schlussrechnung; warum, steht bei InvoiceKindRules (#97).
    fun check(calculated: CalculatedInvoice): List<Finding> =
        when (calculated.invoice.type) {
            InvoiceType.FINAL -> prepayments(calculated.invoice) + deduction(calculated)
            InvoiceType.INVOICE, InvoiceType.PREPAYMENT, InvoiceType.PARTIAL, InvoiceType.CANCELLATION -> emptyList()
        }

    private fun prepayments(invoice: Invoice): List<Finding> =
        buildList {
            // Derselbe Abschlag zweimal abgesetzt ist ein Kopierfehler und fiele
            // sonst nur über den Restbetrag auf.
            // Verglichen wird exakt (#53): Ob "r26-009" dasselbe Dokument meint wie
            // "R26-009", hängt am Nummernkreis des Rechnungsstellers, und den kennt
            // der Generator nicht.
            // Jede mehrfach genannte Nummer bekommt ihren eigenen Befund (#152):
            // Der Gegenstand am Befund ist eine Nummer, also heißt zwei Nummern
            // auch zwei Befunde; mit firstOrNull stünde nur die erste da, und
            // die zweite fiele erst im nächsten Lauf auf (ADR-0004). Die Meldung
            // nennt die Anzahl: Wer "zweimal" liest, wo es drei sind, löscht
            // eine Zeile und läuft in denselben Abbruch.
            invoice.prepayments
                .groupBy { it.invoiceNumber }
                .filterValues { it.size > 1 }
                .forEach { (number, duplicates) ->
                    add(
                        Finding(
                            Rule.ABSCHLAG_DOPPELT,
                            Subject.Prepayment(number),
                            "steht ${duplicates.size}-mal in dieser Schlussrechnung.",
                        ),
                    )
                }

            invoice.prepayments.forEach { abschlag ->
                val subject = Subject.Prepayment(abschlag.invoiceNumber)

                // Fehlt die Vereinnahmung, meldet das InvoiceKindRules – und die
                // Regeln, die sie brauchen, entfallen, aber nur für diesen
                // Abschlag. Ausgedrückt durch das frühe Weiterspringen: Ein Feld
                // am Befund, das seine Voraussetzungen aufzählt, wäre ein
                // Mechanismus für eine Handvoll Fälle.
                val vereinnahmtAm = abschlag.receivedOn ?: return@forEach

                // Was nach dem Rechnungsdatum liegt, ist für diese Rechnung nicht
                // vereinnahmt.
                if (vereinnahmtAm.isAfter(invoice.issueDate)) {
                    add(
                        Finding(
                            Rule.ABSCHLAG_NACH_RECHNUNGSDATUM,
                            subject,
                            "vereinnahmt am $vereinnahmtAm, also nach dem Rechnungsdatum " +
                                "${invoice.issueDate}. Für diese Schlussrechnung ist er nicht vereinnahmt.",
                        ),
                    )
                }

                // Das Gegenstück für das Rechnungsdatum (#47): Ein Abschlag geht der
                // Schlussrechnung voraus. Später datiert ist er ein Tippfehler – oder
                // eine Rechnung, die nicht hierher gehört.
                if (abschlag.issueDate.isAfter(invoice.issueDate)) {
                    add(
                        Finding(
                            Rule.ABSCHLAG_SPAETER_DATIERT,
                            subject,
                            "Rechnungsdatum ${abschlag.issueDate}, also nach dem Datum dieser " +
                                "Schlussrechnung ${invoice.issueDate}. Ein Abschlag geht der Schlussrechnung voraus.",
                        ),
                    )
                }

                // Ohne Anteile liefe die Prüfsumme über eine leere Schleife, und der
                // Abschlag setzte still 0,00 € ab.
                if (abschlag.taxShares.isEmpty()) {
                    add(
                        Finding(
                            Rule.ABSCHLAG_OHNE_STEUERANTEILE,
                            subject,
                            "keine 'steueranteile' angegeben. Ein Abschlag ohne Netto und Steuer lässt " +
                                "sich nicht absetzen – §14 Abs. 5 Satz 2 UStG setzt das Entgelt und die darauf " +
                                "entfallende Steuer ab.",
                        ),
                    )
                    return@forEach
                }

                // Die Prüfsumme gegen den Zahlendreher (ADR-0001). Die Steuer steht in
                // der Datei, weil sie aus der alten Rechnung abgeschrieben wird –
                // nachgerechnet wird sie trotzdem.
                abschlag.taxShares.forEach { anteil ->
                    val expected = anteil.net * anteil.vatRate
                    if (anteil.vat != expected) {
                        add(
                            Finding(
                                Rule.ABSCHLAG_PRUEFSUMME,
                                subject,
                                "${anteil.net} netto zu ${anteil.vatRate} ergeben $expected Umsatzsteuer, " +
                                    "angegeben sind ${anteil.vat}.",
                            ),
                        )
                    }
                }
            }
        }

    /** Die Absetzung gegen die Rechnung selbst – die beiden Regeln, die die Summen brauchen. */
    private fun deduction(calculated: CalculatedInvoice): List<Finding> =
        buildList {
            val prepayments = calculated.prepayments
            // Zwei Positionen mit 0 % können steuerlich Verschiedenes bedeuten,
            // deshalb muss die Kombination aus Kategorie und Satz passen – nicht
            // nur der Satz.
            val present = calculated.vatGroups.map { it.category to it.rate }.toSet()
            prepayments.forEach { deducted ->
                deducted.prepayment.taxShares.forEach { anteil ->
                    if ((anteil.taxCategory to anteil.vatRate) !in present) {
                        add(
                            Finding(
                                Rule.ABSCHLAG_KOMBINATION_FEHLT,
                                Subject.Prepayment(deducted.prepayment.invoiceNumber),
                                "${anteil.taxCategory.code} zu " +
                                    "${anteil.vatRate} kommt in dieser Schlussrechnung nicht vor. Entweder war der " +
                                    "Abschlag damals falsch oder die Schlussrechnung ist es jetzt.",
                            ),
                        )
                    }
                }
            }

            // Eine Überzahlung ist kein Restbetrag: Zurückgezahlt wird außerhalb
            // dieses Programms, oder der zu hohe Abschlag wird storniert. Ein
            // Storno der Schlussrechnung wäre es gerade nicht – die ist ja
            // richtig.
            //
            // Nur bei Abschlägen: Ohne sie ist ein negativer Restbetrag schlicht
            // ein negativer Gesamtbetrag, und den meldet die Regel dafür. Sonst
            // stünde "mehr Abschläge abgesetzt (0.00)" unter einer Rechnung, die
            // gar keine hat (#47).
            if (prepayments.isNotEmpty() && calculated.remainingAmount.isNegative()) {
                add(
                    Finding(
                        Rule.RESTBETRAG_NEGATIV,
                        Subject.WholeInvoice,
                        "Der Restbetrag wäre ${calculated.remainingAmount}: Es sind mehr Abschläge abgesetzt " +
                            "(${calculated.prepaymentTotal}) als die Schlussrechnung ausmacht (${calculated.grossTotal}). " +
                            "Eine Überzahlung gehört nicht in eine Rechnung: Sie wird zurückgezahlt, oder der " +
                            "zu hohe Abschlag wird storniert.",
                    ),
                )
            }
        }
}
