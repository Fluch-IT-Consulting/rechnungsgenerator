package de.fluchitconsulting.rechnungsgenerator.core.accepted

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.rules.rules
import de.fluchitconsulting.rechnungsgenerator.core.rules.verdict

/**
 * Über `verdict()` und nicht [AcceptedInvoice.check] direkt: Dort sitzt das
 * Netz über die Schreibweise der Meldungen.
 */
fun CalculatedInvoice.accepted(): AcceptedInvoice =
    when (val verdict = verdict()) {
        is Verdict.Accepted -> verdict.invoice
        is Verdict.Rejected -> error("Die Testrechnung hat Abbrüche: ${verdict.aborts.rules()}")
    }
