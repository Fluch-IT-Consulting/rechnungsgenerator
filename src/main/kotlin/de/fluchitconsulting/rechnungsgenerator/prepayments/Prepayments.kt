package de.fluchitconsulting.rechnungsgenerator.prepayments

import de.fluchitconsulting.rechnungsgenerator.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.Checked
import de.fluchitconsulting.rechnungsgenerator.Finding
import de.fluchitconsulting.rechnungsgenerator.InvoiceCalculator
import de.fluchitconsulting.rechnungsgenerator.InvoiceKind
import de.fluchitconsulting.rechnungsgenerator.Rule
import de.fluchitconsulting.rechnungsgenerator.Subject
import de.fluchitconsulting.rechnungsgenerator.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.model.Prepayment
import de.fluchitconsulting.rechnungsgenerator.model.SellerConfig
import de.fluchitconsulting.rechnungsgenerator.model.TaxShare
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType

// Der fachliche Teil von `rechnungsgenerator abschlaege` (ADR-0003): Aus
// Abschlagsrechnungen werden die Abschläge einer Schlussrechnung. Ohne Dateien
// und ohne Ausgabe, damit sich jede Regel hier ohne beides prüfen lässt.

/**
 * Was beim Zusammentragen herauskommt: die Abschläge und was dabei auffiel.
 *
 * Beides zusammen, weil das Kommando beides braucht – die Abschläge, um den
 * Block zu schreiben, und die Befunde, um es zu lassen (ADR-0003: alles oder
 * nichts).
 *
 * [prepayments] enthält nur die Abschläge angenommener Abschlagsrechnungen
 * (ADR-0007). Vollständig ist die Liste deshalb nur, wenn [findings] keinen
 * Abbruch trägt – und nur dann schreibt das Kommando den Block.
 */
data class CollectedPrepayments(val prepayments: List<Prepayment>, val findings: List<Finding>)

/**
 * Aus einer angenommenen Abschlagsrechnung wird der Abschlag, den eine
 * Schlussrechnung absetzt.
 *
 * Die Steueranteile sind die Steueraufschlüsselung der Abschlagsrechnung,
 * Zeile für Zeile – dieselben Zahlen, die auf ihrem PDF stehen, weil sie aus
 * demselben [InvoiceCalculator] kommen.
 *
 * Nur eine [AcceptedInvoice]: Aus einer Abschlagsrechnung mit Abbruch wird kein
 * Abschlag, so wie aus ihr kein PDF wird (ADR-0007).
 *
 * `vereinnahmt-am` wird übernommen, wenn die Abschlagsrechnung es trägt: Dort
 * hält es eine Vereinnahmung vor der Rechnungsstellung fest, dieselbe Tatsache
 * wie im Abschlag.
 *
 * Reines Übertragen: Ob das Dokument überhaupt eine Abschlagsrechnung ist,
 * prüft [prepaymentsOf] – dort, wo auch die übrigen Regeln der Auswahl stehen.
 */
fun prepaymentOf(abschlagsrechnung: AcceptedInvoice): Prepayment {
    val calculatedInvoice = abschlagsrechnung.calculated
    val invoice = calculatedInvoice.invoice
    return Prepayment(
        invoiceNumber = invoice.number,
        issueDate = invoice.issueDate,
        // Nur eine Abschlagsrechnung trägt eine Vereinnahmung (ADR-0008). Aus
        // einem anderen Dokument wird ebenfalls ein Abschlag, gemeldet als
        // KEINE_ABSCHLAGSRECHNUNG – dann ohne Datum.
        receivedOn = (abschlagsrechnung.kind as? InvoiceKind.PrepaymentInvoice)?.receivedOn,
        taxShares = calculatedInvoice.vatGroups.map { TaxShare(it.category, it.rate, net = it.base, vat = it.amount) },
    )
}

/**
 * Die Abschläge aus mehreren Abschlagsrechnungen, in der übergebenen Reihenfolge.
 *
 * Geprüft wird hier, was die Schlussrechnung später nicht mehr sehen kann: dass
 * alle Abschlagsrechnungen an denselben Kunden gingen, dass keine doppelt dabei
 * ist und dass jede wirklich eine Abschlagsrechnung ist.
 *
 * Die Hinweise der einzelnen Rechnungen fallen weg: Sie galten der
 * Abschlagsrechnung, als sie entstand, nicht der Schlussrechnung von heute.
 * Ihre Abbrüche kommen mit, mit der Herkunft davor.
 */
fun prepaymentsOf(abschlagsrechnungen: List<Invoice>, config: SellerConfig): CollectedPrepayments {
    val findings = mutableListOf<Finding>()

    // groupBy bildet eine Map vom Kundennamen auf die Abschlagsrechnungen an
    // diesen Kunden; mehr als ein Schlüssel heißt: mehr als ein Kunde.
    val byCustomer = abschlagsrechnungen.groupBy { it.customer.name }
    if (byCustomer.size > 1) {
        val customers =
            byCustomer.entries.joinToString(", ") { (name, invoices) ->
                "$name (${invoices.joinToString(", ") { it.number }})"
            }
        findings +=
            Finding(
                Rule.ABSCHLAGSRECHNUNGEN_VERSCHIEDENE_KUNDEN,
                Subject.WholeInvoice,
                "Die Abschlagsrechnungen gingen an verschiedene Kunden: $customers. " +
                    "Eine Schlussrechnung setzt nur Abschläge desselben Kunden ab.",
            )
    }

    // Exakt verglichen wie die Regel der Schlussrechnung (#53).
    abschlagsrechnungen
        .groupBy { it.number }
        .values
        .firstOrNull { it.size > 1 }
        ?.let { duplicates ->
            findings +=
                Finding(
                    Rule.ABSCHLAGSRECHNUNG_DOPPELT,
                    Subject.FromInvoice(duplicates.first().number, Subject.WholeInvoice),
                    "ist zweimal angegeben.",
                )
        }

    // mapNotNull: wie map, aber was `null` ergibt, fällt aus der Liste – hier
    // die abgelehnten Abschlagsrechnungen.
    val abschlaege =
        abschlagsrechnungen.mapNotNull { abschlagsrechnung ->
            // Keine Regel der Schlussrechnung kennt die Art des Dokuments, aus dem
            // ein Abschlag stammt – eine gewöhnliche Rechnung ginge dort klaglos
            // durch.
            if (abschlagsrechnung.type != InvoiceType.PREPAYMENT) {
                findings +=
                    Finding(
                        Rule.KEINE_ABSCHLAGSRECHNUNG,
                        Subject.FromInvoice(abschlagsrechnung.number, Subject.WholeInvoice),
                        "ist eine ${abschlagsrechnung.type.documentName}, keine Abschlagsrechnung. " +
                            "Abgesetzt werden nur Abschläge.",
                    )
            }

            when (val checked = AcceptedInvoice.check(InvoiceCalculator.calculate(abschlagsrechnung, config))) {
                is Checked.Accepted -> prepaymentOf(checked.invoice)
                is Checked.Rejected -> {
                    findings += checked.aborts.map { it.copy(subject = Subject.FromInvoice(abschlagsrechnung.number, it.subject)) }
                    null
                }
            }
        }

    return CollectedPrepayments(abschlaege, findings)
}
