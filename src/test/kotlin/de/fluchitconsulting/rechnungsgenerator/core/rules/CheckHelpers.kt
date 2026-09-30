package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.accepted.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.accepted.Verdict
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice

// Nach der Kennung der eigenen Regel, nicht dem Wortlaut: Sonst müsste jede
// Testrechnung alle übrigen Regeln bestehen (#49, #105).
private fun CalculatedInvoice.findings(): List<Finding> = verdict().findings

// Jeder Regeltest und `accepted()` gehen hier durch, deshalb steht hier das Netz
// über die Schreibweise der Meldungen: Werte in der Form ihres toString() (#4, #282),
// und kein fehlender Wert (#490).
fun CalculatedInvoice.verdict(): Verdict =
    AcceptedInvoice.check(this).also { verdict ->
        verdict.findings.forEach { finding ->
            check(!REPORT_FORM.containsMatchIn(finding.message)) {
                "Die Meldung zu ${finding.rule} schreibt eine Zahl wie auf dem Blatt statt wie toString(): " +
                    finding.message
            }
            check(!MISSING_VALUE.containsMatchIn(finding.message)) {
                "Die Meldung zu ${finding.rule} setzt einen fehlenden Wert ein: ${finding.message}"
            }
        }
    }

// Die Form des Blatts, die keine Meldung tragen darf.
private val REPORT_FORM = Regex("""\d,\d|\d %|€""")

// So schreibt ein String-Template ein nullbares Feld ohne Wert; keine Meldung darf das Wort sonst verwenden.
private val MISSING_VALUE = Regex("""\bnull\b""")

fun CalculatedInvoice.aborts(): List<Finding> = findings().filter { it.severity == Severity.ABORT }

fun CalculatedInvoice.hints(): List<Finding> = findings().filter { it.severity == Severity.HINT }

fun List<Finding>.rules(): List<Rule> = map { it.rule }

fun List<Finding>.messageOf(rule: Rule): String =
    firstOrNull { it.rule == rule }?.describe()
        ?: error("Kein Befund zu $rule. Gefunden: ${rules()}")

fun CalculatedInvoice.rules(): List<Rule> = findings().rules()

fun CalculatedInvoice.messageOf(rule: Rule): String = findings().messageOf(rule)
