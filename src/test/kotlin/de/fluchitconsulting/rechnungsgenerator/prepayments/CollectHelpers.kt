package de.fluchitconsulting.rechnungsgenerator.prepayments

import de.fluchitconsulting.rechnungsgenerator.core.rules.rules

// Das Gegenstück zu `accepted()` in `core/accepted/AcceptedHelpers.kt` (#118).

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
