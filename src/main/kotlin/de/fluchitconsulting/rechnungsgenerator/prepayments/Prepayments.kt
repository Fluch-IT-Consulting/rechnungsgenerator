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

// Der fachliche Teil von `rechnungsgenerator abschlaege` (ADR-0003): Aus
// Abschlagsrechnungen werden die Abschläge einer Schlussrechnung. Ohne Dateien
// und ohne Ausgabe, damit sich jede Regel hier ohne beides prüfen lässt.

/**
 * Eine Rechnung unter dem Namen, unter dem der Aufrufer sie kennt.
 *
 * Im Kommando ist das der Dateiname, und er soll am Ende über jedem Abschlag
 * stehen. Hier heißt er trotzdem nur `name`: Dieses Modul liest keine Dateien
 * und muss nicht wissen, dass es eine ist.
 */
data class NamedInvoice(val name: String, val invoice: Invoice)

/** Ein Abschlag und der Name der Rechnung, aus der er stammt. */
data class CollectedPrepayment(val name: String, val prepayment: Prepayment)

/**
 * Was beim Zusammentragen herauskommt: alle Abschläge – oder die Gründe, warum
 * keiner geschrieben wird (#118).
 *
 * Die Liste gibt es nur, wenn sie vollständig ist (ADR-0003: alles oder
 * nichts), und die Zusage hält der Typ, nicht ein Filter im Aufrufer.
 *
 * Hinweise trägt keiner der beiden Fälle: Die der einzelnen Abschlagsrechnungen
 * fallen weg (ADR-0003), und die Regeln des Zusammentragens selbst sind alle
 * Abbrüche.
 */
sealed interface CollectedPrepayments {
    /**
     * Die Abschläge in der übergebenen Reihenfolge, jeder mit seiner Herkunft,
     * und die eine [currency], in der sie alle stehen (#409).
     */
    data class Complete(val entries: List<CollectedPrepayment>, val currency: Currency) : CollectedPrepayments

    /** Kein Block: die Abbrüche, jeder mit der Rechnung, aus der er stammt. */
    data class Rejected(val aborts: List<Finding>) : CollectedPrepayments
}

/**
 * Die Abschläge aus mehreren Abschlagsrechnungen, in der übergebenen Reihenfolge.
 *
 * Geprüft wird hier, was die Schlussrechnung später nicht mehr sehen kann: dass
 * alle Abschlagsrechnungen an denselben Kunden gingen und in derselben Währung
 * stehen, dass keine doppelt dabei ist und dass jede wirklich eine
 * Abschlagsrechnung ist.
 *
 * Ein Dokument anderer Art wird gar nicht erst gerechnet und geprüft: Es fliegt
 * ohnehin aus dem Aufruf, und seine übrigen Befunde hülfen niemandem – sie
 * gälten einem Dokument, das hier nichts zu suchen hat.
 *
 * Die Hinweise der einzelnen Rechnungen fallen weg: Sie galten der
 * Abschlagsrechnung, als sie entstand, nicht der Schlussrechnung von heute.
 * Ihre Abbrüche kommen mit, mit der Herkunft davor.
 *
 * Verlangt mindestens eine Abschlagsrechnung. Eine leere Liste ist ein Fehler
 * des Aufrufers und kein Befund: Ein leeres [CollectedPrepayments.Complete]
 * bräuchte eine Währung, und es gäbe keine Datei, aus der sie käme.
 */
fun prepaymentsOf(prepaymentInvoices: List<NamedInvoice>, config: SellerConfig): CollectedPrepayments {
    require(prepaymentInvoices.isNotEmpty()) { "Ohne Abschlagsrechnung gibt es keine Abschläge zusammenzutragen." }
    val findings = mutableListOf<Finding>()

    findings +=
        differing(prepaymentInvoices, Rule.ABSCHLAGSRECHNUNGEN_VERSCHIEDENE_KUNDEN, { it.customer.name }) { customers ->
            "Die Abschlagsrechnungen gingen an verschiedene Kunden: $customers. " +
                "Eine Schlussrechnung setzt nur Abschläge desselben Kunden ab."
        }

    // Die Schlussrechnung trägt eine Währung für jeden Betrag, auch für die
    // abgesetzten (#409). Der Block nennt sie nicht, sie steht in der
    // Schlussrechnung selbst; ob die dieselbe nennt, sieht niemand mehr, der
    // die Abschlagsrechnungen liest (ADR-0001).
    findings +=
        differing(prepaymentInvoices, Rule.ABSCHLAGSRECHNUNGEN_VERSCHIEDENE_WAEHRUNGEN, Invoice::currency) { currencies ->
            "Die Abschlagsrechnungen stehen in verschiedenen Währungen: $currencies. " +
                "Eine Schlussrechnung setzt nur Abschläge in einer Währung ab."
        }

    // Exakt verglichen wie die Regel der Schlussrechnung (#53).
    // Je Nummer ein Befund, mit ihrer Anzahl – wie in der Schlussrechnung (#152).
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

    // Heraus fallen die abgelehnten Abschlagsrechnungen. Die Liste zählt am
    // Ende nur, wenn dabei kein Befund entstanden ist; sonst gibt es sie gar
    // nicht.
    val entries =
        prepaymentInvoices.mapNotNull { (name, prepaymentInvoice) ->
            // Keine Regel der Schlussrechnung kennt die Art des Dokuments, aus dem
            // ein Abschlag stammt – eine gewöhnliche Rechnung ginge dort klaglos
            // durch. Ein `when` über alle Arten ohne `else` (#281): Eine neue Art
            // entscheidet hier, ob sie ein Abschlag ist, statt still unter die
            // übrigen zu fallen.
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

/**
 * Ein Befund nach [rule], wenn die Abschlagsrechnungen in [key] auseinandergehen,
 * sonst keiner. [sentence] bekommt jeden Wert mit den Nummern der Rechnungen,
 * die ihn tragen: `EUR (R26-001), USD (R26-002, R26-003)`.
 */
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
 * Aus einer angenommenen Abschlagsrechnung wird der Abschlag, den eine
 * Schlussrechnung absetzt.
 *
 * Die Steueranteile sind die Steueraufschlüsselung der Abschlagsrechnung,
 * Zeile für Zeile – dieselben Zahlen, die auf ihrem PDF stehen, weil sie aus
 * derselben [CalculatedInvoice] kommen.
 *
 * Nur eine [AcceptedInvoice]: Aus einer Abschlagsrechnung mit Abbruch wird kein
 * Abschlag, so wie aus ihr kein PDF wird (ADR-0007).
 *
 * `vereinnahmt-am` wird übernommen, wenn die Abschlagsrechnung es trägt: Dort
 * hält es eine Vereinnahmung vor der Rechnungsstellung fest, dieselbe Tatsache
 * wie im Abschlag.
 *
 * `private`: Der einzige Aufrufer steht darüber, und sein `when` über
 * `InvoiceType` ohne `else` hält fest, dass nur eine Abschlagsrechnung
 * hierherkommt (#281). Der `else`-Zweig unten ist damit unerreichbar und
 * trotzdem gewollt – kein vergessenes `when` wie im Renderer (#128): Dort
 * braucht jede Rechnungsart eine eigene Entscheidung, hier ist genau eine
 * richtig, und eine neue gehört zu Recht zu den übrigen. Ein `when` über
 * `InvoiceKind` ohne `else` bräuchte stattdessen vier Zweige, drei davon tot.
 * Die Vorprüfung am flachen Modell bleibt, damit ein fremdes Dokument gar
 * nicht erst gerechnet wird (#118).
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
