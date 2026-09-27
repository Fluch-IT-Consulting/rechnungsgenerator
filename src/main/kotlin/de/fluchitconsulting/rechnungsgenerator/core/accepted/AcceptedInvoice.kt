package de.fluchitconsulting.rechnungsgenerator.core.accepted

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.rules.Finding
import de.fluchitconsulting.rechnungsgenerator.core.rules.InvoiceKind
import de.fluchitconsulting.rechnungsgenerator.core.rules.InvoiceKindRules
import de.fluchitconsulting.rechnungsgenerator.core.rules.InvoiceRules
import de.fluchitconsulting.rechnungsgenerator.core.rules.Severity

/**
 * Eine gerechnete Rechnung ohne Abbruch – die einzige Form, die sich setzen
 * oder als Abschlag übernehmen lässt (ADR-0007).
 *
 * ADR-0004 sagt: "Die Zahlen entstehen, gesetzt werden sie nie: Das Kommando
 * bricht vorher ab." Dass das hält, verlangt der Compiler und nicht die
 * Disziplin der Kommandos (#70): `InvoiceRenderer.render` und das
 * Zusammentragen der Abschläge verlangen diesen Typ, und den gibt es nur über
 * [check]. Eine Rechnung mit Abbruch ist nicht ungesetzt, sondern nicht
 * setzbar – derselbe Zug wie beim Zahlungsziel.
 *
 * Sie trägt die Rechnungsart als Typ, [kind] (ADR-0008). Wer wissen will, ob
 * und worauf sich ein Storno bezieht oder welche Abschläge abgesetzt werden,
 * fragt dort und nicht im flachen Modell hinter [calculated].
 *
 */
class AcceptedInvoice private constructor(
    val calculated: CalculatedInvoice,
    val kind: InvoiceKind,
    /**
     * Der Jobname für LuaLaTeX. Auf der [CalculatedInvoice] kann er fehlen,
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
         * über die Rechnung. Als `lazy` auf ihr hätte die Prüfung keinen
         * Besitzer. Der Bau der Rechnungsart ist deshalb eine lokale Variable,
         * und seine Befunde reicht diese Stelle selbst weiter; dass jeder davon
         * ein Abbruch ist, verlangt ein Test in `InvoiceKindRulesTest`.
         *
         * Einen Einstieg, der gleich aus Rechnung und Stammdaten ein [Verdict]
         * macht, gibt es bewusst nicht. Aufgerufen wird die Folge aus
         * [CalculatedInvoice] und [check] nur zweimal, und die Regeltests
         * brauchen die [CalculatedInvoice] ohnehin einzeln: Sie rechnen gerade
         * Rechnungen mit Abbruch und lesen deren Befunde (ADR-0004).
         */
        fun check(calculated: CalculatedInvoice): Verdict {
            val kindCheck = InvoiceKindRules.check(calculated)
            val findings = InvoiceRules.check(calculated, kindCheck)
            val hasAbort = findings.any { it.severity == Severity.ABORT }
            return when (kindCheck) {
                is InvoiceKindRules.Result.Built -> {
                    val fileBaseName = calculated.fileBaseName
                    if (hasAbort || fileBaseName == null) {
                        Verdict.Rejected(findings)
                    } else {
                        Verdict.Accepted(AcceptedInvoice(calculated, kindCheck.kind, fileBaseName, findings))
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
