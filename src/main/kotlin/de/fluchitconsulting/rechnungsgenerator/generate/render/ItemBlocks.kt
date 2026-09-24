package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.accepted.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.accepted.InvoiceVariableResolver
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceVariable
import de.fluchitconsulting.rechnungsgenerator.core.model.LineItem
import de.fluchitconsulting.rechnungsgenerator.core.model.SkontoTier
import de.fluchitconsulting.rechnungsgenerator.core.values.Discount

/**
 * Positionstabelle, Summenblock und die Hinweise darunter: Steuerbefreiungen,
 * der Pflichtsatz der Rechnungsart und das Skonto. Artabhängig sind hier nur
 * Beschriftungen und der Pflichtsatz, beides über [InvoiceType] – ein `when`
 * über die Rechnungsart gibt es in diesen Blöcken nicht.
 */
internal object ItemBlocks {
    // Die Zeilen werden mit \positionstrenner VERBUNDEN, nicht damit
    // abgeschlossen: Ein Umbruch hinter der letzten Zeile erzeugt in LaTeX eine
    // leere Extrazeile.
    //
    // Die laufende Nummer beginnt bei 1. Ob ein Design sie setzt, entscheidet
    // die Vorlage (#188); `mapIndexed` liefert zum Element seinen Index.
    fun lineItems(calculatedInvoice: CalculatedInvoice): Latex =
        calculatedInvoice.lines
            .mapIndexed { index, line ->
                val item = line.item
                Latex.macro(
                    TemplateMacro.POSITIONSZEILE,
                    listOf(
                        "${index + 1}".escapeLatex(),
                        describeItem(item, calculatedInvoice.invoice.type),
                        GermanFormat.quantity(item.quantity),
                        item.unit.label.escapeLatex(),
                        GermanFormat.priceWithCurrency(item.unitPrice),
                        // In der Spalte steht, was vereinbart wurde: der Satz
                        // oder der Betrag, und der ohne Vorzeichen – das trägt
                        // im Storno die Menge.
                        when (val discount = item.discount) {
                            null -> Latex.EMPTY
                            is Discount.Rate -> GermanFormat.percent(discount.rate)
                            is Discount.Amount -> GermanFormat.amountWithCurrency(discount.amount)
                        },
                        GermanFormat.amountWithCurrency(line.net),
                    ),
                )
            }.joinToLatex(rowSeparator(TemplateMacro.POSITIONSTRENNER))

    /**
     * Der Schalter für die Rabattspalte: nicht leer, sobald eine Position einen
     * Rabatt hat. Welches Wort darin steht, ist gleich – die Vorlage fragt nur
     * `\ifblank`.
     */
    fun discountColumn(calculatedInvoice: CalculatedInvoice): Latex =
        if (calculatedInvoice.invoice.items.any { it.discount != null }) "rabatt".escapeLatex() else Latex.EMPTY

    /**
     * Die Beschreibungsspalte einer Position: Bezeichnung, darunter – falls
     * vorhanden – die Zusatzangaben klein und gedämpft. Das ergibt die
     * zweizeilige Position, die die bisherigen Rechnungen hatten, nur dass die
     * zweite Zeile hier aus benannten Feldern entsteht statt aus einem
     * Textzusatz.
     *
     * \newline statt \\ für den Umbruch: Die Spalte ist eine X-Spalte, also
     * ein Absatz. Ein \\ darin würde die Tabellenzeile beenden statt die
     * Textzeile.
     */
    private fun describeItem(item: LineItem, type: InvoiceType): Latex {
        val zusatz =
            buildList {
                item.description?.let { add(it.escapeLatex()) }
                item.note?.let { add(it.escapeLatex()) }
                // Auch hier die Beschriftung der Rechnungsart: Auf einer
                // Abschlagsrechnung ist der Zeitraum einer Position genauso eine
                // Prognose wie der Zeitraum der ganzen Rechnung.
                item.servicePeriod?.let { add("${type.itemPeriodLabel}: ".escapeLatex() + servicePeriod(it, type)) }
            }
        val bezeichnung = item.name.escapeLatex()
        if (zusatz.isEmpty()) return bezeichnung
        return bezeichnung + Latex.macro(TemplateMacro.POSITIONSDETAIL, listOf(zusatz.joinToLatex(" · ")))
    }

