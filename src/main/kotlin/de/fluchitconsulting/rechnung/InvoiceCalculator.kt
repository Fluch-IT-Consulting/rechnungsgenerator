package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.model.CheckMethod
import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.model.LineItem
import de.fluchitconsulting.rechnung.model.SellerConfig
import de.fluchitconsulting.rechnung.values.EuMemberStates
import de.fluchitconsulting.rechnung.values.Money
import de.fluchitconsulting.rechnung.values.Percent
import de.fluchitconsulting.rechnung.values.TaxCategory
import de.fluchitconsulting.rechnung.values.sumOf
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Alle abgeleiteten Werte einer Rechnung. Wird aus Invoice + SellerConfig
// berechnet und ist danach unveränderlich – das Template bekommt nur noch dies.

/** Wird geworfen, wenn die Rechnung in sich nicht stimmig ist. */
class InvoiceValidationException(message: String) : Exception(message)

data class CalculatedLine(
    val item: LineItem,
    // Netto der Position: Menge × Einzelpreis. Das Runden auf Cent macht Money selbst.
    val net: Money,
)

/**
 * Eine Zeile der Steueraufschlüsselung – EN 16931 BG-23.
 *
 * Gruppiert wird nach Kategorie *und* Satz, nicht nur nach Satz: Zwei
 * Positionen mit 0 % können steuerlich völlig Verschiedenes bedeuten
 * (steuerbefreit, Reverse Charge, nicht steuerbar) und gehören dann in
 * getrennte Zeilen.
 */
data class VatGroup(
    val category: TaxCategory,
    val rate: Percent,
    // Bemessungsgrundlage: Summe aller Positionsnetto-Beträge dieser Gruppe.
    val base: Money,
    val amount: Money,
    /** BT-120, nur bei Kategorien ohne Steuer. */
    val exemptionReason: String? = null,
)

data class InvoiceTotals(
    val lines: List<CalculatedLine>,
    val netTotal: Money,
    val vatGroups: List<VatGroup>,
    val grossTotal: Money,
    val dueDate: LocalDate,
    /**
     * Hinweise für den Rechnungssteller – nicht für den Kunden, sie stehen
     * nicht auf dem PDF. Sie betreffen Nachweise, die in die eigene Akte
     * gehören, und Dinge, die zwar zulässig, aber erklärungsbedürftig sind.
     */
    val hints: List<String> = emptyList(),
)

object InvoiceCalculator {
    fun calculate(invoice: Invoice, config: SellerConfig): InvoiceTotals {
        validate(invoice, config)
        val hints = collectHints(invoice, config)

        // Erst pro Position runden, dann summieren – so stimmt die Summe mit dem
        // überein, was der Leser auf der Rechnung nachrechnen kann.
        //
        // roundToAmount() ist die einzige Rundung in dieser Funktion, und der Compiler
        // erzwingt sie: unitPrice ist ein Price (exakt), net ein Money (zwei Stellen).
        val lines = invoice.items.map { CalculatedLine(it, (it.unitPrice * it.quantity).roundToAmount()) }

        val netTotal = lines.sumOf { it.net }

        // USt. je Gruppe auf die summierte Bemessungsgrundlage berechnen
        // (nicht je Position!), so verlangt es EN 16931 (BG-23).
        val vatGroups =
            lines
                .groupBy { it.item.taxCategory to it.item.vatRate }
                .map { (schluessel, group) ->
                    val (category, rate) = schluessel
                    val base = group.sumOf { it.net }
                    VatGroup(
                        category = category,
                        rate = rate,
                        base = base,
                        amount = base * rate,
                        exemptionReason = reasonFor(category, group),
                    )
                }.sortedWith(compareBy({ it.category.ordinal }, { -it.rate.valueAsPercentage.toDouble() }))

        val grossTotal = netTotal + vatGroups.sumOf { it.amount }

        // Elvis-Operator `?:` – nimm den Override, sonst den Default aus den Stammdaten.
        val termDays = invoice.paymentTermDaysOverride ?: config.paymentTermDays
        val dueDate = invoice.issueDate.plusDays(termDays.toLong())

        return InvoiceTotals(lines, netTotal, vatGroups, grossTotal, dueDate, hints)
    }

