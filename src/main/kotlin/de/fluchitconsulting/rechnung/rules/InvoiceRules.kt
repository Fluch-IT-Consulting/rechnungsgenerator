package de.fluchitconsulting.rechnung.rules

import de.fluchitconsulting.rechnung.CalculatedInvoice
import de.fluchitconsulting.rechnung.Finding

/**
 * Alle Regelgruppen einer Rechnung – in der Reihenfolge, in der ihre Befunde
 * erscheinen.
 *
 * Gesammelt statt geworfen (ADR-0004): Wer eine Regel prüft, soll nicht erst
 * eine Rechnung bauen müssen, die alle anderen besteht.
 *
 * Jede Gruppe prüft das fertig gerechnete Ergebnis (#71). Vorher standen die
 * Regeln im Calculator zwischen den Rechenschritten, und weil zwei davon die
 * Summen brauchten, entstand das Ergebnis zweimal.
 *
 * Eine Gruppe je Datei, und je Datei ein Test gleichen Namens – so waren die
 * Tests schon lange geteilt. Nicht je Regel eine Funktion: Das hat ADR-0004
 * verworfen, die Meldung steht weiter neben ihrer Bedingung.
 *
 * `internal` heißt: sichtbar im ganzen Modul, aber nicht darüber hinaus. Nach
 * außen bleibt [CalculatedInvoice.findings] die einzige Schnittstelle.
 */
internal object InvoiceRules {
    // `::check` ist eine Funktionsreferenz: die Funktion selbst als Wert, ohne
    // sie aufzurufen. Die Liste sagt damit nur, welche Gruppen es gibt und in
    // welcher Reihenfolge – die Hinweise bleiben in der Reihenfolge von vorher.
    private val groups: List<(CalculatedInvoice) -> List<Finding>> =
        listOf(
            InvoiceNumberRules::check,
            InvoiceKindRules::check,
            CancellationRules::check,
            ItemRules::check,
            TaxCategoryRules::check,
            ServicePeriodRules::check,
            PrepaymentRules::check,
            FinalInvoiceRules::check,
            PaymentTermRules::check,
            PlausibilityHints::check,
            VatIdCheckRules::check,
        )

    fun check(calculated: CalculatedInvoice): List<Finding> = groups.flatMap { it(calculated) }
}
