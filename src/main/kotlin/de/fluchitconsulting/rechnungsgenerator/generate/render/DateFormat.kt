package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Die Darstellung von Daten in der Sprache einer Rechnung – die
 * Ausgabeschicht, die `LocalDate` nicht kennt.
 *
 * Zahlen stehen nicht hier: Ihre Ziffern liefert [Numbers], ihre Schreibweise
 * die Vorlage (ADR-0021). Ein Datum bleibt, weil in seiner Schreibweise eine
 * Entscheidung steckt – ob ein Zeitraum „Juli 2026“ heißt oder zwei Daten
 * nennt, folgt aus §31 Abs. 4 UStDV und nicht aus dem Satzbild.
 *
 * Eine Klasse für alle Sprachen und nicht eine je Sprache: Was ein Zeitraum
 * ist, der einen ganzen Monat füllt, hängt an keiner. Je Sprache verschieden
 * sind nur die Muster, und die fragt jede Methode in einem `when` ohne `else`.
 *
 * Geliefert werden fertige [Latex]-Fragmente, keine Zeichenketten, damit die
 * Maskierung hier sitzt und nicht bei den Aufrufern.
 *
 * Alle Formate werden mit dem `Locale` der Sprache erzeugt, nie mit dem
 * Standard-Locale der JVM: Sonst hinge das Aussehen einer Rechnung davon ab,
 * wie der Rechner eingestellt ist, auf dem sie erzeugt wurde.
 */
class DateFormat(
    private val language: Language,
) {
    /**
     * Ausgeschriebenes Datum: `14. August 2026`, `14 August 2026`,
     * `August 14, 2026`.
     *
     * Zwischen Tag und Monat ein geschütztes Leerzeichen, das [escapeLatex] zu
     * `~` macht: ein fester Wortzwischenraum. Im Deutschen ist der Punkt nach dem
     * Tag kein Satzende, und ohne `~` bekäme er dort, wo die Vorlage den
     * breiten Satzabstand setzt, eine Lücke wie nach einem Satz (#143). In
     * jeder Sprache bricht das Datum so nicht zwischen Tag und Monat um.
     *
     * Nicht für die XMP-Metadaten, dort gilt [dateInMetadata].
     */
    fun date(date: LocalDate): Latex = format(longDatePattern("\u00A0"), date).escapeLatex()

    /**
     * Dasselbe Datum mit gewöhnlichem Leerzeichen, für die .xmpdata-Datei.
     *
     * pdfx liest die Felder dort als Text und nicht als Satz: Ein `~` käme als
     * Tilde in die Dokumenteigenschaften des PDF ("vom 14.~August 2026").
     * Nachgemessen mit pdfinfo; veraPDF bemängelt das nicht, es ist ja gültiger
     * Text – nur eben der falsche.
     */
    fun dateInMetadata(date: LocalDate): Latex = format(longDatePattern(" "), date).escapeLatex()

    /**
     * Leistungszeitraum in der kürzesten korrekten Form. Deckt er genau einen
     * Kalendermonat ab, genügt dessen Nennung – §31 Abs. 4 UStDV lässt den
     * Kalendermonat als Angabe des Leistungszeitpunkts ausdrücklich zu, und
     * "Juli 2026" liest sich besser als "01.07.2026–31.07.2026".
     */
    fun servicePeriod(start: LocalDate, end: LocalDate): Latex =
        if (start.dayOfMonth == 1 && end == end.withDayOfMonth(end.lengthOfMonth()) && start.withDayOfMonth(1) == end.withDayOfMonth(1)) {
            format(MONTH_WITH_YEAR, start).escapeLatex()
        } else {
            "${shortDateText(start)}\u2009–\u2009${shortDateText(end)}".escapeLatex()
        }

    /**
     * Ein Zeitraum in Kalendermonaten: `Juni bis Oktober 2026`.
     *
     * Für den voraussichtlichen Leistungszeitraum einer Abschlagsrechnung –
     * UStAE 14.8 Abs. 1 und 4 lassen dort den Monat genügen, und eine Prognose
     * auf den Tag genau zu nennen behauptet mehr, als man weiß.
     *
     * Füllt der Zeitraum die Randmonate nicht aus, bleiben die Daten stehen:
     * "Juni bis Oktober" für den 15.06. bis 20.10. wäre keine gröbere Angabe
     * mehr, sondern eine andere.
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
            // Dasselbe Jahr wird einmal genannt, nicht zweimal.
            start.year == end.year -> "${format(MONTH, start)} $until ${format(MONTH_WITH_YEAR, end)}".escapeLatex()
            else -> "${format(MONTH_WITH_YEAR, start)} $until ${format(MONTH_WITH_YEAR, end)}".escapeLatex()
        }
    }

    /** Kurzes Datum für Zeiträume: `01.07.2026`, `2026-07-01`. */
    fun shortDate(date: LocalDate): Latex = shortDateText(date).escapeLatex()

    // Ohne Maskierung, für den Zusammenbau innerhalb dieser Klasse.
    //
    // Im Englischen ISO 8601, in beiden Varianten: 01/07/2026 läse ein
    // amerikanischer Leser als 7. Januar, 07/01/2026 ein britischer als
    // 7. Juli. Das lange Datum folgt der Variante; mit ausgeschriebenem Monat
    // ist es in jeder eindeutig.
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
