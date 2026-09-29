package de.fluchitconsulting.rechnungsgenerator.core.accepted

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.rules.Finding
import de.fluchitconsulting.rechnungsgenerator.core.rules.InvoiceKind
import de.fluchitconsulting.rechnungsgenerator.core.rules.InvoiceKindRules
import de.fluchitconsulting.rechnungsgenerator.core.rules.InvoiceRules
import de.fluchitconsulting.rechnungsgenerator.core.rules.Severity

/**
 * Die einzige Form, die sich setzen oder als Abschlag übernehmen lässt (ADR-0007). Die Rechnungsart steht in
 * [kind] und nicht im flachen Modell hinter [calculated] (ADR-0008).
 */
class AcceptedInvoice private constructor(
    val calculated: CalculatedInvoice,
    val kind: InvoiceKind,
    /** Hier nicht mehr `null`: Wer setzt, braucht keinen eigenen Abbruch dafür (#150). */
    val fileBaseName: String,
    /** Die Hinweise. Abbrüche hat eine angenommene Rechnung nicht – dafür steht der Typ. */
    val hints: List<Finding>,
) {
    companion object {
        /**
         * Angenommen wird nur ohne Abbruch, mit gebauter Rechnungsart und mit Dateinamen. Geprüft hier und nicht in der
         * [CalculatedInvoice]: Befunde sind ein Urteil und kein abgeleiteter Wert (#105).
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

sealed interface Verdict {
    /** Alle Befunde der Prüfung, in der Reihenfolge der Regelgruppen (#49). */
    val findings: List<Finding>

    data class Accepted(val invoice: AcceptedInvoice) : Verdict {
        // Die Hinweise hält die angenommene Rechnung selbst.
        override val findings: List<Finding> get() = invoice.hints
    }

    /** Alle Befunde, auch die Hinweise. Was abbricht, steht in [aborts]. */
    data class Rejected(override val findings: List<Finding>) : Verdict {
        val aborts: List<Finding> get() = findings.filter { it.severity == Severity.ABORT }
    }
}
