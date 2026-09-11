package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.model.Prepayment
import de.fluchitconsulting.rechnung.model.SellerConfig
import de.fluchitconsulting.rechnung.model.TaxShare
import de.fluchitconsulting.rechnung.values.InvoiceType

// Der fachliche Teil von `rechnungsgenerator abschlaege` (ADR-0003): Aus
// Abschlagsrechnungen werden die Abschläge einer Schlussrechnung. Ohne Dateien
// und ohne Ausgabe, damit sich jede Regel hier ohne beides prüfen lässt.

/**
 * Aus einer berechneten Abschlagsrechnung wird der Abschlag, den eine
 * Schlussrechnung absetzt.
 *
 * Die Steueranteile sind die Steueraufschlüsselung der Abschlagsrechnung,
 * Zeile für Zeile – dieselben Zahlen, die auf ihrem PDF stehen, weil sie aus
 * demselben [InvoiceCalculator] kommen.
 *
 * `vereinnahmt-am` wird übernommen, wenn die Abschlagsrechnung es trägt: Dort
 * hält es eine Vereinnahmung vor der Rechnungsstellung fest, dieselbe Tatsache
 * wie im Abschlag.
 */
fun prepaymentOf(invoice: Invoice, totals: InvoiceTotals): Prepayment {
    // Keine Regel der Schlussrechnung kennt die Art des Dokuments, aus dem ein
    // Abschlag stammt – eine gewöhnliche Rechnung ginge dort klaglos durch.
    if (invoice.type != InvoiceType.PREPAYMENT) {
        throw InvoiceValidationException(
            "${invoice.number} ist eine ${invoice.type.documentName}, keine Abschlagsrechnung. " +
                "Abgesetzt werden nur Abschläge.",
        )
    }
    return Prepayment(
        invoiceNumber = invoice.number,
        issueDate = invoice.issueDate,
        receivedOn = invoice.receivedOn,
        taxShares = totals.vatGroups.map { TaxShare(it.category, it.rate, net = it.base, vat = it.amount) },
    )
}

/**
 * Die Abschläge aus mehreren Abschlagsrechnungen, in der übergebenen Reihenfolge.
 *
 * Geprüft wird hier, was die Schlussrechnung später nicht mehr sehen kann: dass
 * alle Abschlagsrechnungen an denselben Kunden gingen und keine doppelt dabei
 * ist. Beides vor dem Rechnen, damit ein Fehler in der Auswahl als solcher
 * gemeldet wird und nicht als Folgefehler einer einzelnen Datei.
 *
 * Die Hinweise des Calculators fallen weg: Sie galten der Abschlagsrechnung,
 * als sie entstand, nicht der Schlussrechnung von heute.
 */
fun prepaymentsOf(abschlagsrechnungen: List<Invoice>, config: SellerConfig): List<Prepayment> {
    // groupBy bildet eine Map vom Kundennamen auf die Abschlagsrechnungen an
    // diesen Kunden; mehr als ein Schlüssel heißt: mehr als ein Kunde.
    val byCustomer = abschlagsrechnungen.groupBy { it.customer.name }
    if (byCustomer.size > 1) {
        val customers =
            byCustomer.entries.joinToString(", ") { (name, invoices) ->
                "$name (${invoices.joinToString(", ") { it.number }})"
            }
        throw InvoiceValidationException(
            "Die Abschlagsrechnungen gingen an verschiedene Kunden: $customers. " +
                "Eine Schlussrechnung setzt nur Abschläge desselben Kunden ab.",
        )
    }

    // Normalisiert verglichen wie Regel 7 der Schlussrechnung: "R26-009 " und
    // "r26-009" meinen dasselbe Dokument.
    abschlagsrechnungen
        .groupBy { it.number.trim().uppercase() }
        .values
        .firstOrNull { it.size > 1 }
        ?.let { duplicates ->
            throw InvoiceValidationException(
                "Die Abschlagsrechnung ${duplicates.first().number.trim()} ist zweimal angegeben.",
            )
        }

    return abschlagsrechnungen.map { abschlagsrechnung ->
        // `try` ist in Kotlin ein Ausdruck: Sein Wert ist der des letzten
        // Ausdrucks im Block, hier also das Ergebnis des Calculators.
        // Die Nummer kommt davor, weil die Meldungen des Calculators nur
        // "Position 1 (…)" sagen – bei mehreren Abschlagsrechnungen bliebe
        // offen, in welcher.
        val totals =
            try {
                InvoiceCalculator.calculate(abschlagsrechnung, config)
            } catch (e: InvoiceValidationException) {
                throw InvoiceValidationException("${abschlagsrechnung.number}: ${e.message}")
            }
        prepaymentOf(abschlagsrechnung, totals)
    }
}
