package de.fluchitconsulting.rechnungsgenerator.prepayments

import de.fluchitconsulting.rechnungsgenerator.core.accepted.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.accepted.Verdict
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.Prepayment
import de.fluchitconsulting.rechnungsgenerator.core.model.SellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxShare
import de.fluchitconsulting.rechnungsgenerator.core.rules.Finding
import de.fluchitconsulting.rechnungsgenerator.core.rules.InvoiceKind
import de.fluchitconsulting.rechnungsgenerator.core.rules.Rule
import de.fluchitconsulting.rechnungsgenerator.core.rules.Subject
import de.fluchitconsulting.rechnungsgenerator.core.values.Currency

// Ohne Dateien und ohne Ausgabe, damit sich jede Regel ohne beides prüfen lässt (ADR-0003).

/** Im Kommando der Dateiname; hier nur `name`, weil dieses Modul keine Dateien liest. */
data class NamedInvoice(val name: String, val invoice: Invoice)

data class CollectedPrepayment(val name: String, val prepayment: Prepayment)

/** Die Liste gibt es nur vollständig, gehalten vom Typ (ADR-0003). Hinweise trägt keiner der beiden Fälle. */
sealed interface CollectedPrepayments {
    data class Complete(val entries: List<CollectedPrepayment>, val currency: Currency) : CollectedPrepayments

    data class Rejected(val aborts: List<Finding>) : CollectedPrepayments
}

/**
 * Geprüft wird, was die Schlussrechnung nicht mehr sehen kann: derselbe Kunde, dieselbe Währung, keine doppelt,
 * jede eine Abschlagsrechnung. Deren Hinweise fallen weg, sie galten ihr beim Entstehen; ihre Abbrüche kommen mit.
 * Eine leere Liste ist ein Fehler des Aufrufers: Ohne Datei gäbe es keine Währung.
 */
fun prepaymentsOf(prepaymentInvoices: List<NamedInvoice>, config: SellerConfig): CollectedPrepayments {
    require(prepaymentInvoices.isNotEmpty()) { "Ohne Abschlagsrechnung gibt es keine Abschläge zusammenzutragen." }
    val findings = mutableListOf<Finding>()

    findings +=
        differing(prepaymentInvoices, Rule.ABSCHLAGSRECHNUNGEN_VERSCHIEDENE_KUNDEN, { it.customer.name }) { customers ->
            "Die Abschlagsrechnungen gingen an verschiedene Kunden: $customers. " +
                "Eine Schlussrechnung setzt nur Abschläge desselben Kunden ab."
        }

    // Ob die Schlussrechnung dieselbe Währung nennt, sieht später niemand mehr (ADR-0001).
    findings +=
        differing(prepaymentInvoices, Rule.ABSCHLAGSRECHNUNGEN_VERSCHIEDENE_WAEHRUNGEN, Invoice::currency) { currencies ->
            "Die Abschlagsrechnungen stehen in verschiedenen Währungen: $currencies. " +
                "Eine Schlussrechnung setzt nur Abschläge in einer Währung ab."
        }

    // Exakt verglichen, je Nummer ein Befund mit Anzahl, wie in der Schlussrechnung (#53).
    prepaymentInvoices
        .groupBy { it.invoice.number }
        .filterValues { it.size > 1 }
        .forEach { (number, duplicates) ->
            findings +=
                Finding(
                    Rule.ABSCHLAGSRECHNUNG_DOPPELT,
                    Subject.FromInvoice(number, Subject.WholeInvoice),
                    "ist ${duplicates.size}-mal angegeben.",
                )
        }

    val entries =
        prepaymentInvoices.mapNotNull { (name, prepaymentInvoice) ->
            // Keine Regel der Schlussrechnung kennt die Art der Quelle; eine gewöhnliche Rechnung ginge dort durch.
            when (prepaymentInvoice.type) {
                InvoiceType.PREPAYMENT -> {
                    when (val verdict = AcceptedInvoice.check(CalculatedInvoice(prepaymentInvoice, config))) {
                        is Verdict.Accepted -> {
                            CollectedPrepayment(name, prepaymentOf(verdict.invoice))
                        }
                        is Verdict.Rejected -> {
                            findings +=
                                verdict.aborts.map { it.copy(subject = Subject.FromInvoice(prepaymentInvoice.number, it.subject)) }
                            null
                        }
                    }
                }
                InvoiceType.INVOICE, InvoiceType.PARTIAL, InvoiceType.FINAL, InvoiceType.CANCELLATION -> {
                    findings +=
                        Finding(
                            Rule.KEINE_ABSCHLAGSRECHNUNG,
                            Subject.FromInvoice(prepaymentInvoice.number, Subject.WholeInvoice),
                            "ist eine ${prepaymentInvoice.type.documentName}, keine Abschlagsrechnung. " +
                                "Abgesetzt werden nur Abschläge.",
                        )
                    null
                }
            }
        }

    return if (findings.isEmpty()) {
        // Ohne ABSCHLAGSRECHNUNGEN_VERSCHIEDENE_WAEHRUNGEN stehen alle in der ersten.
        CollectedPrepayments.Complete(entries, prepaymentInvoices.first().invoice.currency)
    } else {
        CollectedPrepayments.Rejected(findings)
    }
}

/** [sentence] bekommt jeden Wert mit seinen Rechnungen: `EUR (R26-001), USD (R26-002, R26-003)`. */
private fun <K> differing(
    prepaymentInvoices: List<NamedInvoice>,
    rule: Rule,
    key: (Invoice) -> K,
    sentence: (String) -> String,
): List<Finding> {
    val byKey = prepaymentInvoices.groupBy { key(it.invoice) }
    if (byKey.size <= 1) return emptyList()
    val values =
        byKey.entries.joinToString(", ") { (value, invoices) ->
            "$value (${invoices.joinToString(", ") { it.invoice.number }})"
        }
    return listOf(Finding(rule, Subject.WholeInvoice, sentence(values)))
}

/**
 * Die Steueranteile sind die Steueraufschlüsselung der Abschlagsrechnung, dieselben Zahlen wie auf ihrem PDF. Der
 * `else`-Zweig ist gewollt: Hier ist genau eine Art richtig, und das `when` des Aufrufers hält das fest (#281).
 */
private fun prepaymentOf(prepaymentInvoice: AcceptedInvoice): Prepayment {
    val calculatedInvoice = prepaymentInvoice.calculated
    val invoice = calculatedInvoice.invoice
    val receivedOn =
        when (val kind = prepaymentInvoice.kind) {
            is InvoiceKind.PrepaymentInvoice -> kind.receivedOn
            else -> error("${invoice.number} ist keine Abschlagsrechnung – das prüft prepaymentsOf, bevor es hierher kommt.")
        }
    return Prepayment(
        invoiceNumber = invoice.number,
        issueDate = invoice.issueDate,
        receivedOn = receivedOn,
        taxShares = calculatedInvoice.vatGroups.map { TaxShare(it.category, it.rate, net = it.base, vat = it.amount) },
    )
}
