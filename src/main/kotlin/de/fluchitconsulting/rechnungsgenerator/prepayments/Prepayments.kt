package de.fluchitconsulting.rechnungsgenerator.prepayments

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.model.Prepayment
import de.fluchitconsulting.rechnungsgenerator.model.SellerConfig
import de.fluchitconsulting.rechnungsgenerator.model.TaxShare
import de.fluchitconsulting.rechnungsgenerator.rules.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.rules.Finding
import de.fluchitconsulting.rechnungsgenerator.rules.InvoiceKind
import de.fluchitconsulting.rechnungsgenerator.rules.Rule
import de.fluchitconsulting.rechnungsgenerator.rules.Subject
import de.fluchitconsulting.rechnungsgenerator.rules.Verdict
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType

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
 * `sealed` wie [Verdict]: genau diese beiden Fälle, und ein `when` darüber
 * braucht keinen `else`. Bis #118 war das ein Paar aus einer Liste und den
 * Befunden, und die Liste war laut Kommentar "nur vollständig, wenn kein
 * Abbruch gemeldet ist" – geprüft hat das das Kommando. Jetzt gibt es die
 * Liste nur, wenn sie vollständig ist (ADR-0003: alles oder nichts), und die
 * Zusage hält der Typ, nicht ein Filter im Aufrufer.
 *
 * Hinweise trägt keiner der beiden Fälle: Die der einzelnen Abschlagsrechnungen
 * fallen weg (ADR-0003), und die Regeln des Zusammentragens selbst sind alle
 * Abbrüche.
 */
sealed interface CollectedPrepayments {
    /** Die Abschläge in der übergebenen Reihenfolge, jeder mit seiner Herkunft. */
    data class Complete(val entries: List<CollectedPrepayment>) : CollectedPrepayments

    /** Kein Block: die Abbrüche, jeder mit der Rechnung, aus der er stammt. */
    data class Rejected(val aborts: List<Finding>) : CollectedPrepayments
}

/**
 * Die Abschläge aus mehreren Abschlagsrechnungen, in der übergebenen Reihenfolge.
 *
 * Geprüft wird hier, was die Schlussrechnung später nicht mehr sehen kann: dass
 * alle Abschlagsrechnungen an denselben Kunden gingen, dass keine doppelt dabei
 * ist und dass jede wirklich eine Abschlagsrechnung ist.
 *
 * Ein Dokument anderer Art wird gar nicht erst gerechnet und geprüft: Es fliegt
 * ohnehin aus dem Aufruf, und seine übrigen Befunde hülfen niemandem – sie
 * gälten einem Dokument, das hier nichts zu suchen hat. Bis #118 lief die
 * ganze Prüfung trotzdem darüber.
 *
 * Die Hinweise der einzelnen Rechnungen fallen weg: Sie galten der
 * Abschlagsrechnung, als sie entstand, nicht der Schlussrechnung von heute.
 * Ihre Abbrüche kommen mit, mit der Herkunft davor.
 */
fun prepaymentsOf(abschlagsrechnungen: List<NamedInvoice>, config: SellerConfig): CollectedPrepayments {
    val findings = mutableListOf<Finding>()

    // groupBy bildet eine Map vom Kundennamen auf die Abschlagsrechnungen an
    // diesen Kunden; mehr als ein Schlüssel heißt: mehr als ein Kunde.
    val byCustomer = abschlagsrechnungen.groupBy { it.invoice.customer.name }
    if (byCustomer.size > 1) {
        val customers =
            byCustomer.entries.joinToString(", ") { (name, invoices) ->
                "$name (${invoices.joinToString(", ") { it.invoice.number }})"
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
    // Je Nummer ein Befund, mit ihrer Anzahl – wie in der Schlussrechnung (#152).
    abschlagsrechnungen
        .groupBy { it.invoice.number }
        .filterValues { it.size > 1 }
        .forEach { (nummer, duplicates) ->
            findings +=
                Finding(
                    Rule.ABSCHLAGSRECHNUNG_DOPPELT,
                    Subject.FromInvoice(nummer, Subject.WholeInvoice),
                    "ist ${duplicates.size}-mal angegeben.",
                )
        }

    // mapNotNull: wie map, aber was `null` ergibt, fällt aus der Liste – hier
    // die abgelehnten Abschlagsrechnungen. Die Liste zählt am Ende nur, wenn
    // dabei kein Befund entstanden ist; sonst gibt es sie gar nicht.
    //
    // `(name, abschlagsrechnung)` in der Lambda-Parameterliste zerlegt das
    // NamedInvoice in seine beiden Felder ("destructuring").
    val entries =
        abschlagsrechnungen.mapNotNull { (name, abschlagsrechnung) ->
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
                return@mapNotNull null
            }

            when (val verdict = AcceptedInvoice.check(CalculatedInvoice(abschlagsrechnung, config))) {
                is Verdict.Accepted -> {
                    CollectedPrepayment(name, prepaymentOf(verdict.invoice))
                }
                is Verdict.Rejected -> {
                    findings += verdict.aborts.map { it.copy(subject = Subject.FromInvoice(abschlagsrechnung.number, it.subject)) }
                    null
                }
            }
        }

    return if (findings.isEmpty()) CollectedPrepayments.Complete(entries) else CollectedPrepayments.Rejected(findings)
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
 * `private`: Der einzige Aufrufer steht darüber, und der hat die Art schon
 * geprüft. Deshalb ist der `else`-Zweig unten unerreichbar. Gewollt ist er
 * trotzdem, und kein vergessenes `when` wie im Renderer vor #128: Dort braucht
 * jede Rechnungsart eine eigene Entscheidung, hier ist genau eine richtig, und
 * eine neue gehört zu Recht zu den übrigen. Die Vorprüfung am flachen Modell
 * bleibt, damit ein fremdes Dokument gar nicht erst gerechnet wird (#118).
 */
private fun prepaymentOf(abschlagsrechnung: AcceptedInvoice): Prepayment {
    val calculatedInvoice = abschlagsrechnung.calculated
    val invoice = calculatedInvoice.invoice
    val receivedOn =
        when (val kind = abschlagsrechnung.kind) {
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
