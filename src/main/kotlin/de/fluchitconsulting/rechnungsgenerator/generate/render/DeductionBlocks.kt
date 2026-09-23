package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.accepted.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.rules.Deduction
import de.fluchitconsulting.rechnungsgenerator.rules.InvoiceKind

/**
 * Die Absetzung der vereinnahmten Abschläge – der Block unter dem
 * Gesamtbetrag, den eine Schlussrechnung bekommt. Hier steht das dritte
 * `when` über die Rechnungsart (#128).
 */
internal object DeductionBlocks {
    /**
     * Der Block steht **unter** den Summen und nie in der Positionstabelle
     * (ADR-0002): Ein Abschlag ist keine negative Leistung. Als Position
     * gerechnet, senkte er die Bemessungsgrundlage und damit die ausgewiesene
     * Umsatzsteuer der Schlussrechnung – die aber schuldet der
     * Rechnungssteller in voller Höhe; abgesetzt wird nur, was auf den
     * Abschlag schon entrichtet wurde.
     *
     * Jeder Abschlag steht einzeln da. UStAE 14.8 Abs. 7 ließe die Summe
     * genügen, aber der Kunde soll sehen, welche seiner Zahlungen abgesetzt
     * wurde – und die Zeilen liegen ohnehin im Modell.
     */
    fun deductionBlock(acceptedInvoice: AcceptedInvoice): Latex {
        val calculatedInvoice = acceptedInvoice.calculated
        // Nur eine Schlussrechnung setzt ab. Die übrigen Varianten stehen
        // ausgeschrieben da statt als `else`: Eine neue muss hier sagen, ob sie
        // absetzt (#128). `return` ist in Kotlin ein Ausdruck und passt deshalb
        // als Wert eines Zweigs.
        val deductions =
            when (val kind = acceptedInvoice.kind) {
                is InvoiceKind.FinalInvoice -> kind.deductions
                is InvoiceKind.Ordinary, is InvoiceKind.PrepaymentInvoice, is InvoiceKind.Cancellation -> return Latex.EMPTY
            }

        // Derselbe Trenner wie bei den Positionen: Jede Zeile hat eine
        // Zusatzzeile, genau wie eine Position mit Beschreibung.
        //
        // Das Minus davor schreibt der Renderer und nicht die Vorlage: Es ist
        // das Vorzeichen des abgesetzten Betrags und damit Inhalt, so wie das
        // €-Zeichen zum Betrag gehört (#76).
        val rows =
            deductions
                .map { abgesetzt ->
                    Latex.raw("  ") +
                        Latex.macro(
                            TemplateMacro.SUMMENZEILE,
                            listOf(
                                describePrepayment(abgesetzt),
                                "\u2212".escapeLatex() + GermanFormat.amountWithCurrency(abgesetzt.deducted.gross),
                            ),
                        )
                }.joinToLatex(rowSeparator(TemplateMacro.POSITIONSTRENNER))

        // Ein Restbetrag von null bekommt einen Satz statt einer Zahl: Ein
        // nacktes "0,00 €" unter dem Strich sieht aus wie ein Fehler, nicht wie
        // ein ausgeglichener Betrag.
        val (closingRow, trailingNote) =
            if (calculatedInvoice.settledByPrepayments) {
                // Kein Absatzwechsel davor: \hinweisabsatz beginnt selbst mit \par.
                Latex.EMPTY to
                    Latex.macro(
                        TemplateMacro.HINWEISABSATZ,
                        listOf(
                            (
                                "Der Rechnungsbetrag ist durch die vereinnahmten Abschläge ausgeglichen; " +
                                    "es verbleibt kein Restbetrag."
                            ).escapeLatex(),
                        ),
                    )
            } else {
                summaryRow("Restbetrag", calculatedInvoice.remainingAmount) to Latex.EMPTY
            }

        return Latex.macro(
            TemplateMacro.ABSETZUNGSKOPF,
            listOf("Abzüglich der berechneten und vereinnahmten Abschläge:".escapeLatex()),
        ) +
            Latex.raw("\n") +
            Latex.macro(TemplateMacro.ABSETZUNGSTABELLE, listOf(Latex.raw("\n") + rows + closingRow + Latex.raw("\n"))) +
            trailingNote
    }

    /**
     * Die Beschreibungsspalte eines abgesetzten Abschlags: Nummer und Daten in
     * der ersten Zeile, die Aufteilung in Netto und Steuer klein darunter –
     * dieselbe Zweizeiligkeit wie bei einer Position.
     */
    private fun describePrepayment(abgesetzt: Deduction): Latex {
        val abschlag = abgesetzt.deducted.prepayment
        // Ohne checkNotNull: Eine Deduction gibt es nur mit Datum (ADR-0008).
        val vereinnahmtAm = abgesetzt.receivedOn
        val heading =
            "Abschlagsrechnung ".escapeLatex() + abschlag.invoiceNumber.escapeLatex() + " vom ".escapeLatex() +
                GermanFormat.shortDate(abschlag.issueDate) + ", vereinnahmt am ".escapeLatex() +
                GermanFormat.shortDate(vereinnahmtAm)
        val anteile =
            abschlag.taxShares
                .map { anteil ->
                    "netto ".escapeLatex() + GermanFormat.amountWithCurrency(anteil.net) + " zzgl. ".escapeLatex() +
                        GermanFormat.percent(anteil.vatRate) + " USt. ".escapeLatex() +
                        GermanFormat.amountWithCurrency(anteil.vat)
                }.joinToLatex(" · ")
        return heading + Latex.macro(TemplateMacro.ABSETZUNGSDETAIL, listOf(anteile))
    }
}
