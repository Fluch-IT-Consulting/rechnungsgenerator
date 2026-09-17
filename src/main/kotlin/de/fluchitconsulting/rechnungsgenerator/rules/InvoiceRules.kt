package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice

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
 * außen bleibt `AcceptedInvoice.check` die einzige Schnittstelle.
 */
internal object InvoiceRules {
    /**
     * Die Befunde aller Gruppen. Die der Rechnungsart kommen aus dem Bau, den der
     * Aufrufer schon in der Hand hat: Er braucht ihn ohnehin, und so gibt es ihn
     * nur einmal (#93). Weitergereicht werden sie hier, an ihrer Stelle in der
     * Reihenfolge – nicht als Eintrag, der in einer Liste fehlen könnte, ohne dass
     * es jemand merkt (#105).
     */
    fun check(calculated: CalculatedInvoice, kind: InvoiceKindRules.Result): List<Finding> =
        // `flatten` macht aus der Liste von Listen eine einzige.
        listOf(
            InvoiceNumberRules.check(calculated),
            kind.findings,
            CancellationRules.check(calculated),
            ItemRules.check(calculated),
            TaxCategoryRules.check(calculated),
            ServicePeriodRules.check(calculated),
            PrepaymentRules.check(calculated),
            FinalInvoiceRules.check(calculated),
            PaymentTermRules.check(calculated),
            SkontoRules.check(calculated),
            PlausibilityHints.check(calculated),
            VatIdCheckRules.check(calculated),
        ).flatten()
}
