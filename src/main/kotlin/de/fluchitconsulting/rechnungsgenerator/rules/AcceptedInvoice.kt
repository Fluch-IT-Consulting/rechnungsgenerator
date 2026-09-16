package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.calculation.InvoiceKind

/**
 * Eine gerechnete Rechnung ohne Abbruch – die einzige Form, die sich setzen
 * oder als Abschlag übernehmen lässt (ADR-0007).
 *
 * ADR-0004 sagt: "Die Zahlen entstehen, gesetzt werden sie nie: Das Kommando
 * bricht vorher ab." Bis #70 stimmte das nur, weil beide Kommandos daran
 * dachten. `InvoiceRenderer.render` und das Zusammentragen der Abschläge
 * nahmen jede [CalculatedInvoice] an, auch eine mit Abbrüchen. Jetzt verlangen
 * sie diesen Typ, und den gibt es nur über [check]: Eine Rechnung mit Abbruch
 * ist nicht mehr ungesetzt, sondern nicht setzbar – derselbe Zug wie beim
 * Zahlungsziel.
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
class AcceptedInvoice private constructor(
    val calculated: CalculatedInvoice,
    val kind: InvoiceKind,
    /**
     * Der Dateiname ohne Endung. Auf der [CalculatedInvoice] kann er fehlen,
     * hier nicht mehr: Wer setzt, braucht keinen eigenen Abbruch dafür (#150).
     */
    val fileBaseName: String,
    /** Die Hinweise. Abbrüche hat eine angenommene Rechnung nicht – dafür steht der Typ. */
    val hints: List<Finding>,
) {
    companion object {
        /**
         * Prüft die Rechnung und nimmt sie an – oder lehnt sie ab, mit allen
         * Befunden.
         *
         * Angenommen wird nur, was keinen Abbruch hat **und** dessen Rechnungsart
         * sich bauen lässt. Ohne gebaute Rechnungsart gibt es keine
         * [AcceptedInvoice], das hält der Typ. Ebenso ohne Dateinamen – den
         * Grund dafür nennen `RECHNUNGSNUMMER_FEHLT` und
         * `RECHNUNGSNUMMER_OHNE_DATEINAMEN`, und dass keine Ablehnung ohne
         * Grund bleibt, verlangt ein Test in `AcceptedInvoiceTest`.
         *
         * Geprüft wird hier und nicht in [CalculatedInvoice] (#105). Das
         * Zahlungsziel ist ein abgeleiteter Wert, die Befunde sind ein Urteil
         * über die Rechnung. Als `lazy` auf ihr hatte die Prüfung keinen Besitzer:
         * Die Rechnungsart wurde dort gemerkt, damit Regelgruppe und diese Stelle
         * aus einem Bau lasen (#93), und dass eine Ablehnung ihre Gründe nannte,
         * hing an einem Eintrag in der Liste der Regelgruppen. Jetzt ist der Bau
         * eine lokale Variable, und seine Befunde reicht diese Stelle selbst
         * weiter. Dass jeder davon ein Abbruch ist, verlangt ein Test in
         * `InvoiceKindRulesTest`.
         *
         * Einen Einstieg, der gleich aus Rechnung und Stammdaten ein [Verdict]
         * macht, gibt es bewusst nicht. Aufgerufen wird die Folge aus
         * `InvoiceCalculator.calculate` und [check] nur zweimal, und die
         * Regeltests brauchen `calculate` ohnehin einzeln: Sie rechnen gerade
         * Rechnungen mit Abbruch und lesen deren Befunde (ADR-0004).
         */
        fun check(calculated: CalculatedInvoice): Verdict {
            val built = InvoiceKindRules.build(calculated)
            val findings = InvoiceRules.check(calculated, built)
            val hasAbort = findings.any { it.severity == Severity.ABORT }
            return when (built) {
                is InvoiceKindRules.Result.Built -> {
                    // Eine lokale val, damit der Compiler nach der Prüfung auf
                    // null weiß, dass sie ein String ist (Smart Cast). Bei
                    // einem Getter wie calculated.fileBaseName darf er das nicht.
                    val fileBaseName = calculated.fileBaseName
                    if (hasAbort || fileBaseName == null) {
                        Verdict.Rejected(findings)
                    } else {
                        Verdict.Accepted(AcceptedInvoice(calculated, built.kind, fileBaseName, findings))
                    }
                }
                is InvoiceKindRules.Result.NotBuilt -> {
                    Verdict.Rejected(findings)
                }
            }
        }
    }
}

/**
 * Das Urteil der Prüfung ([AcceptedInvoice.check]): angenommen oder abgelehnt,
 * wie im Glossar.
 *
 * Bis #125 hieß der Typ nach dem Vorgang statt nach dem Ergebnis. Der Name
 * sagte nur, dass geprüft wurde – nicht was, und nicht, was herauskam; die
 * Bedeutung trugen allein die beiden Fälle.
 *
 * `sealed`: Es gibt genau diese beiden Fälle, und der Compiler weiß das. Ein
 * `when` darüber braucht deshalb keinen `else`-Zweig – und kommt ein dritter
 * Fall dazu, wird jedes `when` ohne ihn zum Übersetzungsfehler.
 */
sealed interface Verdict {
    /** Alle Befunde der Prüfung, in der Reihenfolge der Regelgruppen (#49). */
    val findings: List<Finding>

    data class Accepted(val invoice: AcceptedInvoice) : Verdict {
        // Einer angenommenen Rechnung bleiben nur Hinweise. Gehalten werden sie
        // einmal, in der Rechnung selbst.
        override val findings: List<Finding> get() = invoice.hints
    }

    /** Alle Befunde, auch die Hinweise. Was abbricht, steht in [aborts]. */
    data class Rejected(override val findings: List<Finding>) : Verdict {
        val aborts: List<Finding> get() = findings.filter { it.severity == Severity.ABORT }
    }
}
