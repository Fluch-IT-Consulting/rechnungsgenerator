package de.fluchitconsulting.rechnungsgenerator.prepayments

import de.fluchitconsulting.rechnungsgenerator.rules.rules

// Wie die Tests des Zusammentragens an ihr Ergebnis kommen – das Gegenstück zu
// `accepted()` in `rules/CheckHelpers.kt` (#118): Wer mit den Einträgen rechnet,
// scheitert bei einer Ablehnung hier, mit den Kennungen, statt später an einer
// Stelle, die davon nichts weiß. Und umgekehrt.

fun CollectedPrepayments.complete(): CollectedPrepayments.Complete =
    when (this) {
        is CollectedPrepayments.Complete -> this
        is CollectedPrepayments.Rejected -> error("Das Zusammentragen wurde abgelehnt: ${aborts.rules()}")
    }

fun CollectedPrepayments.rejected(): CollectedPrepayments.Rejected =
    when (this) {
        is CollectedPrepayments.Rejected -> this
        is CollectedPrepayments.Complete -> error("Das Zusammentragen wurde angenommen, mit ${entries.size} Abschlägen.")
    }
