package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.accepted.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.accepted.InvoiceVariableResolver
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Discount
import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.LineItem
import de.fluchitconsulting.rechnungsgenerator.core.model.SkontoTier
import de.fluchitconsulting.rechnungsgenerator.core.values.Language

/** Positionstabelle, Summenblock und die Hinweise darunter. Was an der Rechnungsart hängt, kommt aus [InvoiceType]. */
internal object ItemBlocks {
    // Verbunden, nicht abgeschlossen: Ein Trenner hinter der letzten Zeile ergäbe in LaTeX eine leere Zeile.
    fun lineItems(calculatedInvoice: CalculatedInvoice): Latex =
        calculatedInvoice.lines
            .mapIndexed { index, line ->
                val item = line.item
                Latex.macro(
                    TemplateMacro.POSITIONSZEILE,
                    listOf(
                        "${index + 1}".escapeLatex(),
                        describeItem(item, calculatedInvoice.invoice),
                        Numbers.quantity(item.quantity),
                        item.unit.nameFor(item.quantity, calculatedInvoice.invoice.language).escapeLatex(),
                        Numbers.price(item.unitPrice),
                        // Was vereinbart wurde, Satz oder Betrag; der Betrag ohne Vorzeichen, das trägt im Storno die Menge.
                        when (val discount = item.discount) {
                            null -> Latex.EMPTY
                            is Discount.Rate -> Numbers.percent(discount.rate)
                            is Discount.Amount -> Numbers.amount(discount.amount)
                        },
                        Numbers.amount(line.net),
                    ),
                )
            }.joinToLatex(rowSeparator(TemplateMacro.POSITIONSTRENNER))

    /** Der Schalter für die Rabattspalte. Das Wort ist gleich, die Vorlage fragt nur `\ifblank`. */
    fun discountColumn(calculatedInvoice: CalculatedInvoice): Latex =
        if (calculatedInvoice.invoice.items.any { it.discount != null }) "rabatt".escapeLatex() else Latex.EMPTY

    private fun describeItem(item: LineItem, invoice: Invoice): Latex {
        val type = invoice.type
        val details =
            buildList {
                item.description?.let { add(it.escapeLatex()) }
                item.note?.let { add(it.escapeLatex()) }
                // Auf einer Abschlagsrechnung ist auch der Zeitraum einer Position eine Prognose.
                item.servicePeriod?.let { add("${type.itemPeriodLabel(invoice.language)}: ".escapeLatex() + servicePeriod(it, invoice)) }
            }
        val name = item.name.escapeLatex()
        if (details.isEmpty()) return name
        return name + Latex.macro(TemplateMacro.POSITIONSDETAIL, listOf(details.joinToLatex(" · ")))
    }

    /**
     * Der Summenblock. Eine Steuerzeile hat jede Gruppe ohne Befreiungsgrund, nicht jede mit Steuer über null:
     * Der Nullsatz Z ist keine Befreiung, und §14 Abs. 4 Nr. 8 UStG verlangt Satz und Betrag (#218).
     */
    fun totalsBlock(calculatedInvoice: CalculatedInvoice): Latex =
        buildList {
            add(Latex.macro(TemplateMacro.NETTOZEILE, listOf(Numbers.amount(calculatedInvoice.netTotal))))
            // Das Entgelt je Satz (§14 Abs. 4 Nr. 7 UStG) erst ab zwei Gruppen, sonst stünde die Zwischensumme
            // doppelt. Steuerfreie Gruppen zählen mit.
            val showsBase = calculatedInvoice.vatGroups.size > 1
            calculatedInvoice.vatGroups.filter { it.exemptionReason == null }.forEach { group ->
                val base = if (showsBase) Numbers.amount(group.base) else Latex.EMPTY
                add(
                    Latex.macro(
                        TemplateMacro.STEUERZEILE,
                        listOf(Numbers.percent(group.rate), base, Numbers.amount(group.amount)),
                    ),
                )
            }
        }.map { Latex.raw("  ") + it }
            .joinToLatex(rowSeparator(TemplateMacro.SUMMENTRENNER)) +
            Latex.raw("\n  ") +
            Latex.macro(TemplateMacro.GESAMTZEILE, listOf(Numbers.amount(calculatedInvoice.grossTotal)))

    /** Der Pflichtsatz der Rechnungsart. `\hinweisabsatz` bringt den Abstand mit, damit ein leerer Platzhalter nichts einnimmt. */
    fun typeNote(acceptedInvoice: AcceptedInvoice): Latex =
        acceptedInvoice.calculated.invoice
            .let { it.type.mandatoryNote(it.language) }
            ?.let {
                val format = DateFormat(acceptedInvoice.calculated.invoice.language)
                Latex.macro(TemplateMacro.HINWEISABSATZ, listOf(it.toLatex(format, InvoiceVariableResolver(acceptedInvoice))))
            }
            ?: Latex.EMPTY