    /**
     * Der Summenblock: Beträge und Sätze, die Beschriftungen setzt die Vorlage
     * (#217).
     *
     * Hier steht eine Gruppe genau dann, wenn sie keinen Befreiungsgrund trägt.
     * Eine befreite Gruppe erschiene sonst als "zzgl. 0 % USt. 0,00 €" – das
     * sähe aus wie ein Rechenfehler; warum keine Steuer anfällt, sagt
     * [taxNotes] darunter. Der Nullsatz (Kategorie Z) trägt keinen Grund und
     * steht deshalb hier: Er ist keine Befreiung, sondern eine Besteuerung mit
     * 0 %, und §14 Abs. 4 Nr. 8 UStG verlangt dafür Satz und Steuerbetrag
     * (#218). Nicht der Steuerbetrag von null: Danach fiele eine Z-Gruppe ganz
     * vom Blatt.
     */
    fun totalsBlock(calculatedInvoice: CalculatedInvoice): Latex =
        buildList {
            add(Latex.macro(TemplateMacro.NETTOZEILE, listOf(GermanFormat.amountWithCurrency(calculatedInvoice.netTotal))))
            // §14 Abs. 4 Nr. 7 UStG verlangt das Entgelt je Steuersatz (#218).
            // Bei nur einer Gruppe ist es die Zwischensumme und stünde doppelt da;
            // sonst steht es in der Steuerzeile. Steuerfreie Gruppen zählen mit,
            // ihr Entgelt nennt taxNotes.
            val showsBase = calculatedInvoice.vatGroups.size > 1
            calculatedInvoice.vatGroups.filter { it.exemptionReason == null }.forEach { group ->
                // Das Prozentzeichen ist maskiert, bevor es hier ankommt –
                // GermanFormat.percent liefert ein fertiges Fragment.
                val base = if (showsBase) GermanFormat.amountWithCurrency(group.base) else Latex.EMPTY
                add(
                    Latex.macro(
                        TemplateMacro.STEUERZEILE,
                        listOf(GermanFormat.percent(group.rate), base, GermanFormat.amountWithCurrency(group.amount)),
                    ),
                )
            }
        }.map { Latex.raw("  ") + it }
            .joinToLatex(rowSeparator(TemplateMacro.SUMMENTRENNER)) +
            Latex.raw("\n  ") +
            Latex.macro(TemplateMacro.GESAMTZEILE, listOf(GermanFormat.amountWithCurrency(calculatedInvoice.grossTotal)))

    /**
     * Der Pflichtsatz der Rechnungsart, unter den Summen. Ob eine Art einen hat
     * und wie er lautet, trägt [InvoiceType.mandatoryNote], den Abstand die
     * Vorlage: \hinweisabsatz bringt ihn mit, sodass bei den übrigen Arten der
     * leere Platzhalter auch wirklich nichts einnimmt.
     *
     * Der Satz darf Werte der Rechnung nennen, als `<<NAME>>`; welche es gibt,
     * zählt [InvoiceVariable] auf, woher sie kommen, [InvoiceVariableResolver].
     */
    fun typeNote(acceptedInvoice: AcceptedInvoice): Latex =
        acceptedInvoice.calculated.invoice.type.mandatoryNote
            ?.let { Latex.macro(TemplateMacro.HINWEISABSATZ, listOf(it.toLatex(InvoiceVariableResolver(acceptedInvoice)))) }
            ?: Latex.EMPTY

    /**
     * Das Zahlungsziel als ein Satz unter den Summen, als Frist und nicht als
     * Datum: "Die Zahlung ist 14 Tage nach Rechnungsdatum fällig." Das Design
     * `prag` setzt ihn statt der Kopfzeile, `helsinki` übergeht ihn (#211).
     *
     * Der Bezug ist das Rechnungsdatum und nicht der Erhalt der Rechnung: Von
     * dort rechnen [CalculatedInvoice.dueDate] und das Skonto daneben. "ohne
     * Abzug" nur mit Skonto, sonst fragte man, welcher Abzug gemeint ist.
     * "sofort" und Skonto zusammen gibt es nicht, weil eine Skontofrist vor dem
     * Zahlungsziel endet (SKONTOFRIST_NICHT_VOR_ZAHLUNGSZIEL).
     *
     * Ob der Satz dasteht, sagt [CalculatedInvoice.dueDate] – wie die Kopfzeile.
     */
    fun paymentTermNote(calculatedInvoice: CalculatedInvoice): Latex {
        if (calculatedInvoice.dueDate == null) return Latex.EMPTY

        val days = calculatedInvoice.paymentTermDays
        val withoutDeduction = if (calculatedInvoice.skonto.isEmpty()) "" else "ohne Abzug "
        val deadline =
            when (days) {
                0 -> "sofort"
                1 -> "${withoutDeduction}1 Tag nach Rechnungsdatum"
                else -> "$withoutDeduction$days Tage nach Rechnungsdatum"
            }
        return Latex.macro(TemplateMacro.HINWEISABSATZ, listOf("Die Zahlung ist $deadline fällig.".escapeLatex()))
    }

