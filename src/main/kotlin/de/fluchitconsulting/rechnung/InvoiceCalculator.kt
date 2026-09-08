package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.model.CheckMethod
import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.model.LineItem
import de.fluchitconsulting.rechnung.model.Prepayment
import de.fluchitconsulting.rechnung.model.SellerConfig
import de.fluchitconsulting.rechnung.values.EuMemberStates
import de.fluchitconsulting.rechnung.values.InvoiceType
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

/**
 * Ein abgesetzter Abschlag mit seinen Summen.
 *
 * Netto und Steuer bleiben getrennt (ADR-0002): §14 Abs. 5 Satz 2 UStG setzt
 * das Entgelt *und* die darauf entfallende Steuer ab, und aus einem
 * Bruttobetrag ließe sich beides nicht zurückgewinnen.
 */
data class DeductedPrepayment(
    val prepayment: Prepayment,
    val net: Money,
    val vat: Money,
    val gross: Money,
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
    /** Die abgesetzten Abschläge, in der Reihenfolge der Rechnungsdatei. */
    val prepayments: List<DeductedPrepayment> = emptyList(),
) {
    // Abgeleitet statt gespeichert: Zwei Felder, die dasselbe sagen wie die
    // Liste, könnten von ihr abweichen.
    val prepaymentTotal: Money get() = prepayments.sumOf { it.gross }

    /** Was nach der Absetzung noch zu zahlen ist. Null ist zulässig, negativ nicht. */
    val remainingAmount: Money get() = grossTotal - prepaymentTotal
}

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

        val prepayments =
            invoice.prepayments.map { abschlag ->
                val net = abschlag.taxShares.sumOf { it.net }
                val vat = abschlag.taxShares.sumOf { it.vat }
                DeductedPrepayment(abschlag, net = net, vat = vat, gross = net + vat)
            }
        // Die übrigen sieben Regeln stehen in validatePrepayments, das validate()
        // aufruft: Sie kommen ohne die Summen aus und sollen zuschlagen, bevor
        // überhaupt gerechnet wird.
        validateDeduction(vatGroups, grossTotal, prepayments)

        return InvoiceTotals(lines, netTotal, vatGroups, grossTotal, dueDate, hints, prepayments)
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

        validateReceipt(invoice)
        validatePrepayments(invoice)
        validateVatIdCheck(invoice, config, brauchtKundenUstId.toSet())
    }

    /**
     * Die Angaben je Abschlag – alles, was sich ohne die Summen der Rechnung
     * prüfen lässt. Die Absetzung gegen die Rechnung selbst folgt in
     * [validateDeduction], sobald die Steueraufschlüsselung steht.
     */
    private fun validatePrepayments(invoice: Invoice) {
        // Regel 1: Sobald abgesetzt wird, *ist* das Dokument eine Schlussrechnung.
        if (invoice.prepayments.isNotEmpty() && invoice.type != InvoiceType.FINAL) {
            throw InvoiceValidationException(
                "Abschläge werden nur in einer Schlussrechnung abgesetzt (art: schlussrechnung); " +
                    "dieses Dokument ist eine ${invoice.type.documentName}.",
            )
        }
        // Regel 2: Umgekehrt fehlt dann entweder die Absetzung – oder es ist
        // gar keine Schlussrechnung.
        if (invoice.type == InvoiceType.FINAL && invoice.prepayments.isEmpty()) {
            throw InvoiceValidationException(
                "Eine Schlussrechnung setzt die vereinnahmten Abschläge ab, hier ist keiner angegeben. " +
                    "Bitte 'abschlaege' füllen – oder, wenn es keine gab, 'art: rechnung' schreiben.",
            )
        }

        // Regel 7: Derselbe Abschlag zweimal abgesetzt ist ein Kopierfehler und
        // fiele sonst nur über den Restbetrag auf.
        // Normalisiert verglichen, anders als sonst im Programm: Hier geht es
        // nicht darum, wie die Nummer geschrieben wird, sondern ob zweimal
        // dasselbe Dokument gemeint ist. "R26-009 " und "r26-009" sind es.
        invoice.prepayments
            .groupBy { it.invoiceNumber.trim().uppercase() }
            .filterValues { it.size > 1 }
            .values
            .firstOrNull()
            ?.let { duplicates ->
                throw InvoiceValidationException(
                    "Der Abschlag ${duplicates.first().invoiceNumber} steht zweimal in dieser Schlussrechnung.",
                )
            }

        invoice.prepayments.forEach { abschlag ->
            val label = "Abschlag ${abschlag.invoiceNumber}"

            // Regel 3: §14 Abs. 5 Satz 2 UStG setzt ab, was vereinnahmt wurde,
            // nicht was berechnet wurde.
            val vereinnahmtAm =
                abschlag.receivedOn
                    ?: throw InvoiceValidationException(
                        "$label: ohne 'vereinnahmt-am' keine Absetzung. Abgesetzt wird, was vereinnahmt " +
                            "wurde – ein berechneter, aber unbezahlter Abschlag ist eine offene Forderung " +
                            "und wird gemahnt oder storniert.",
                    )

            // Regel 4: Was nach dem Rechnungsdatum liegt, ist für diese
            // Rechnung nicht vereinnahmt.
            if (vereinnahmtAm.isAfter(invoice.issueDate)) {
                throw InvoiceValidationException(
                    "$label: vereinnahmt am $vereinnahmtAm, also nach dem Rechnungsdatum " +
                        "${invoice.issueDate}. Für diese Schlussrechnung ist er nicht vereinnahmt.",
                )
            }

            // Regel 9, aus demselben Grund wie Regel 5: Ohne Anteile liefe die
            // Prüfsumme über eine leere Schleife, und der Abschlag setzte still
            // 0,00 € ab.
            if (abschlag.taxShares.isEmpty()) {
                throw InvoiceValidationException(
                    "$label: keine 'steueranteile' angegeben. Ein Abschlag ohne Netto und Steuer lässt " +
                        "sich nicht absetzen – §14 Abs. 5 Satz 2 UStG setzt das Entgelt und die darauf " +
                        "entfallende Steuer ab.",
                )
            }

            // Regel 5: Die Prüfsumme gegen den Zahlendreher (ADR-0001). Die
            // Steuer steht in der Datei, weil sie aus der alten Rechnung
            // abgeschrieben wird – nachgerechnet wird sie trotzdem.
            abschlag.taxShares.forEach { anteil ->
                val expected = anteil.net * anteil.vatRate
                if (anteil.vat != expected) {
                    throw InvoiceValidationException(
                        "$label: ${anteil.net} netto zu ${anteil.vatRate} ergeben $expected Umsatzsteuer, " +
                            "angegeben sind ${anteil.vat}.",
                    )
                }
            }
        }
    }

    /**
     * Die Absetzung gegen die Rechnung selbst – die beiden Regeln, die die
     * Summen brauchen.
     */
    private fun validateDeduction(
        vatGroups: List<VatGroup>,
        grossTotal: Money,
        prepayments: List<DeductedPrepayment>,
    ) {
        // Regel 6: Zwei Positionen mit 0 % können steuerlich Verschiedenes
        // bedeuten, deshalb muss die Kombination aus Kategorie und Satz passen –
        // nicht nur der Satz.
        val present = vatGroups.map { it.category to it.rate }.toSet()
        prepayments.forEach { abgesetzt ->
            abgesetzt.prepayment.taxShares.forEach { anteil ->
                if ((anteil.taxCategory to anteil.vatRate) !in present) {
                    throw InvoiceValidationException(
                        "Abschlag ${abgesetzt.prepayment.invoiceNumber}: ${anteil.taxCategory.code} zu " +
                            "${anteil.vatRate} kommt in dieser Schlussrechnung nicht vor. Entweder war der " +
                            "Abschlag damals falsch oder die Schlussrechnung ist es jetzt.",
                    )
                }
            }
        }

        // Regel 8: Eine Überzahlung ist kein Restbetrag, sondern ein anderes
        // Dokument – Gutschrift oder Storno, beides gibt es hier noch nicht.
        val abgesetzt = prepayments.sumOf { it.gross }
        val restbetrag = grossTotal - abgesetzt
        if (restbetrag.isNegative()) {
            throw InvoiceValidationException(
                "Der Restbetrag wäre $restbetrag: Es sind mehr Abschläge abgesetzt ($abgesetzt) als die " +
                    "Schlussrechnung ausmacht ($grossTotal). Eine Überzahlung gehört in eine Gutschrift, " +
                    "nicht in eine Rechnung.",
            )
        }
    }

    /**
     * Der Zeitpunkt der Vereinnahmung (BT-7) gehört auf die Abschlagsrechnung –
     * und nur dorthin. Auf einer Rechnung über eine erbrachte Leistung ist die
     * Umsatzsteuer mit der Leistung entstanden, nicht mit dem Geldeingang; die
     * Angabe wäre dort keine Zusatzinformation, sondern eine falsche.
     */
    private fun validateReceipt(invoice: Invoice) {
        val vereinnahmt = invoice.receivedOn ?: return

        if (invoice.type != InvoiceType.PREPAYMENT) {
            throw InvoiceValidationException(
                "'vereinnahmt-am' gehört nur auf eine Abschlagsrechnung (art: abschlagsrechnung); " +
                    "dieses Dokument ist eine ${invoice.type.documentName}.",
            )
        }
        // Gleiche Prüfung wie beim Datum der Bestätigungsabfrage: Was nach dem
        // Rechnungsdatum liegt, belegt für diese Rechnung nichts. Steht der
        // Zeitpunkt noch nicht fest, bleibt die Angabe weg.
        if (vereinnahmt.isAfter(invoice.issueDate)) {
            throw InvoiceValidationException(
                "Die Vereinnahmung am $vereinnahmt liegt nach dem Rechnungsdatum ${invoice.issueDate}. " +
                    "Steht der Zeitpunkt noch nicht fest, bitte 'vereinnahmt-am' weglassen.",
            )
        }
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
