package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType

/** Was an einem vorhandenen Abschlag falsch sein kann. Ob es ihn geben darf, prüft [InvoiceKindRules]. */
internal object FinalInvoiceRules {
    fun check(calculated: CalculatedInvoice): List<Finding> =
        when (calculated.invoice.type) {
            InvoiceType.FINAL -> prepayments(calculated.invoice) + deduction(calculated)
            InvoiceType.INVOICE, InvoiceType.PREPAYMENT, InvoiceType.PARTIAL, InvoiceType.CANCELLATION -> emptyList()
        }

    private fun prepayments(invoice: Invoice): List<Finding> =
        buildList {
            // Exakt verglichen: Ob „r26-009“ „R26-009“ meint, hängt am Nummernkreis (#53). Die Meldung nennt die
            // Anzahl, sonst löschte, wer „zweimal“ liest, wo es drei sind, eine Zeile zu wenig.
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

                // Ohne Vereinnahmung meldet InvoiceKindRules; die Regeln, die sie brauchen, entfallen für diesen Abschlag.
                val vereinnahmtAm = abschlag.receivedOn ?: return@forEach

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

                // Sonst liefe die Prüfsumme leer, und der Abschlag setzte still 0,00 € ab.
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

                // Gegen den Zahlendreher beim Abschreiben (ADR-0001).
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

    private fun deduction(calculated: CalculatedInvoice): List<Finding> =
        buildList {
            val prepayments = calculated.prepayments
            // Kategorie und Satz, nicht nur der Satz: 0 % kann Verschiedenes bedeuten.
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

            // Nur mit Abschlägen: Ohne sie meldet GESAMTBETRAG_NEGATIV denselben Fall (#47).
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