    /**
     * Das Skonto als ein Satz unter den Summen, eine Stufe je Satzteil, nach der
     * Skontofrist sortiert: "Bei Zahlung bis 21. August 2026 können 2 % Skonto
     * abgezogen werden, bei Zahlung bis 24. August 2026 1 %."
     *
     * Ein Satz und keine Zeilen im Kopf (#183): Dort steht weiter "Zahlbar ohne
     * Abzug bis …", als Gegenstück. Kein Datum steht doppelt, weil jede
     * Skontofrist vor dem Zahlungsziel endet (SKONTOFRIST_NICHT_VOR_ZAHLUNGSZIEL).
     * Den Skontobetrag nennt der Satz bewusst nicht.
     *
     * Zwischen "bis" und dem Datum ein geschütztes Leerzeichen, das [escapeLatex]
     * zu `~` macht: Ein Zeilenende dort ließe "bis" allein am Zeilenende stehen.
     *
     * Ob Skonto auf dieser Rechnungsart stehen darf, fragt der Renderer nicht
     * noch einmal: Eine angenommene Rechnung mit Skonto ist eine Rechnung oder
     * Teilrechnung, die zur Zahlung auffordert (SKONTO_AUF_DIESER_ART_NICHT_MOEGLICH,
     * SKONTO_OHNE_ZAHLUNGSAUFFORDERUNG).
     */
    fun skontoNote(calculatedInvoice: CalculatedInvoice): Latex {
        val stufen = calculatedInvoice.skonto
        if (stufen.isEmpty()) return Latex.EMPTY

        fun frist(stufe: SkontoTier): Latex = together(GermanFormat.date(calculatedInvoice.skontoUntil(stufe)))

        val erste = stufen.first()
        val satz =
            "Bei Zahlung bis\u00A0".escapeLatex() + frist(erste) + " können ".escapeLatex() +
                GermanFormat.percent(erste.rate) + " Skonto abgezogen werden".escapeLatex() +
                stufen
                    .drop(1)
                    .map { stufe ->
                        ", bei Zahlung bis\u00A0".escapeLatex() + frist(stufe) + " ".escapeLatex() +
                            GermanFormat.percent(stufe.rate)
                    }.joinToLatex() +
                ".".escapeLatex()
        return Latex.macro(TemplateMacro.HINWEISABSATZ, listOf(satz))
    }

    /**
     * Die Begründungen für Positionen ohne Umsatzsteuer – EN 16931 BT-120.
     *
     * Bei Reverse Charge ist das keine Höflichkeit: §14a Abs. 5 UStG verlangt
     * den Hinweis auf die Steuerschuldnerschaft des Leistungsempfängers
     * ausdrücklich auf der Rechnung.
     *
     * Gefragt wird nur nach dem Grund, nicht zusätzlich nach dem Steuerbetrag
     * null: Einen Grund tragen nur Kategorien, die auch den Satz 0 verlangen –
     * das hält ein Test am Enum fest. Stünde beides hier, entschiede der
     * Renderer selbst, was die Gruppe schon weiß (#148).
     */
    fun taxNotes(calculatedInvoice: CalculatedInvoice): Latex =
        calculatedInvoice.vatGroups
            .mapNotNull { group ->
                group.exemptionReason?.let { reason ->
                    "Auf ".escapeLatex() + GermanFormat.amountWithCurrency(group.base) +
                        " entfällt keine Umsatzsteuer: ".escapeLatex() + reason.escapeLatex() +
                        ".".escapeLatex()
                }
            }.joinToLatex(Latex.macro(TemplateMacro.ZEILENWECHSEL))
}