    /**
     * Die Regeln, die EN 16931 an die Steuerkategorie knüpft. Sie hier zu prüfen
     * und nicht erst beim ZUGFeRD-Export hat einen praktischen Grund: Eine
     * Rechnung mit "AE" und 19 % ginge sonst als PDF hinaus und fiele erst dem
     * Empfänger auf – dann ist sie schon verschickt.
     */
    private fun validate(invoice: Invoice, config: SellerConfig) {
        invoice.items.forEachIndexed { index, item ->
            val position = "Position ${index + 1} (${item.name})"
            val kategorie = item.taxCategory

            if (kategorie.requiresZeroRate && item.vatRate.valueAsMultiplier.signum() != 0) {
                throw InvoiceValidationException(
                    "$position: Kategorie ${kategorie.code} (${kategorie.label}) verlangt einen Steuersatz von 0 %, " +
                        "angegeben ist ${item.vatRate}.",
                )
            }
            if (kategorie == TaxCategory.STANDARD && item.vatRate.valueAsMultiplier.signum() == 0) {
                throw InvoiceValidationException(
                    "$position: Kategorie S (Regelsatz) mit 0 % ist widersprüchlich. Gemeint ist vermutlich " +
                        "Z (Nullsatz), E (steuerbefreit) oder AE (Steuerschuldnerschaft des Leistungsempfängers).",
                )
            }
            if (!kategorie.requiresReason && item.taxExemptionReason != null) {
                throw InvoiceValidationException(
                    "$position: Kategorie ${kategorie.code} trägt keinen Steuerbefreiungsgrund.",
                )
            }
            if (kategorie.requiresReason && item.taxExemptionReason == null && kategorie.defaultReason == null) {
                throw InvoiceValidationException(
                    "$position: Kategorie ${kategorie.code} (${kategorie.label}) verlangt einen " +
                        "Steuerbefreiungsgrund – bitte 'steuerbefreiungsgrund' angeben.",
                )
            }
        }

        // BT-48: Ohne USt-IdNr. des Empfängers lässt sich weder die
        // Steuerschuldnerschaft noch eine innergemeinschaftliche Lieferung belegen.
        val brauchtKundenUstId =
            invoice.items
                .map { it.taxCategory }
                .filter { it == TaxCategory.REVERSE_CHARGE || it == TaxCategory.INTRA_COMMUNITY }
        if (brauchtKundenUstId.isNotEmpty() && invoice.customer.vatId == null) {
            throw InvoiceValidationException(
                "Kategorie ${brauchtKundenUstId.first().code} verlangt die USt-IdNr. des Kunden – " +
                    "bitte 'umsatzsteuer-id' unter 'kunde' angeben.",
            )
        }

        validateVatIdCheck(invoice, config, brauchtKundenUstId.toSet())
    }

    /**
     * Verlangt den dokumentierten Nachweis der Bestätigungsabfrage, bevor ohne
     * Umsatzsteuer abgerechnet wird.
     *
     * Rechtsstand August 2026. Die Regeln sind bewusst verschieden streng, weil
     * die Rechtslage es auch ist:
     *
     *  - Innergemeinschaftliche Lieferung (K): §6a UStG verlangt die geprüfte
     *    USt-IdNr., unabhängig davon, wo der Kunde sitzt. Kein Spielraum.
     *  - Reverse Charge (AE) grenzüberschreitend in die EU: Seit Januar 2024
     *    ist die gültige USt-IdNr. keine zwingende Voraussetzung des §13b mehr
     *    – entscheidend ist die Unternehmereigenschaft. Ohne dokumentierte
     *    Abfrage entfällt aber der Vertrauensschutz, deshalb hier trotzdem
     *    Pflicht.
     *  - Reverse Charge im Inland (§13b Abs. 2, etwa Bauleistungen): kein
     *    Nachweis. Das BZSt bestätigt *ausländische* USt-IdNrn.; für die Nummer
     *    eines deutschen Kunden gibt es diese Abfrage gar nicht.
     *  - Reverse Charge an einen Drittlandskunden: VIES und die Abfrage beim
     *    BZSt decken nur EU-Mitgliedstaaten ab. Für die Schweiz oder das
     *    Vereinigte Königreich gibt es dort nichts abzufragen; einen Nachweis
     *    zu verlangen, den es nicht geben kann, wäre keine Strenge, sondern ein
     *    Fehler. Stattdessen ein Hinweis, siehe [collectHints].
     *
     * Das ist Steuerrecht und keine Rechtsberatung – wer die Regeln ändert,
     * sollte sie mit dem Steuerberater abgleichen.
     */
    private fun validateVatIdCheck(invoice: Invoice, config: SellerConfig, kategorien: Set<TaxCategory>) {
        if (kategorien.isEmpty()) return
        val kunde = invoice.customer
        val ausland = !kunde.address.countryCode.equals(config.address.countryCode, ignoreCase = true)
        val inEu = EuMemberStates.contains(kunde.address.countryCode)
        val pflicht =
            TaxCategory.INTRA_COMMUNITY in kategorien ||
                (TaxCategory.REVERSE_CHARGE in kategorien && inEu && ausland)
        if (!pflicht) return

        val pruefung =
            kunde.vatIdCheck
                ?: throw InvoiceValidationException(
                    "Ohne dokumentierte Bestätigungsabfrage keine Rechnung ohne Umsatzsteuer. " +
                        "Bitte die USt-IdNr. ${kunde.vatId} beim BZSt oder über VIES prüfen und das " +
                        "Ergebnis unter 'kunde: ust-id-geprueft' eintragen (am, verfahren, nachweis).",
                )

        // Eine Abfrage nach dem Rechnungsdatum belegt für diese Rechnung nichts.
        if (pruefung.date.isAfter(invoice.issueDate)) {
            throw InvoiceValidationException(
                "Die Bestätigungsabfrage vom ${pruefung.date} liegt nach dem Rechnungsdatum " +
                    "${invoice.issueDate} – für diese Rechnung belegt sie nichts.",
            )
        }
    }

