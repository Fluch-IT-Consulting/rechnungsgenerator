package de.fluchitconsulting.rechnung.render

import de.fluchitconsulting.rechnung.values.Money
import de.fluchitconsulting.rechnung.values.Percent
import de.fluchitconsulting.rechnung.values.Price
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Deutsche Darstellung von Zahlen und Daten – die Ausgabeschicht, die die
 * Value Objects bewusst nicht kennen (dort ist toString() locale-unabhängige
 * Debug-Form, siehe Money.toString).
 *
 * Geliefert werden fertige [Latex]-Fragmente, keine Zeichenketten: Genau eine
 * dieser Funktionen erzeugt ein LaTeX-Sonderzeichen ([percent] mit dem `%`),
 * und welche das ist, soll keine Aufrufstelle wissen müssen. Die Maskierung
 * sitzt deshalb hier, nicht bei den zwanzig Aufrufern.
 *
 * Alle Formate werden mit explizitem Locale erzeugt, nie mit dem Standard-Locale
 * der JVM: Sonst hinge das Aussehen einer Rechnung davon ab, wie der Rechner
 * eingestellt ist, auf dem sie erzeugt wurde.
 */
object GermanFormat {
    private val SYMBOLS = DecimalFormatSymbols(Locale.GERMANY)

    // DecimalFormat ist nicht thread-safe, deshalb pro Aufruf eine neue Instanz
    // statt einer geteilten val. Das Rendern läuft ein paar Dutzend Mal pro
    // Rechnung – der Aufwand ist gegenüber dem LaTeX-Lauf nicht messbar.
    private fun format(pattern: String, value: BigDecimal): String = DecimalFormat(pattern, SYMBOLS).format(value)

    /**
     * Zahl mit so vielen Nachkommastellen, wie der Wert hat – mindestens
     * [minFraction], höchstens so viele, wie tatsächlich da sind.
     *
     * Feste Muster haben hier vorher stillschweigend gerundet: `#,##0.00##`
     * machte aus einem Einzelpreis von 0,00001 € ein `0,00`, und dann steht auf
     * der Rechnung "10.000 Stück zu 0,00 € = 0,10 €". Die Summen stimmten – der
     * Calculator rechnet mit dem exakten Wert –, aber nachrechnen konnte das
     * niemand. Genau das soll eine Rechnung erlauben.
     *
     * Der Preis dafür ist eine breitere Spalte in genau den Fällen, in denen
     * die Breite die Wahrheit ist.
     *
     * Gerundet wird hier also nicht mehr. Sollte es doch je dazu kommen, dann
     * HALF_UP wie überall sonst – die Vorgabe von DecimalFormat wäre HALF_EVEN
     * und damit die zweite Rundungsart im Programm.
     */
    private fun exact(value: BigDecimal, minFraction: Int): String {
        // stripTrailingZeros macht aus 19,0000000 die 19 und aus 1000 ein
        // 1E+3 mit negativem scale – deshalb der Boden bei minFraction.
        val stellen = maxOf(value.stripTrailingZeros().scale(), minFraction)
        return DecimalFormat("#,##0", SYMBOLS)
            .apply {
                roundingMode = RoundingMode.HALF_UP
                minimumFractionDigits = minFraction
                maximumFractionDigits = stellen
            }.format(value)
    }

    /** Betrag ohne Währung: `8.337,50` */
    fun amount(money: Money): Latex = exact(money.value, minFraction = 2).escapeLatex()

    /** Betrag mit Währung und geschütztem Leerzeichen: `8.337,50 €` */
    fun amountWithCurrency(money: Money): Latex = "${exact(money.value, minFraction = 2)} €".escapeLatex()

    /**
     * Einzelpreis: mindestens zwei, höchstens vier Nachkommastellen.
     * Price darf mehr Stellen tragen als Money (EN 16931 BT-146), und die
     * sollen auf der Rechnung auch sichtbar sein: `0,3350` statt `0,34`.
     */
    fun price(price: Price): Latex = exact(price.value, minFraction = 2).escapeLatex()

    fun priceWithCurrency(price: Price): Latex = "${exact(price.value, minFraction = 2)} €".escapeLatex()

    /** Menge ohne überflüssige Nullen: `86,5`, `1`, `1000` */
    fun quantity(quantity: BigDecimal): Latex = exact(quantity, minFraction = 0).escapeLatex()

    /**
     * Steuersatz in Prozentschreibweise: `19 %`, `7 %`, `2,5 %`.
     *
     * Die einzige Funktion hier, deren Ergebnis maskiert werden **muss**: Ein
     * nacktes `%` kommentiert in LaTeX den Rest der Zeile aus, samt
     * Spaltentrenner und Zeilenumbruch.
     */
    fun percent(percent: Percent): Latex = "${exact(percent.valueAsPercentage, minFraction = 0)} %".escapeLatex()

    /** Ausgeschriebenes Datum: `14. August 2026` */
    fun date(date: LocalDate): Latex =
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
