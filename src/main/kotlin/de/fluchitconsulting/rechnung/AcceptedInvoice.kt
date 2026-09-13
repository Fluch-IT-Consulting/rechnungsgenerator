package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.rules.InvoiceKindRules

/**
 * Eine gerechnete Rechnung ohne Abbruch – die einzige Form, die sich setzen
 * oder als Abschlag übernehmen lässt (ADR-0007).
 *
 * ADR-0004 sagt: "Die Zahlen entstehen, gesetzt werden sie nie: Das Kommando
 * bricht vorher ab." Bis #70 stimmte das nur, weil beide Kommandos daran
 * dachten. `InvoiceRenderer.render` und `prepaymentOf` nahmen jede
 * [CalculatedInvoice] an, auch eine mit Abbrüchen. Jetzt verlangen sie diesen
 * Typ, und den gibt es nur über [check]: Eine Rechnung mit Abbruch ist nicht
 * mehr ungesetzt, sondern nicht setzbar – derselbe Zug wie beim Zahlungsziel.
 *
 * Sie trägt die Rechnungsart als Typ, [kind] (ADR-0008). Wer wissen will, ob
 * und worauf sich ein Storno bezieht oder welche Abschläge abgesetzt werden,
 * fragt dort und nicht im flachen Modell hinter [calculated].
 *
 * `private constructor`: Außerhalb dieser Klasse lässt sich keine
 * `AcceptedInvoice(...)` bauen, auch nicht im selben Paket. Der einzige Weg
 * führt durch [check], das den Konstruktor aus dem `companion object` heraus
 * aufrufen darf.
 */
class AcceptedInvoice private constructor(val calculated: CalculatedInvoice, val kind: InvoiceKind) {
    /** Die Hinweise. Abbrüche hat eine angenommene Rechnung nicht – dafür steht der Typ. */
    val hints: List<Finding> get() = calculated.findings.filter { it.severity == Severity.HINT }

    companion object {
        /**
         * Angenommen wird nur, was keinen Abbruch hat **und** dessen Rechnungsart
         * sich bauen lässt. Ohne gebaute Rechnungsart gibt es keine
         * [AcceptedInvoice], das hält der Typ.
         *
         * Dass die Ablehnung dann auch einen Grund nennt, hängt an zwei Dingen:
         * [findings][CalculatedInvoice.findings] enthält die Befunde desselben
         * Baus, den diese Stelle liest ([CalculatedInvoice.kindResult], #93) –
         * und jeder dieser Befunde ist ein Abbruch. Das Zweite verlangt ein Test
         * in `InvoiceKindRulesTest`.
         *
         * Einen Einstieg, der gleich aus Rechnung und Stammdaten ein [Checked]
         * macht, gibt es bewusst nicht. Aufgerufen wird die Folge aus
         * `InvoiceCalculator.calculate` und [check] nur zweimal, und die
         * Regeltests brauchen `calculate` ohnehin einzeln: Sie rechnen gerade
         * Rechnungen mit Abbruch und lesen deren Befunde (ADR-0004).
         */
        fun check(calculated: CalculatedInvoice): Checked {
            val aborts = calculated.findings.filter { it.severity == Severity.ABORT }
            return when (val built = calculated.kindResult) {
                is InvoiceKindRules.Result.Built ->
                    if (aborts.isEmpty()) Checked.Accepted(AcceptedInvoice(calculated, built.kind)) else Checked.Rejected(aborts)
                is InvoiceKindRules.Result.NotBuilt -> Checked.Rejected(aborts)
            }
        }
    }
}

/**
 * Das Ergebnis von [AcceptedInvoice.check]: angenommen oder abgelehnt.
 *
 * `sealed`: Es gibt genau diese beiden Fälle, und der Compiler weiß das. Ein
 * `when` darüber braucht deshalb keinen `else`-Zweig – und kommt ein dritter
 * Fall dazu, wird jedes `when` ohne ihn zum Übersetzungsfehler.
 */
sealed interface Checked {
    data class Accepted(val invoice: AcceptedInvoice) : Checked

    /** Alle Abbrüche auf einmal, in der Reihenfolge der Regelgruppen (#49). */
    data class Rejected(val aborts: List<Finding>) : Checked
}
