package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Daten in der Sprache einer Rechnung; Zahlen formatiert die Vorlage (ADR-0021). Immer mit dem `Locale` der
 * Sprache, nie dem der JVM – sonst hinge die Rechnung an der Einstellung des Rechners.
 */
class DateFormat(
    private val language: Language,
) {
    /**
     * `14. August 2026`, `August 14, 2026`. Das geschützte Leerzeichen hält Tag und Monat zusammen, und der Punkt
     * nach dem Tag bekommt keinen Satzabstand (#143).
     */
    fun date(date: LocalDate): Latex = format(longDatePattern("\u00A0"), date).escapeLatex()

    /** Für die .xmpdata, maskiert wie sie (#485). */
    fun dateInMetadata(date: LocalDate): Latex = format(longDatePattern(" "), date).escapeXmpData()

    /** Ein voller Kalendermonat genügt als `Juli 2026`, §31 Abs. 4 UStDV. */
    fun servicePeriod(start: LocalDate, end: LocalDate): Latex =
        if (start == end) {
            shortDate(start)
        } else if (start.dayOfMonth == 1 && end == end.withDayOfMonth(end.lengthOfMonth()) &&
            start.withDayOfMonth(1) == end.withDayOfMonth(1)
        ) {
            format(MONTH_WITH_YEAR, start).escapeLatex()
        } else {
            "${shortDateText(start)}\u2009–\u2009${shortDateText(end)}".escapeLatex()
        }

    /**
     * `Juni bis Oktober 2026`, für den voraussichtlichen Zeitraum (UStAE 14.8). Nur bei vollen Randmonaten, sonst
     * wäre es keine gröbere Angabe, sondern eine andere.
     */
    fun monthPeriod(start: LocalDate, end: LocalDate): Latex {
        val until =
            when (language) {
                Language.GERMAN -> "bis"
                Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> "to"
            }
        val fillsWholeMonths = start.dayOfMonth == 1 && end == end.withDayOfMonth(end.lengthOfMonth())
        if (!fillsWholeMonths) return servicePeriod(start, end)

        return when {
            YearMonth.from(start) == YearMonth.from(end) -> format(MONTH_WITH_YEAR, start).escapeLatex()
            start.year == end.year -> "${format(MONTH, start)} $until ${format(MONTH_WITH_YEAR, end)}".escapeLatex()
            else -> "${format(MONTH_WITH_YEAR, start)} $until ${format(MONTH_WITH_YEAR, end)}".escapeLatex()
        }
    }

    fun month(month: YearMonth): Latex = format(MONTH_WITH_YEAR, month.atDay(1)).escapeLatex()

    fun shortDate(date: LocalDate): Latex = shortDateText(date).escapeLatex()

    // Im Englischen ISO 8601: 01/07/2026 läsen Briten und Amerikaner verschieden.
    private fun shortDateText(date: LocalDate): String =
        when (language) {
            Language.GERMAN -> format("dd.MM.yyyy", date)
            Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> format("yyyy-MM-dd", date)
        }

    private fun longDatePattern(space: String): String =
        when (language) {
            Language.GERMAN -> "d.${space}MMMM yyyy"
            Language.BRITISH_ENGLISH -> "d${space}MMMM yyyy"
            Language.AMERICAN_ENGLISH -> "MMMM${space}d, yyyy"
        }

    private fun format(pattern: String, date: LocalDate): String =
        DateTimeFormatter.ofPattern(pattern, language.locale).format(date)

    private companion object {
        const val MONTH = "MMMM"
        const val MONTH_WITH_YEAR = "MMMM yyyy"
    }
}
