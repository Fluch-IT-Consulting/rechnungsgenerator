package de.fluchitconsulting.rechnungsgenerator.generate.render

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Deutsche Darstellung von Daten – die Ausgabeschicht, die `LocalDate` nicht
 * kennt.
 *
 * Zahlen stehen nicht hier: Ihre Ziffern liefert [Numbers], ihre Schreibweise
 * die Vorlage (ADR-0021). Ein Datum bleibt, weil in seiner Schreibweise eine
 * Entscheidung steckt – ob ein Zeitraum „Juli 2026“ heißt oder zwei Daten
 * nennt, folgt aus §31 Abs. 4 UStDV und nicht aus dem Satzbild.
 *
 * Geliefert werden fertige [Latex]-Fragmente, keine Zeichenketten, damit die
 * Maskierung hier sitzt und nicht bei den Aufrufern.
 *
 * Alle Formate werden mit explizitem Locale erzeugt, nie mit dem Standard-Locale
 * der JVM: Sonst hinge das Aussehen einer Rechnung davon ab, wie der Rechner
 * eingestellt ist, auf dem sie erzeugt wurde.
 */
object GermanFormat {
    /**
     * Ausgeschriebenes Datum: `14. August 2026`.
     *
     * Hinter dem Tag ein geschütztes Leerzeichen, das [escapeLatex] zu `~`
     * macht: ein fester Wortzwischenraum. Der Punkt nach dem Tag ist kein
     * Satzende, und ohne `~` bekäme er dort, wo die Vorlage den breiten
     * Satzabstand setzt, eine Lücke wie nach einem Satz (#143). Nebenbei bricht
     * das Datum nicht zwischen Tag und Monat um.
     *
     * Nicht für die XMP-Metadaten, dort gilt [dateInMetadata].
     */
    fun date(date: LocalDate): Latex =
        DateTimeFormatter.ofPattern("d.\u00A0MMMM yyyy", Locale.GERMANY).format(date).escapeLatex()

    /**
     * Dasselbe Datum mit gewöhnlichem Leerzeichen, für die .xmpdata-Datei.
     *
     * pdfx liest die Felder dort als Text und nicht als Satz: Ein `~` käme als
     * Tilde in die Dokumenteigenschaften des PDF ("vom 14.~August 2026").
     * Nachgemessen mit pdfinfo; veraPDF bemängelt das nicht, es ist ja gültiger
     * Text – nur eben der falsche.
     */
    fun dateInMetadata(date: LocalDate): Latex =
        DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMANY).format(date).escapeLatex()

    /**
     * Leistungszeitraum in der kürzesten korrekten Form. Deckt er genau einen
     * Kalendermonat ab, genügt dessen Nennung – §31 Abs. 4 UStDV lässt den
     * Kalendermonat als Angabe des Leistungszeitpunkts ausdrücklich zu, und
     * "Juli 2026" liest sich besser als "01.07.2026–31.07.2026".
     */
    fun servicePeriod(start: LocalDate, end: LocalDate): Latex =
        if (start.dayOfMonth == 1 && end == end.withDayOfMonth(end.lengthOfMonth()) && start.withDayOfMonth(1) == end.withDayOfMonth(1)) {
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMANY).format(start).escapeLatex()
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
        val fillsWholeMonths = start.dayOfMonth == 1 && end == end.withDayOfMonth(end.lengthOfMonth())
        if (!fillsWholeMonths) return servicePeriod(start, end)

        val month = DateTimeFormatter.ofPattern("MMMM", Locale.GERMANY)
        val monthWithYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMANY)
        return when {
            YearMonth.from(start) == YearMonth.from(end) -> monthWithYear.format(start).escapeLatex()
            // Dasselbe Jahr wird einmal genannt, nicht zweimal.
            start.year == end.year -> "${month.format(start)} bis ${monthWithYear.format(end)}".escapeLatex()
            else -> "${monthWithYear.format(start)} bis ${monthWithYear.format(end)}".escapeLatex()
        }
    }

    /** Kurzes Datum für Zeiträume: `01.07.2026` */
    fun shortDate(date: LocalDate): Latex = shortDateText(date).escapeLatex()

    // Ohne Maskierung, für den Zusammenbau innerhalb dieser Datei.
    private fun shortDateText(date: LocalDate): String =
        DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY).format(date)
}
