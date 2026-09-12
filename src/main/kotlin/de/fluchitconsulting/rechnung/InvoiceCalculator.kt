package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.model.CheckMethod
import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.model.LineItem
import de.fluchitconsulting.rechnung.model.Prepayment
import de.fluchitconsulting.rechnung.model.SellerConfig
import de.fluchitconsulting.rechnung.values.CountryCode
import de.fluchitconsulting.rechnung.values.EuMemberStates
import de.fluchitconsulting.rechnung.values.InvoiceType
import de.fluchitconsulting.rechnung.values.Money
import de.fluchitconsulting.rechnung.values.Percent
import de.fluchitconsulting.rechnung.values.TaxCategory
import de.fluchitconsulting.rechnung.values.VatIdConfirmation
import de.fluchitconsulting.rechnung.values.sumOf
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Eine Rechnung samt allem, was sich aus ihr und den Stammdaten ausrechnet.
// Unveränderlich; der Renderer bekommt nur noch dies.

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

/**
 * Eine Rechnung, wie sie sich mit ihren Stammdaten ausrechnet: die Eingaben und
 * alles Abgeleitete in einem Wert.
 *
 * Vorher reisten Rechnung und Summen als Paar, das nichts zusammenhielt. Jeder
 * Verbraucher nahm beide entgegen und verließ sich darauf, dass sie zueinander
 * gehören; zwei Eingaben standen sicherheitshalber noch einmal in den Summen.
 * Jetzt ist ein Paar aus einer Rechnung und den Summen einer anderen nicht mehr
 * baubar – nicht bloß ungebaut, derselbe Zug wie beim Zahlungsziel.
 */
data class CalculatedInvoice(
    val invoice: Invoice,
    val config: SellerConfig,
    val lines: List<CalculatedLine>,
    val netTotal: Money,
    val vatGroups: List<VatGroup>,
    val grossTotal: Money,
    /** Die abgesetzten Abschläge, in der Reihenfolge der Rechnungsdatei. */
    val prepayments: List<DeductedPrepayment> = emptyList(),
    /**
     * Was bei der Prüfung auffiel, in der Reihenfolge der Rechnung: erst der
     * Kopf, dann die Positionen, dann die Abschläge.
     */
    val findings: List<Finding> = emptyList(),
) {
    /**
     * Die vereinbarte Zahlungsfrist in Tagen: die der Rechnung, sonst die der
     * Stammdaten. Abgeleitet und nicht kopiert – als eigenes Feld wäre es
     * dieselbe Angabe ein zweites Mal, und die zweite kann abweichen.
     */
    val paymentTermDays: Int get() = invoice.paymentTermDaysOverride ?: config.paymentTermDays

    // Abgeleitet statt gespeichert: Zwei Felder, die dasselbe sagen wie die
    // Liste, könnten von ihr abweichen.
    val prepaymentTotal: Money get() = prepayments.sumOf { it.gross }

    /** Was nach der Absetzung noch zu zahlen ist. Null ist zulässig, negativ nicht. */
    val remainingAmount: Money get() = grossTotal - prepaymentTotal

    /**
     * Bis wann zu zahlen ist – oder `null`, wenn nichts mehr zu zahlen ist.
     *
     * Das ist der Fall bei einer Schlussrechnung, deren Abschläge den Betrag
     * decken: Sie sagt unten "es verbleibt kein Restbetrag", und ein
     * Zahlungsziel daneben wäre die zweite, andere Aussage desselben Blattes.
     * Die Entscheidung fällt hier und nicht im Renderer – der setzt, was
     * dasteht, und entscheidet nicht, was dastehen soll.
     *
     * Abgeleitet und nicht gesetzt, aus demselben Grund wie [remainingAmount]:
     * Vorher entstand das Ergebnis erst ohne Zahlungsziel und wurde danach
     * kopiert, und wer eines in der Hand hielt, sah ihm nicht an, ob die
     * Entscheidung schon gefallen war. Jetzt ist ein Ergebnis mit Zahlungsziel
     * *und* Restbetrag null nicht mehr baubar – nicht bloß ungebaut.
     */
    val dueDate: LocalDate? get() =
        if (remainingAmount.isZero()) null else invoice.issueDate.plusDays(paymentTermDays.toLong())
}

