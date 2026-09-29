package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType

/**
 * Die Rechnungsart als Typ und die Regeln, ohne die er nicht baubar ist (ADR-0008).
 *
 * Die übrigen Gruppen prüfen die flachen Angaben, sonst entfielen mit einem Abschlag ohne Vereinnahmung auch alle
 * anderen Prüfungen. Eine Gruppe einer Rechnungsart spricht aber nur auf dieser Art, entschieden in einem `when`
 * ohne `else` (#97): Auf der falschen spräche ihre Meldung von einem Dokument, das es auf dem Blatt nicht gibt.
 */
internal object InvoiceKindRules {
    sealed interface Result {
        val findings: List<Finding>

        data class Built(val kind: InvoiceKind) : Result {
            override val findings: List<Finding> get() = emptyList()
        }

        data class NotBuilt(override val findings: List<Finding>) : Result
    }

    fun check(calculated: CalculatedInvoice): Result {
        val invoice = calculated.invoice
        val foreign = foreignFields(invoice)

        val own =
            when (invoice.type) {
                InvoiceType.INVOICE, InvoiceType.PARTIAL -> Result.Built(InvoiceKind.Ordinary(invoice.type))
                InvoiceType.PREPAYMENT -> Result.Built(InvoiceKind.PrepaymentInvoice(invoice.receivedOn))
                InvoiceType.FINAL -> finalInvoice(calculated)
                InvoiceType.CANCELLATION -> cancellation(invoice)
            }

        return when (own) {
            is Result.Built -> if (foreign.isEmpty()) own else Result.NotBuilt(foreign)
            is Result.NotBuilt -> Result.NotBuilt(foreign + own.findings)
        }
    }

    /** Die Meldung spricht von der Art des Dokuments statt von einem überzähligen YAML-Schlüssel. */
    private fun foreignFields(invoice: Invoice): List<Finding> =
        buildList {
            if (invoice.type != InvoiceType.CANCELLATION && invoice.cancelled != null) {
                add(
                    Finding(
                        Rule.BEZUG_OHNE_STORNO,
                        Subject.WholeInvoice,
                        "'storniert' gehört nur auf eine Stornorechnung (art: stornorechnung); " +
                            "dieses Dokument ist eine ${invoice.type.documentName}.",
                    ),
                )
            }
            // Auf einer erbrachten Leistung entsteht die Steuer mit der Leistung, nicht mit dem Geldeingang.
            if (invoice.type != InvoiceType.PREPAYMENT && invoice.receivedOn != null) {
                add(
                    Finding(
                        Rule.VEREINNAHMUNG_FALSCHE_ART,
                        Subject.WholeInvoice,
                        "'vereinnahmt-am' gehört nur auf eine Abschlagsrechnung (art: abschlagsrechnung); " +
                            "dieses Dokument ist eine ${invoice.type.documentName}.",
                    ),
                )
            }
            if (invoice.type != InvoiceType.FINAL && invoice.prepayments.isNotEmpty()) {
                add(
                    Finding(
                        Rule.ABSCHLAEGE_FALSCHE_ART,
                        Subject.WholeInvoice,
                        "Abschläge werden nur in einer Schlussrechnung abgesetzt (art: schlussrechnung); " +
                            "dieses Dokument ist eine ${invoice.type.documentName}.",
                    ),
                )
            }
        }

    /** Ein Abschlag landet bei den Absetzungen oder bei den Befunden, nie in keiner und nie in beiden. */
    private fun finalInvoice(calculated: CalculatedInvoice): Result {
        if (calculated.prepayments.isEmpty()) {
            return Result.NotBuilt(
                listOf(
                    Finding(
                        Rule.ABSCHLAEGE_FEHLEN,
                        Subject.WholeInvoice,
                        "Eine Schlussrechnung setzt die vereinnahmten Abschläge ab, hier ist keiner angegeben. " +
                            "Bitte 'abschlaege' füllen – oder, wenn es keine gab, 'art: rechnung' schreiben.",
                    ),
                ),
            )
        }

        val deductions = mutableListOf<Deduction>()
        val findings = mutableListOf<Finding>()
        calculated.prepayments.forEach { deducted ->
            val vereinnahmtAm = deducted.prepayment.receivedOn
            if (vereinnahmtAm != null) {
                deductions += Deduction(deducted, vereinnahmtAm)
            } else {
                findings +=
                    Finding(
                        Rule.ABSCHLAG_OHNE_VEREINNAHMUNG,
                        Subject.Prepayment(deducted.prepayment.invoiceNumber),
                        "ohne 'vereinnahmt-am' keine Absetzung. Abgesetzt wird, was vereinnahmt " +
                            "wurde – ein berechneter, aber unbezahlter Abschlag ist eine offene Forderung " +
                            "und wird gemahnt oder storniert.",
                    )
            }
        }
        return if (findings.isEmpty()) Result.Built(InvoiceKind.FinalInvoice(deductions)) else Result.NotBuilt(findings)
    }

    private fun cancellation(invoice: Invoice): Result =
        invoice.cancelled?.let { Result.Built(InvoiceKind.Cancellation(it)) }
            ?: Result.NotBuilt(
                listOf(
                    Finding(
                        Rule.STORNO_OHNE_BEZUG,
                        Subject.WholeInvoice,
                        "Eine Stornorechnung nennt die Rechnung, die sie aufhebt – 'storniert' fehlt. " +
                            "Ohne Nummer und Datum der Ursprungsrechnung ist sie wertlos (EN 16931 BG-3).",
                    ),
                ),
            )
}