    /**
     * Die Zahlungsfrist als Satz, für `prag` (#211). „ohne Abzug“ nur mit Skonto; „sofort“ und Skonto zusammen
     * gibt es nicht (SKONTOFRIST_NICHT_VOR_ZAHLUNGSZIEL).
     */
    fun paymentTermNote(calculatedInvoice: CalculatedInvoice): Latex {
        if (calculatedInvoice.dueDate == null) return Latex.EMPTY

        val days = calculatedInvoice.paymentTermDays
        val withSkonto = calculatedInvoice.skonto.isNotEmpty()
        val sentence =
            when (val language = calculatedInvoice.invoice.language) {
                Language.GERMAN -> {
                    val withoutDeduction = if (withSkonto) "ohne Abzug " else ""
                    val deadline =
                        when (days) {
                            0 -> "sofort"
                            1 -> "${withoutDeduction}1 Tag nach Rechnungsdatum"
                            else -> "$withoutDeduction$days Tage nach Rechnungsdatum"
                        }
                    "Die Zahlung ist $deadline fällig."
                }
                Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> {
                    // „in full“ wie „Due in full by“ im Kopf von helsinki.
                    val withoutDeduction = if (withSkonto) "in full " else ""
                    when (days) {
                        0 -> "Payment is due immediately."
                        1 -> "Payment is due ${withoutDeduction}1 day after the invoice date."
                        else -> "Payment is due $withoutDeduction$days days after the invoice date."
                    }
                }
            }
        return Latex.macro(TemplateMacro.HINWEISABSATZ, listOf(sentence.escapeLatex()))
    }

    /**
     * Das Skonto als ein Satz, eine Stufe je Satzteil. Den Skontobetrag nennt er bewusst nicht (#183). Nach
     * „bis“ ein geschütztes Leerzeichen, damit es nicht allein am Zeilenende steht.
     */
    fun skontoNote(calculatedInvoice: CalculatedInvoice): Latex {
        val stufen = calculatedInvoice.skonto
        if (stufen.isEmpty()) return Latex.EMPTY

        val language = calculatedInvoice.invoice.language
        val format = DateFormat(language)

        fun deadline(stufe: SkontoTier): Latex = together(format.date(calculatedInvoice.skontoUntil(stufe)))

        val firstTier = stufen.first()
        val furtherTiers = stufen.drop(1)
        val sentence =
            when (language) {
                Language.GERMAN -> {
                    "Bei Zahlung bis\u00A0".escapeLatex() + deadline(firstTier) + " können ".escapeLatex() +
                        Numbers.percent(firstTier.rate) + " Skonto abgezogen werden".escapeLatex() +
                        furtherTiers
                            .map { stufe ->
                                ", bei Zahlung bis\u00A0".escapeLatex() + deadline(stufe) + " ".escapeLatex() +
                                    Numbers.percent(stufe.rate)
                            }.joinToLatex()
                }
                // „of 2%“ statt „a 2%“: Der Artikel hinge an der Aussprache der Zahl – a 2%, an 8%.
                Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> {
                    "For payment by\u00A0".escapeLatex() + deadline(firstTier) + ", an early payment discount of ".escapeLatex() +
                        Numbers.percent(firstTier.rate) + " may be deducted".escapeLatex() +
                        furtherTiers
                            .map { stufe ->
                                "; for payment by\u00A0".escapeLatex() + deadline(stufe) + ", ".escapeLatex() +
                                    Numbers.percent(stufe.rate)
                            }.joinToLatex()
                }
            } + ".".escapeLatex()
        return Latex.macro(TemplateMacro.HINWEISABSATZ, listOf(sentence))
    }

    /** Die Umsatzsteuer in Euro, mit Kurs und Monat, damit sich der Betrag nachrechnen lässt (ADR-0027). */
    fun conversionNote(calculatedInvoice: CalculatedInvoice): Latex {
        val conversion = calculatedInvoice.invoice.conversion ?: return Latex.EMPTY
        val euro = Numbers.euroAmount(checkNotNull(calculatedInvoice.vatTotalInEuro))
        val rate = together(Numbers.exchangeRate(conversion.rate))
        val month = DateFormat(calculatedInvoice.invoice.language).month(conversion.month)

        val sentence =
            when (calculatedInvoice.invoice.language) {
                Language.GERMAN -> {
                    "Umgerechnet in Euro beträgt die Umsatzsteuer ".escapeLatex() + euro + " (".escapeLatex() + rate +
                        ", Monatsdurchschnittskurs des BMF für ".escapeLatex() + month + ").".escapeLatex()
                }
                // Ausgeschrieben: „BMF“ kennt nur, wer deutsche Umsatzsteuer erklärt.
                Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> {
                    "Converted to euros, the VAT amounts to ".escapeLatex() + euro + " (".escapeLatex() + rate +
                        ", monthly average rate of the German Federal Ministry of Finance for ".escapeLatex() + month +
                        ").".escapeLatex()
                }
            }
        return Latex.macro(TemplateMacro.HINWEISABSATZ, listOf(sentence))
    }

    /**
     * Die Befreiungsgründe (BT-120), bei Reverse Charge Pflicht nach §14a UStG. Gefragt wird nur nach dem Grund,
     * wie im Summenblock (#148). Jeder Satz ein eigenes Makro: Zwischen zwei Sätzen darf die Seite brechen (ADR-0020).
     */
    fun taxNotes(calculatedInvoice: CalculatedInvoice): Latex =
        calculatedInvoice.vatGroups
            .mapNotNull { group ->
                group.exemptionReason?.let { reason ->
                    // „No VAT is charged“ statt „VAT exempt“: O ist nicht steuerbar, und das ist nicht befreit.
                    val sentence =
                        when (calculatedInvoice.invoice.language) {
                            Language.GERMAN -> {
                                "Auf ".escapeLatex() + Numbers.amount(group.base) +
                                    " entfällt keine Umsatzsteuer: ".escapeLatex()
                            }
                            Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> {
                                "No VAT is charged on ".escapeLatex() + Numbers.amount(group.base) + ": ".escapeLatex()
                            }
                        } + reason.escapeLatex() + ".".escapeLatex()
                    Latex.macro(TemplateMacro.STEUERHINWEIS, listOf(sentence))
                }
            }.joinToLatex("\n")
}
