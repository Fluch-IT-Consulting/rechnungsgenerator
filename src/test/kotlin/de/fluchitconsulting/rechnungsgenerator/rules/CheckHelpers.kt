package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice

// Wie die Regeltests prüfen, seit der Calculator sammelt statt zu werfen (#49):
// nach der Kennung der eigenen Regel, nicht nach dem Wortlaut irgendeiner
// Meldung. Andere Befunde stören den Test damit nicht mehr – vorher musste jede
// Testrechnung alle übrigen Regeln bestehen und durfte keinen Hinweis auslösen.
//
// Die Befunde kommen aus der Prüfung selbst, gleich ob sie annimmt oder ablehnt
// (#105).
private fun CalculatedInvoice.findings(): List<Finding> = AcceptedInvoice.check(this).findings

fun CalculatedInvoice.aborts(): List<Finding> = findings().filter { it.severity == Severity.ABORT }

fun CalculatedInvoice.hints(): List<Finding> = findings().filter { it.severity == Severity.HINT }

fun List<Finding>.rules(): List<Rule> = map { it.rule }

/** Die Zeile zu einer Regel, so wie ein Mensch sie zu sehen bekommt. */
fun List<Finding>.messageOf(rule: Rule): String =
    firstOrNull { it.rule == rule }?.describe()
        ?: error("Kein Befund zu $rule. Gefunden: ${rules()}")

fun CalculatedInvoice.rules(): List<Rule> = findings().rules()

fun CalculatedInvoice.messageOf(rule: Rule): String = findings().messageOf(rule)

/**
 * Die angenommene Rechnung für Tests, die setzen oder einen Abschlag übernehmen
 * (ADR-0007). Hat die Testrechnung einen Abbruch, scheitert der Test hier – mit
 * den Kennungen, statt später an einer Stelle, die davon nichts weiß.
 */
fun CalculatedInvoice.accepted(): AcceptedInvoice =
    when (val verdict = AcceptedInvoice.check(this)) {
        is Verdict.Accepted -> verdict.invoice
        is Verdict.Rejected -> error("Die Testrechnung hat Abbrüche: ${verdict.aborts.rules()}")
    }