    /** Hinweise, die den Lauf nicht abbrechen, aber in die eigene Akte gehören. */
    private fun collectHints(invoice: Invoice, config: SellerConfig): List<String> =
        buildList {
            val kunde = invoice.customer
            val kategorien = invoice.items.map { it.taxCategory }.toSet()
            val ausland = !kunde.address.countryCode.equals(config.address.countryCode, ignoreCase = true)

            // Die Altersprüfung der EU-Liste hängt bewusst an `ausland` und läuft
            // nicht immer: Bei einer Inlandsrechnung wird die Liste gar nicht
            // befragt, ein Hinweis darauf wäre also Lärm. Und Hinweise, die bei
            // jeder Rechnung erscheinen, liest nach der dritten niemand mehr –
            // dann geht der eine unter, auf den es ankommt.
            if (ausland) {
                val listenAlter = ChronoUnit.MONTHS.between(EuMemberStates.AS_OF, invoice.issueDate)
                if (listenAlter >= 12) {
                    add(
                        "Die hinterlegte Liste der EU-Mitgliedstaaten ist $listenAlter Monate alt " +
                            "(zuletzt geprüft am ${EuMemberStates.AS_OF}). Sie entscheidet mit, ob für " +
                            "${kunde.address.countryCode} ein Nachweis verlangt wird – bitte gegen die " +
                            "amtliche Aufstellung abgleichen und EuMemberStates.AS_OF nachziehen.",
                    )
                }
            }

            if (TaxCategory.REVERSE_CHARGE in kategorien && ausland && !EuMemberStates.contains(kunde.address.countryCode)) {
                add(
                    "${kunde.address.countryCode} ist kein EU-Mitgliedstaat – eine VIES-Abfrage gibt es dafür " +
                        "nicht. Für die Akte braucht es einen anderen Nachweis der Unternehmereigenschaft, " +
                        "etwa eine Unternehmerbescheinigung.",
                )
            }
            kunde.vatIdCheck?.let { pruefung ->
                if (pruefung.method == CheckMethod.SIMPLE) {
                    add(
                        "Die Abfrage vom ${pruefung.date} war einfach, nicht qualifiziert. Nur die " +
                            "qualifizierte Abfrage beim BZSt gleicht Name und Anschrift ab und verschafft " +
                            "Vertrauensschutz.",
                    )
                }
                if (pruefung.reference == null) {
                    add(
                        "Zur Abfrage vom ${pruefung.date} ist kein Nachweis vermerkt – " +
                            "ohne Protokoll nützt sie im Zweifel wenig.",
                    )
                }
                val alter = ChronoUnit.MONTHS.between(pruefung.date, invoice.issueDate)
                if (alter >= 12) {
                    add(
                        "Die Bestätigungsabfrage ist $alter Monate alt. Bei laufender Geschäftsbeziehung " +
                            "wird üblicherweise regelmäßig erneut geprüft.",
                    )
                }
            }
        }

    /**
     * Der Befreiungsgrund einer Gruppe. Alle Positionen einer Gruppe müssen sich
     * einig sein: BG-23 trägt genau einen Grund je Kategorie und Satz, zwei
     * verschiedene Begründungen ließen sich dort nicht abbilden.
     */
    private fun reasonFor(category: TaxCategory, group: List<CalculatedLine>): String? {
        if (!category.requiresReason) return null
        val gruende = group.mapNotNull { it.item.taxExemptionReason }.distinct()
        if (gruende.size > 1) {
            throw InvoiceValidationException(
                "Kategorie ${category.code} hat zwei verschiedene Befreiungsgründe: " +
                    gruende.joinToString(" / ") + ". Die Steueraufschlüsselung trägt nur einen je Kategorie.",
            )
        }
        return gruende.firstOrNull() ?: category.defaultReason
    }
}
