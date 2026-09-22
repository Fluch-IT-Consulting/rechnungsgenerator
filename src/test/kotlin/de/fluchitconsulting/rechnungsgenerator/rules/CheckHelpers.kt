package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice

// Wie die Regeltests prüfen, seit der Calculator sammelt statt zu werfen (#49):
// nach der Kennung der eigenen Regel, nicht nach dem Wortlaut irgendeiner
// Meldung. Andere Befunde stören den Test damit nicht: Sonst müsste jede
// Testrechnung alle übrigen Regeln bestehen und dürfte keinen Hinweis auslösen.
//
// Die Befunde kommen aus der Prüfung selbst, gleich ob sie annimmt oder ablehnt
// (#105).
private fun CalculatedInvoice.findings(): List<Finding> = verdict().findings

// Jeder Regeltest geht durch diese Stelle, deshalb steht hier auch das Netz
// über die Schreibweise der Meldungen: Es gilt so für jede Meldung, die
// irgendein Test auslöst. Werte schreibt eine Meldung in der Form ihres
// toString() – Punkt statt Komma, ohne Währung, das Prozentzeichen am Wert
// (#4). Eine von Hand getippte Zahl im Satz entgeht dieser einen Stelle, und
// dann heißt derselbe Gesamtbetrag in der einen Gruppe "0,00 €" und in der
// anderen "0.00" (#282).
private fun CalculatedInvoice.verdict(): Verdict =
    AcceptedInvoice.check(this).also { verdict ->
        verdict.findings.forEach { finding ->
            check(!REPORT_FORM.containsMatchIn(finding.message)) {
                "Die Meldung zu ${finding.rule} schreibt eine Zahl wie auf dem Blatt statt wie toString(): " +
                    finding.message
            }
        }
    }

/** Komma in einer Zahl, Leerzeichen vor dem Prozentzeichen, Währungszeichen – die Form von `GermanFormat`. */
private val REPORT_FORM = Regex("""\d,\d|\d %|€""")

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
    when (val verdict = verdict()) {
        is Verdict.Accepted -> verdict.invoice
        is Verdict.Rejected -> error("Die Testrechnung hat Abbrüche: ${verdict.aborts.rules()}")
    }