object InvoiceCalculator {
    fun calculate(invoice: Invoice, config: SellerConfig): CalculatedInvoice {
        // Gesammelt statt geworfen (#49): Wer eine Regel prüft, soll nicht erst
        // eine Rechnung bauen müssen, die alle anderen besteht.
        val findings = validate(invoice, config).toMutableList()
        findings += collectHints(invoice, config)

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
                        exemptionReason = reasonFor(category, group, findings),
                    )
                }.sortedWith(compareBy({ it.category.ordinal }, { -it.rate.valueAsPercentage.toDouble() }))

        val grossTotal = netTotal + vatGroups.sumOf { it.amount }

        // Eine Rechnung über einen negativen Betrag wäre eine Gutschrift (#47).
        // Bis dahin schlug hier erst Regel 8 der Absetzung zu, mit einer
        // Meldung über Abschläge, die eine gewöhnliche Rechnung gar nicht hat.
        if (grossTotal.isNegative()) {
            findings +=
                Finding(
                    Rule.GESAMTBETRAG_NEGATIV,
                    Subject.WholeInvoice,
                    "Der Gesamtbetrag wäre $grossTotal. Eine Rechnung über einen negativen Betrag ist eine " +
                        "Gutschrift, und die gibt es hier noch nicht.",
                )
        }

        val prepayments =
            invoice.prepayments.map { abschlag ->
                val net = abschlag.taxShares.sumOf { it.net }
                val vat = abschlag.taxShares.sumOf { it.vat }
                DeductedPrepayment(abschlag, net = net, vat = vat, gross = net + vat)
            }
        // Elvis-Operator `?:` – nimm den Override, sonst den Default aus den Stammdaten.
        val termDays = invoice.paymentTermDaysOverride ?: config.paymentTermDays
        // Negativ setzte es die Fälligkeit vor das Rechnungsdatum (#47). Die
        // Meldung nennt, woher der Wert kommt; 0 heißt "sofort" und bleibt.
        if (termDays < 0) {
            val quelle = if (invoice.paymentTermDaysOverride != null) "in der Rechnung" else "in den Stammdaten"
            findings +=
                Finding(
                    Rule.ZAHLUNGSZIEL_NEGATIV,
                    Subject.WholeInvoice,
                    "Das Zahlungsziel $quelle ist $termDays Tage, die Fälligkeit läge also vor dem " +
                        "Rechnungsdatum. Bitte 'zahlungsziel-tage' prüfen; 0 heißt sofort.",
                )
        }
        // Die beiden Regeln der Absetzung brauchen die abgeleiteten Beträge, und
        // die kennt nur der fertige Wert. Deshalb entsteht er hier einmal ohne
        // ihre Befunde und gleich darauf mit; nach außen geht nur der zweite.
        // Die übrigen acht Regeln stehen in validatePrepayments und kommen ohne
        // die Summen aus.
        val ohneAbsetzung =
            CalculatedInvoice(invoice, config, lines, netTotal, vatGroups, grossTotal, prepayments, findings)
        return ohneAbsetzung.copy(findings = findings + validateDeduction(ohneAbsetzung))
    }

    /**
     * Die Form der Rechnungsnummer (ADR-0005).
     *
     * Warum hier und nicht beim Einlesen: Ob eine Nummer gültig ist, hängt an
     * zwei Dateien – der Rechnung und den Stammdaten. Ein Serialisierer sieht
     * immer nur eine.
     *
     * Warum nicht auch die Nummern der abgesetzten Abschläge: Die stammen aus
     * früheren Rechnungen, womöglich aus der Zeit vor einem Wechsel des
     * Musters. Gegen das heutige Muster geprüft, hielte eine korrekte alte
     * Nummer die Schlussrechnung auf.
     *
     * Die drei Regeln stehen in einer eigenen Funktion, weil eine die nächste
     * erübrigt: Eine fehlende Nummer passt zu keinem Muster, und das zu melden
     * hieße, denselben Fehler zweimal zu nennen. `return` statt eines
     * Mechanismus zur Unterdrückung – dieselbe Wahl wie bei den Abschlägen.
     */
    private fun validateInvoiceNumber(invoice: Invoice, config: SellerConfig): List<Finding> {
        val number = invoice.number

        // Leer und Leerraum gelten unabhängig vom Muster, also auch ohne
        // Eintrag in den Stammdaten: Aus der Nummer entsteht der Dateiname des
        // PDF, und ein gequotetes ' R26-009' ergäbe still eine andere Datei.
        if (number.isBlank()) {
            return listOf(
                Finding(
                    Rule.RECHNUNGSNUMMER_FEHLT,
                    Subject.WholeInvoice,
                    "Die Rechnungsnummer fehlt – 'nummer' ist leer. Aus ihr entsteht auch der Dateiname des PDF.",
                ),
            )
        }
        if (number != number.trim()) {
            return listOf(
                Finding(
                    Rule.RECHNUNGSNUMMER_MIT_LEERRAUM,
                    Subject.WholeInvoice,
                    "Die Rechnungsnummer '$number' beginnt oder endet mit Leerraum. Nur ein gequoteter " +
                        "YAML-Wert kann ihn tragen; gemeint war vermutlich '${number.trim()}'.",
                ),
            )
        }

        // Ohne Muster in den Stammdaten wird die Form nicht geprüft: Welche
        // Nummern gültig sind, weiß nur der Rechnungssteller.
        val muster = config.invoiceNumberPattern ?: return emptyList()
        if (!muster.matches(number)) {
            return listOf(
                Finding(
                    Rule.RECHNUNGSNUMMER_PASST_NICHT,
                    Subject.WholeInvoice,
                    "Die Rechnungsnummer '$number' passt nicht zum Muster '$muster' aus den Stammdaten " +
                        "('rechnungsnummer-muster').",
                ),
            )
        }
        return emptyList()
    }

    /**
     * Die Regeln, die EN 16931 an die Steuerkategorie knüpft. Sie hier zu prüfen
     * und nicht erst beim ZUGFeRD-Export hat einen praktischen Grund: Eine
     * Rechnung mit "AE" und 19 % ginge sonst als PDF hinaus und fiele erst dem
     * Empfänger auf – dann ist sie schon verschickt.
     */
    private fun validate(invoice: Invoice, config: SellerConfig): List<Finding> =
        buildList {
            addAll(validateInvoiceNumber(invoice, config))

            // EN 16931 BR-16. Ohne Position entstünde eine Rechnung über 0,00 €.
            if (invoice.items.isEmpty()) {
                add(
                    Finding(
                        Rule.KEINE_POSITION,
                        Subject.WholeInvoice,
                        "Eine Rechnung braucht mindestens eine Position – 'positionen' ist leer.",
                    ),
                )
            }

            invoice.items.forEachIndexed { index, item ->
                val position = Subject.Item(index, item.name)
                val kategorie = item.taxCategory

                // EN 16931 BR-27: Der Einzelpreis ist nie negativ.
                if (item.unitPrice.value.signum() < 0) {
                    add(
                        Finding(
                            Rule.EINZELPREIS_NEGATIV,
                            position,
                            "Der Einzelpreis ${item.unitPrice.value.toPlainString()} ist negativ. " +
                                "EN 16931 lässt das nicht zu (BR-27).",
                        ),
                    )
                }

                if (kategorie.requiresZeroRate && item.vatRate.valueAsMultiplier.signum() != 0) {
                    add(
                        Finding(
                            Rule.SATZ_MUSS_NULL_SEIN,
                            position,
                            "Kategorie ${kategorie.code} (${kategorie.label}) verlangt einen Steuersatz von 0 %, " +
                                "angegeben ist ${item.vatRate}.",
                        ),
                    )
                }
                if (kategorie == TaxCategory.STANDARD && item.vatRate.valueAsMultiplier.signum() == 0) {
                    add(
                        Finding(
                            Rule.REGELSATZ_MIT_NULL,
                            position,
                            "Kategorie S (Regelsatz) mit 0 % ist widersprüchlich. Gemeint ist vermutlich " +
                                "Z (Nullsatz), E (steuerbefreit) oder AE (Steuerschuldnerschaft des Leistungsempfängers).",
                        ),
                    )
                }
                if (!kategorie.requiresReason && item.taxExemptionReason != null) {
                    add(
                        Finding(
                            Rule.BEFREIUNGSGRUND_UNZULAESSIG,
                            position,
                            "Kategorie ${kategorie.code} trägt keinen Steuerbefreiungsgrund.",
                        ),
                    )
                }
                if (kategorie.requiresReason && item.taxExemptionReason == null && kategorie.defaultReason == null) {
                    add(
                        Finding(
                            Rule.BEFREIUNGSGRUND_FEHLT,
                            position,
                            "Kategorie ${kategorie.code} (${kategorie.label}) verlangt einen " +
                                "Steuerbefreiungsgrund – bitte 'steuerbefreiungsgrund' angeben.",
                        ),
                    )
                }
            }

            // BT-48: Ohne USt-IdNr. des Empfängers lässt sich weder die
            // Steuerschuldnerschaft noch eine innergemeinschaftliche Lieferung belegen.
            val brauchtKundenUstId =
                invoice.items
                    .map { it.taxCategory }
                    .filter { it.requiresCustomerVatId }
            if (brauchtKundenUstId.isNotEmpty() && invoice.customer.vatId == null) {
                add(
                    Finding(
                        Rule.KUNDEN_UST_ID_FEHLT,
                        Subject.WholeInvoice,
                        "Kategorie ${brauchtKundenUstId.first().code} verlangt die USt-IdNr. des Kunden – " +
                            "bitte 'umsatzsteuer-id' unter 'kunde' angeben.",
                    ),
                )
            }

            addAll(validateServicePeriods(invoice))
            addAll(validateReceipt(invoice))
            addAll(validatePrepayments(invoice))
            addAll(validateVatIdCheck(invoice, config, brauchtKundenUstId.toSet()))
        }

    /**
     * Die Zeiträume der Rechnung und ihrer Positionen (#47).
     *
     * Kein Zeitraum endet vor seinem Beginn – EN 16931 BR-29 für die Rechnung,
     * BR-30 für die Position. Und eine Rechnung über eine erbrachte Leistung
     * entsteht nicht, bevor die Leistung zu Ende ist: So definiert das Glossar
     * den Leistungszeitraum. Ausgenommen ist nur ein Zeitraum, der eine
     * Prognose ist – der der Abschlagsrechnung.
     */
    private fun validateServicePeriods(invoice: Invoice): List<Finding> =
        buildList {
            // Je Zeitraum, wem er gehört und wie er in der Meldung heißt: Der
            // Zeitraum der Rechnung trägt seinen Namen selbst, der einer
            // Position bekommt ihn vom Gegenstand davor.
            val zeitraeume =
                listOf(Triple(Subject.WholeInvoice, "Der Leistungszeitraum", invoice.servicePeriod)) +
                    invoice.items.mapIndexedNotNull { index, item ->
                        item.servicePeriod?.let { Triple(Subject.Item(index, item.name), "Der Zeitraum", it) }
                    }

            zeitraeume.forEach { (subject, label, zeitraum) ->
                if (zeitraum.start.isAfter(zeitraum.end)) {
                    add(
                        Finding(
                            Rule.ZEITRAUM_VERDREHT,
                            subject,
                            "$label beginnt am ${zeitraum.start}, endet aber schon am ${zeitraum.end}.",
                        ),
                    )
                }
                if (!invoice.type.servicePeriodIsForecast && zeitraum.end.isAfter(invoice.issueDate)) {
                    add(
                        Finding(
                            Rule.ZEITRAUM_NACH_RECHNUNGSDATUM,
                            subject,
                            "$label endet am ${zeitraum.end}, nach dem Rechnungsdatum ${invoice.issueDate}. Eine " +
                                "${invoice.type.documentName} rechnet eine erbrachte Leistung ab – steht die Leistung " +
                                "noch aus, ist es eine Abschlagsrechnung.",
                        ),
                    )
                }
            }
        }

    /**
     * Die Angaben je Abschlag – alles, was sich ohne die Summen der Rechnung
     * prüfen lässt. Die Absetzung gegen die Rechnung selbst folgt in
     * [validateDeduction], sobald die Steueraufschlüsselung steht.
     */
    private fun validatePrepayments(invoice: Invoice): List<Finding> =
        buildList {
            // Regel: Sobald abgesetzt wird, *ist* das Dokument eine Schlussrechnung.
            if (invoice.prepayments.isNotEmpty() && invoice.type != InvoiceType.FINAL) {
                add(
                    Finding(
                        Rule.ABSCHLAEGE_FALSCHE_ART,
                        Subject.WholeInvoice,
                        "Abschläge werden nur in einer Schlussrechnung abgesetzt (art: schlussrechnung); " +
                            "dieses Dokument ist eine ${invoice.type.documentName}.",
                    ),
                )
            }
            // Umgekehrt fehlt dann entweder die Absetzung – oder es ist gar keine
            // Schlussrechnung.
            if (invoice.type == InvoiceType.FINAL && invoice.prepayments.isEmpty()) {
                add(
                    Finding(
                        Rule.ABSCHLAEGE_FEHLEN,
                        Subject.WholeInvoice,
                        "Eine Schlussrechnung setzt die vereinnahmten Abschläge ab, hier ist keiner angegeben. " +
                            "Bitte 'abschlaege' füllen – oder, wenn es keine gab, 'art: rechnung' schreiben.",
                    ),
                )
            }

            // Derselbe Abschlag zweimal abgesetzt ist ein Kopierfehler und fiele
            // sonst nur über den Restbetrag auf.
            // Verglichen wird exakt (#53): Ob "r26-009" dasselbe Dokument meint wie
            // "R26-009", hängt am Nummernkreis des Rechnungsstellers, und den kennt
            // der Generator nicht.
            invoice.prepayments
                .groupBy { it.invoiceNumber }
                .filterValues { it.size > 1 }
                .values
                .firstOrNull()
                ?.let { duplicates ->
                    add(
                        Finding(
                            Rule.ABSCHLAG_DOPPELT,
                            Subject.Prepayment(duplicates.first().invoiceNumber),
                            "Der Abschlag steht zweimal in dieser Schlussrechnung.",
                        ),
                    )
                }

            invoice.prepayments.forEach { abschlag ->
                val subject = Subject.Prepayment(abschlag.invoiceNumber)

                // §14 Abs. 5 Satz 2 UStG setzt ab, was vereinnahmt wurde, nicht was
                // berechnet wurde.
                //
                // Fehlt die Angabe, entfallen die Regeln, die sie brauchen – aber nur
                // für diesen Abschlag. Ausgedrückt durch das frühe Weiterspringen:
                // Ein Feld am Befund, das seine Voraussetzungen aufzählt, wäre ein
                // Mechanismus für eine Handvoll Fälle.
                val vereinnahmtAm = abschlag.receivedOn
                if (vereinnahmtAm == null) {
                    add(
                        Finding(
                            Rule.ABSCHLAG_OHNE_VEREINNAHMUNG,
                            subject,
                            "ohne 'vereinnahmt-am' keine Absetzung. Abgesetzt wird, was vereinnahmt " +
                                "wurde – ein berechneter, aber unbezahlter Abschlag ist eine offene Forderung " +
                                "und wird gemahnt oder storniert.",
                        ),
                    )
                    return@forEach
                }

                // Was nach dem Rechnungsdatum liegt, ist für diese Rechnung nicht
                // vereinnahmt.
                if (vereinnahmtAm.isAfter(invoice.issueDate)) {
                    add(
                        Finding(
                            Rule.ABSCHLAG_NACH_RECHNUNGSDATUM,
                            subject,
                            "vereinnahmt am $vereinnahmtAm, also nach dem Rechnungsdatum " +
                                "${invoice.issueDate}. Für diese Schlussrechnung ist er nicht vereinnahmt.",
                        ),
                    )
                }

                // Das Gegenstück für das Rechnungsdatum (#47): Ein Abschlag geht der
                // Schlussrechnung voraus. Später datiert ist er ein Tippfehler – oder
                // eine Rechnung, die nicht hierher gehört.
                if (abschlag.issueDate.isAfter(invoice.issueDate)) {
                    add(
                        Finding(
                            Rule.ABSCHLAG_SPAETER_DATIERT,
                            subject,
                            "Rechnungsdatum ${abschlag.issueDate}, also nach dem Datum dieser " +
                                "Schlussrechnung ${invoice.issueDate}. Ein Abschlag geht der Schlussrechnung voraus.",
                        ),
                    )
                }

                // Ohne Anteile liefe die Prüfsumme über eine leere Schleife, und der
                // Abschlag setzte still 0,00 € ab.
                if (abschlag.taxShares.isEmpty()) {
                    add(
                        Finding(
                            Rule.ABSCHLAG_OHNE_STEUERANTEILE,
                            subject,
                            "keine 'steueranteile' angegeben. Ein Abschlag ohne Netto und Steuer lässt " +
                                "sich nicht absetzen – §14 Abs. 5 Satz 2 UStG setzt das Entgelt und die darauf " +
                                "entfallende Steuer ab.",
                        ),
                    )
                    return@forEach
                }

                // Die Prüfsumme gegen den Zahlendreher (ADR-0001). Die Steuer steht in
                // der Datei, weil sie aus der alten Rechnung abgeschrieben wird –
                // nachgerechnet wird sie trotzdem.
                abschlag.taxShares.forEach { anteil ->
                    val expected = anteil.net * anteil.vatRate
                    if (anteil.vat != expected) {
                        add(
                            Finding(
                                Rule.ABSCHLAG_PRUEFSUMME,
                                subject,
                                "${anteil.net} netto zu ${anteil.vatRate} ergeben $expected Umsatzsteuer, " +
                                    "angegeben sind ${anteil.vat}.",
                            ),
                        )
                    }
                }
            }
        }

    /**
     * Die Absetzung gegen die Rechnung selbst – die beiden Regeln, die die
     * Summen brauchen.
     */
    private fun validateDeduction(calculatedInvoice: CalculatedInvoice): List<Finding> =
        buildList {
            val prepayments = calculatedInvoice.prepayments
            // Zwei Positionen mit 0 % können steuerlich Verschiedenes bedeuten,
            // deshalb muss die Kombination aus Kategorie und Satz passen – nicht
            // nur der Satz.
            val present = calculatedInvoice.vatGroups.map { it.category to it.rate }.toSet()
            prepayments.forEach { abgesetzt ->
                abgesetzt.prepayment.taxShares.forEach { anteil ->
                    if ((anteil.taxCategory to anteil.vatRate) !in present) {
                        add(
                            Finding(
                                Rule.ABSCHLAG_KOMBINATION_FEHLT,
                                Subject.Prepayment(abgesetzt.prepayment.invoiceNumber),
                                "${anteil.taxCategory.code} zu " +
                                    "${anteil.vatRate} kommt in dieser Schlussrechnung nicht vor. Entweder war der " +
                                    "Abschlag damals falsch oder die Schlussrechnung ist es jetzt.",
                            ),
                        )
                    }
                }
            }

            // Eine Überzahlung ist kein Restbetrag, sondern ein anderes Dokument –
            // Gutschrift oder Storno, beides gibt es hier noch nicht.
            //
            // Nur bei Abschlägen: Ohne sie ist ein negativer Restbetrag schlicht
            // ein negativer Gesamtbetrag, und den meldet die Regel dafür. Vorher
            // fiel das nicht auf, weil der erste Wurf gewann – seit alle Befunde
            // erscheinen, stünde hier sonst "mehr Abschläge abgesetzt (0.00)"
            // unter einer Rechnung, die gar keine hat (#47).
            if (prepayments.isNotEmpty() && calculatedInvoice.remainingAmount.isNegative()) {
                add(
                    Finding(
                        Rule.RESTBETRAG_NEGATIV,
                        Subject.WholeInvoice,
                        "Der Restbetrag wäre ${calculatedInvoice.remainingAmount}: Es sind mehr Abschläge abgesetzt " +
                            "(${calculatedInvoice.prepaymentTotal}) als die Schlussrechnung ausmacht (${calculatedInvoice.grossTotal}). " +
                            "Eine Überzahlung gehört in eine Gutschrift, nicht in eine Rechnung.",
                    ),
                )
            }
        }

    /**
     * Der Zeitpunkt der Vereinnahmung (BT-7) gehört auf die Abschlagsrechnung –
     * und nur dorthin. Auf einer Rechnung über eine erbrachte Leistung ist die
     * Umsatzsteuer mit der Leistung entstanden, nicht mit dem Geldeingang; die
     * Angabe wäre dort keine Zusatzinformation, sondern eine falsche.
     */
    private fun validateReceipt(invoice: Invoice): List<Finding> =
        buildList {
            val vereinnahmt = invoice.receivedOn ?: return@buildList

            if (invoice.type != InvoiceType.PREPAYMENT) {
                add(
                    Finding(
                        Rule.VEREINNAHMUNG_FALSCHE_ART,
                        Subject.WholeInvoice,
                        "'vereinnahmt-am' gehört nur auf eine Abschlagsrechnung (art: abschlagsrechnung); " +
                            "dieses Dokument ist eine ${invoice.type.documentName}.",
                    ),
                )
            }
            // Gleiche Prüfung wie beim Datum der Bestätigungsabfrage: Was nach dem
            // Rechnungsdatum liegt, belegt für diese Rechnung nichts. Steht der
            // Zeitpunkt noch nicht fest, bleibt die Angabe weg.
            if (vereinnahmt.isAfter(invoice.issueDate)) {
                add(
                    Finding(
                        Rule.VEREINNAHMUNG_NACH_RECHNUNGSDATUM,
                        Subject.WholeInvoice,
                        "Die Vereinnahmung am $vereinnahmt liegt nach dem Rechnungsdatum ${invoice.issueDate}. " +
                            "Steht der Zeitpunkt noch nicht fest, bitte 'vereinnahmt-am' weglassen.",
                    ),
                )
            }
        }

    /**
     * Verlangt den dokumentierten Nachweis der Bestätigungsabfrage, bevor ohne
     * Umsatzsteuer abgerechnet wird.
     *
     * **Welche** Kategorie was verlangt, steht am Enum: [VatIdConfirmation]
     * und die Werte in [TaxCategory]. Dort und nicht hier, weil es eine
     * Eigenschaft der Kategorie ist und keine dieser Funktion – vorher stand
     * die Fallunterscheidung als Konstantenvergleich in dieser Zeile, und wer
     * wissen wollte, was für "G" gilt, musste den ganzen Calculator lesen.
     *
     * Hier steht nur noch, wie daraus eine Pflicht wird: Der Nachweis muss
     * vorliegen, er muss vor dem Rechnungsdatum erbracht sein, und fehlt er,
     * bricht der Lauf ab.
     *
     * Das ist Steuerrecht und keine Rechtsberatung – wer die Regeln ändert,
     * sollte sie mit dem Steuerberater abgleichen.
     */
    private fun validateVatIdCheck(invoice: Invoice, config: SellerConfig, kategorien: Set<TaxCategory>): List<Finding> =
        buildList {
            if (kategorien.isEmpty()) return@buildList
            val kunde = invoice.customer
            val ausland = istAusland(invoice, config)
            val inEu = EuMemberStates.contains(kunde.address.countryCode)
            val pflicht =
                kategorien.any {
                    when (it.vatIdConfirmation) {
                        VatIdConfirmation.ALWAYS -> true
                        VatIdConfirmation.IN_EU -> ausland && inEu
                        VatIdConfirmation.NEVER -> false
                    }
                }
            if (!pflicht) return@buildList

            // Ohne Nachweis entfällt auch die Frage nach seinem Datum – deshalb
            // hier das frühe Weiterspringen.
            val pruefung = kunde.vatIdCheck
            if (pruefung == null) {
                add(
                    Finding(
                        Rule.BESTAETIGUNGSABFRAGE_FEHLT,
                        Subject.WholeInvoice,
                        "Ohne dokumentierte Bestätigungsabfrage keine Rechnung ohne Umsatzsteuer. " +
                            "Bitte die USt-IdNr. ${kunde.vatId} beim BZSt oder über VIES prüfen und das " +
                            "Ergebnis unter 'kunde: ust-id-geprueft' eintragen (am, verfahren, nachweis).",
                    ),
                )
                return@buildList
            }

            // Eine Abfrage nach dem Rechnungsdatum belegt für diese Rechnung nichts.
            if (pruefung.date.isAfter(invoice.issueDate)) {
                add(
                    Finding(
                        Rule.BESTAETIGUNGSABFRAGE_NACH_RECHNUNGSDATUM,
                        Subject.WholeInvoice,
                        "Die Bestätigungsabfrage vom ${pruefung.date} liegt nach dem Rechnungsdatum " +
                            "${invoice.issueDate} – für diese Rechnung belegt sie nichts.",
                    ),
                )
            }
        }

    /**
     * Ausland heißt hier: Das Land des Kunden ist nicht das Land des
     * Rechnungsstellers – nicht "nicht Deutschland". Die beiden fallen nur
     * zusammen, solange der Rechnungssteller in Deutschland sitzt, und genau
     * diese Verwechslung stand vorher im Renderer.
     *
     * Zwei Regeln fragen danach, und vorher stand die Zeile zweimal wortgleich
     * da. Der Vergleich selbst ist inzwischen ein schlichtes `!=`, weil
     * [CountryCode] die Schreibweise vereinheitlicht.
     */
    private fun istAusland(invoice: Invoice, config: SellerConfig): Boolean =
        invoice.customer.address.countryCode != config.address.countryCode

    /** Hinweise, die den Lauf nicht abbrechen, aber in die eigene Akte gehören. */
    private fun collectHints(invoice: Invoice, config: SellerConfig): List<Finding> =
        buildList {
            val kunde = invoice.customer
            val kategorien = invoice.items.map { it.taxCategory }.toSet()
            val ausland = istAusland(invoice, config)

            // Menge 0 ist zulässig, aber fast immer ein Tippfehler (#47).
            invoice.items.forEachIndexed { index, item ->
                if (item.quantity.signum() == 0) {
                    add(
                        Finding(
                            Rule.MENGE_NULL,
                            Subject.Item(index, item.name),
                            "Menge 0 – die Position steht mit 0,00 € auf der Rechnung. Ist das gewollt?",
                        ),
                    )
                }
            }

            // §14 Abs. 2 UStG verlangt die Rechnung innerhalb von sechs Monaten nach
            // Ausführung der Leistung (#47). Verglichen mit dem Ende der Leistung,
            // nicht mit heute: So ergibt dieselbe Datei jederzeit dieselben
            // Hinweise, auch wenn sie ein Jahr später noch einmal erzeugt wird. Bei
            // der Abschlagsrechnung schweigt er, denn ihr Zeitraum ist eine Prognose.
            val serviceEnd = invoice.servicePeriod.end
            if (!invoice.type.servicePeriodIsForecast && invoice.issueDate.isAfter(serviceEnd.plusMonths(6))) {
                add(
                    Finding(
                        Rule.RECHNUNG_SPAET,
                        Subject.WholeInvoice,
                        "Die Rechnung vom ${invoice.issueDate} kommt mehr als sechs Monate nach dem Ende der " +
                            "Leistung am $serviceEnd. §14 Abs. 2 UStG verlangt sie innerhalb von sechs Monaten – " +
                            "ist das Rechnungsdatum richtig?",
                    ),
                )
            }

            // Die Altersprüfung der EU-Liste hängt bewusst an `ausland` und läuft
            // nicht immer: Bei einer Inlandsrechnung wird die Liste gar nicht
            // befragt, ein Hinweis darauf wäre also Lärm. Und Hinweise, die bei
            // jeder Rechnung erscheinen, liest nach der dritten niemand mehr –
            // dann geht der eine unter, auf den es ankommt.
            if (ausland) {
                val listenAlter = ChronoUnit.MONTHS.between(EuMemberStates.AS_OF, invoice.issueDate)
                if (listenAlter >= 12) {
                    add(
                        Finding(
                            Rule.EU_LISTE_ALT,
                            Subject.WholeInvoice,
                            "Die hinterlegte Liste der EU-Mitgliedstaaten ist $listenAlter Monate alt " +
                                "(zuletzt geprüft am ${EuMemberStates.AS_OF}). Sie entscheidet mit, ob für " +
                                "${kunde.address.countryCode} ein Nachweis verlangt wird – bitte gegen die " +
                                "amtliche Aufstellung abgleichen und EuMemberStates.AS_OF nachziehen.",
                        ),
                    )
                }
            }

            // Eine Kategorie, deren Nachweis nur innerhalb der EU zu führen
            // ist, trifft hier auf einen Kunden ausserhalb: Verlangt wird dann
            // nichts (siehe validateVatIdCheck), aber die Akte braucht trotzdem
            // etwas.
            val nurInEu = kategorien.any { it.vatIdConfirmation == VatIdConfirmation.IN_EU }
            if (nurInEu && ausland && !EuMemberStates.contains(kunde.address.countryCode)) {
                add(
                    Finding(
                        Rule.KEIN_VIES_MOEGLICH,
                        Subject.WholeInvoice,
                        "${kunde.address.countryCode} ist kein EU-Mitgliedstaat – eine VIES-Abfrage gibt es dafür " +
                            "nicht. Für die Akte braucht es einen anderen Nachweis der Unternehmereigenschaft, " +
                            "etwa eine Unternehmerbescheinigung.",
                    ),
                )
            }
            kunde.vatIdCheck?.let { pruefung ->
                if (pruefung.method == CheckMethod.SIMPLE) {
                    add(
                        Finding(
                            Rule.ABFRAGE_EINFACH,
                            Subject.WholeInvoice,
                            "Die Abfrage vom ${pruefung.date} war einfach, nicht qualifiziert. Nur die " +
                                "qualifizierte Abfrage beim BZSt gleicht Name und Anschrift ab und verschafft " +
                                "Vertrauensschutz.",
                        ),
                    )
                }
                if (pruefung.reference == null) {
                    add(
                        Finding(
                            Rule.ABFRAGE_OHNE_NACHWEIS,
                            Subject.WholeInvoice,
                            "Zur Abfrage vom ${pruefung.date} ist kein Nachweis vermerkt – " +
                                "ohne Protokoll nützt sie im Zweifel wenig.",
                        ),
                    )
                }
                val alter = ChronoUnit.MONTHS.between(pruefung.date, invoice.issueDate)
                if (alter >= 12) {
                    add(
                        Finding(
                            Rule.ABFRAGE_ALT,
                            Subject.WholeInvoice,
                            "Die Bestätigungsabfrage ist $alter Monate alt. Bei laufender Geschäftsbeziehung " +
                                "wird üblicherweise regelmäßig erneut geprüft.",
                        ),
                    )
                }
            }
        }

    /**
     * Der Befreiungsgrund einer Gruppe. Alle Positionen einer Gruppe müssen sich
     * einig sein: BG-23 trägt genau einen Grund je Kategorie und Satz, zwei
     * verschiedene Begründungen ließen sich dort nicht abbilden.
     */
    private fun reasonFor(category: TaxCategory, group: List<CalculatedLine>, findings: MutableList<Finding>): String? {
        if (!category.requiresReason) return null
        val gruende = group.mapNotNull { it.item.taxExemptionReason }.distinct()
        if (gruende.size > 1) {
            findings +=
                Finding(
                    Rule.BEFREIUNGSGRUENDE_UNEINHEITLICH,
                    Subject.WholeInvoice,
                    "Kategorie ${category.code} hat zwei verschiedene Befreiungsgründe: " +
                        gruende.joinToString(" / ") + ". Die Steueraufschlüsselung trägt nur einen je Kategorie.",
                )
            // Ohne Einigkeit trägt die Gruppe keinen Grund: Einen der beiden zu
            // wählen hieße raten, welcher gemeint war.
            return null
        }
        return gruende.firstOrNull() ?: category.defaultReason
    }
}
