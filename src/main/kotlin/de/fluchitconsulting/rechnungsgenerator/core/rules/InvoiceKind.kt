package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.DeductedPrepayment
import de.fluchitconsulting.rechnungsgenerator.core.model.CancelledInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import java.time.LocalDate

/**
 * Die Rechnungsart samt ihrer Angaben als Typ, gebaut nur in `InvoiceKindRules` (ADR-0008). Wer ihn liest, rechnet
 * nicht mehr mit Kombinationen, die die Prüfung schon ausgeschlossen hat.
 */
sealed interface InvoiceKind {
    /** Rechnung und Teilrechnung, die sich nur im Code unterscheiden. */
    data class Ordinary(val type: InvoiceType) : InvoiceKind

    /** Steht die Vereinnahmung noch nicht fest, entfällt sie. */
    data class PrepaymentInvoice(val receivedOn: LocalDate?) : InvoiceKind

    data class FinalInvoice(val deductions: List<Deduction>) : InvoiceKind

    data class Cancellation(val cancelled: CancelledInvoice) : InvoiceKind
}

/** Ein abgesetzter Abschlag mit feststehender Vereinnahmung (§14 Abs. 5 Satz 2 UStG, ABSCHLAG_OHNE_VEREINNAHMUNG). */
data class Deduction(val deducted: DeductedPrepayment, val receivedOn: LocalDate)
