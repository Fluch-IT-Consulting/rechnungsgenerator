package de.fluchitconsulting.rechnungsgenerator.core.accepted

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.rules.rules
import de.fluchitconsulting.rechnungsgenerator.core.rules.verdict

/**
 * Die angenommene Rechnung für Tests, die setzen oder einen Abschlag übernehmen
 * (ADR-0007). Hat die Testrechnung einen Abbruch, scheitert der Test hier – mit
 * den Kennungen, statt später an einer Stelle, die davon nichts weiß.
 *
 * Über `verdict()` aus den Regeltests und nicht über [AcceptedInvoice.check]
 * direkt: Dort sitzt das Netz über die Schreibweise der Meldungen, und die
 * Hinweise einer angenommenen Rechnung sind auch Meldungen.
 */
fun CalculatedInvoice.accepted(): AcceptedInvoice =
    when (val verdict = verdict()) {
        is Verdict.Accepted -> verdict.invoice
        is Verdict.Rejected -> error("Die Testrechnung hat Abbrüche: ${verdict.aborts.rules()}")
    }
