package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.calculation.DeductedPrepayment
import de.fluchitconsulting.rechnungsgenerator.model.CancelledInvoice
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType
import java.time.LocalDate

/**
 * Die Rechnungsart samt der Angaben, die nur zu ihr gehören – als Typ
 * (ADR-0008).
 *
 * In der YAML-Datei und in [de.fluchitconsulting.rechnungsgenerator.model.Invoice] sind
 * `storniert`, `vereinnahmt-am` und `abschlaege` optional, weil dort jede
 * Kombination stehen kann und die Regeln sie in der Sprache der Rechnungsart
 * melden sollen. Dahinter gibt es nur noch diesen Wert: Eine Stornorechnung
 * trägt ihren Bezug, eine Schlussrechnung ihre Abschläge, jeden mit
 * feststehender Vereinnahmung. Wer ihn liest, muss nicht mehr mit Kombinationen
 * rechnen, die die Prüfung schon ausgeschlossen hat.
 *
 * Gebaut wird er nur in `InvoiceKindRules`, zusammen mit den Regeln, ohne die er
 * nicht baubar ist.
 */
sealed interface InvoiceKind {
    /**
     * Rechnung und Teilrechnung: keine Angaben über die gemeinsamen hinaus. Die
     * Teilrechnung unterscheidet sich nur im Code (326), deshalb trägt die
     * Variante den [InvoiceType] mit.
     */
    data class Ordinary(val type: InvoiceType) : InvoiceKind

    /** Die Abschlagsrechnung. Ihre Vereinnahmung ist fachlich optional – steht sie noch nicht fest, entfällt sie. */
    data class PrepaymentInvoice(val receivedOn: LocalDate?) : InvoiceKind

    /** Die Schlussrechnung mit ihren abgesetzten Abschlägen, in der Reihenfolge der Rechnungsdatei. */
    data class FinalInvoice(val deductions: List<Deduction>) : InvoiceKind

    /** Die Stornorechnung und die Ursprungsrechnung, die sie aufhebt (ADR-0006). */
    data class Cancellation(val cancelled: CancelledInvoice) : InvoiceKind
}

/**
 * Ein abgesetzter Abschlag, dessen Vereinnahmung feststeht.
 *
 * §14 Abs. 5 Satz 2 UStG setzt ab, was vereinnahmt wurde. Im flachen Modell ist
 * [receivedOn] optional, damit ABSCHLAG_OHNE_VEREINNAHMUNG erklären kann, warum
 * ein unbezahlter Abschlag nicht abgesetzt wird. Hier ist es das nicht mehr,
 * und der Renderer braucht dafür kein `checkNotNull`.
 */
data class Deduction(val deducted: DeductedPrepayment, val receivedOn: LocalDate)
