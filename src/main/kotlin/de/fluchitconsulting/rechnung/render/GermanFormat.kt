package de.fluchitconsulting.rechnung.render

import de.fluchitconsulting.rechnung.values.Money
import de.fluchitconsulting.rechnung.values.Percent
import de.fluchitconsulting.rechnung.values.Price
import java.math.BigDecimal
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

    /** Betrag ohne Währung: `8.337,50` */
    fun amount(money: Money): String = format("#,##0.00", money.value)

    /** Betrag mit Währung und geschütztem Leerzeichen: `8.337,50 €` */
    fun amountWithCurrency(money: Money): String = "${amount(money)} €"

    /**
     * Einzelpreis: mindestens zwei, höchstens vier Nachkommastellen.
     * Price darf mehr Stellen tragen als Money (EN 16931 BT-146), und die
     * sollen auf der Rechnung auch sichtbar sein: `0,3350` statt `0,34`.
     */
    fun price(price: Price): String = format("#,##0.00##", price.value)

    fun priceWithCurrency(price: Price): String = "${price(price)} €"

    /** Menge ohne überflüssige Nullen: `86,5`, `1`, `1000` */
    fun quantity(quantity: BigDecimal): String = format("#,##0.###", quantity)

    /** Steuersatz in Prozentschreibweise: `19 %`, `7 %`, `2,5 %` */
    fun percent(percent: Percent): String = "${format("#,##0.###", percent.valueAsPercentage)} %"

    /** Ausgeschriebenes Datum: `14. August 2026` */
    fun date(date: LocalDate): String = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMANY).format(date)

    /**
     * Leistungszeitraum in der kürzesten korrekten Form. Deckt er genau einen
     * Kalendermonat ab, genügt dessen Nennung – §31 Abs. 4 UStDV lässt den
     * Kalendermonat als Angabe des Leistungszeitpunkts ausdrücklich zu, und
     * "Juli 2026" liest sich besser als "01.07.2026–31.07.2026".
     */
    fun servicePeriod(start: LocalDate, end: LocalDate): String =
        if (start.dayOfMonth == 1 && end == end.withDayOfMonth(end.lengthOfMonth()) && start.withDayOfMonth(1) == end.withDayOfMonth(1)) {
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMANY).format(start)
        } else {
            "${shortDate(start)}\u2009–\u2009${shortDate(end)}"
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
    fun monthPeriod(start: LocalDate, end: LocalDate): String {
        val fuelltRandmonate = start.dayOfMonth == 1 && end == end.withDayOfMonth(end.lengthOfMonth())
        if (!fuelltRandmonate) return servicePeriod(start, end)

        val monat = DateTimeFormatter.ofPattern("MMMM", Locale.GERMANY)
        val monatMitJahr = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMANY)
        return when {
            YearMonth.from(start) == YearMonth.from(end) -> monatMitJahr.format(start)
            // Dasselbe Jahr wird einmal genannt, nicht zweimal.
            start.year == end.year -> "${monat.format(start)} bis ${monatMitJahr.format(end)}"
            else -> "${monatMitJahr.format(start)} bis ${monatMitJahr.format(end)}"
        }
    }

    /** Kurzes Datum für Zeiträume: `01.07.2026` */
    fun shortDate(date: LocalDate): String = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY).format(date)
}
