package de.fluchitconsulting.rechnungsgenerator.prepayments

import de.fluchitconsulting.rechnungsgenerator.core.model.TaxShare

/**
 * Der `abschlaege`-Block als YAML-Text (ADR-0003), von Hand gesetzt statt über kaml: kaml schreibt keine
 * Kommentare und ein fehlendes `vereinnahmt-am` als `null`. Gleiche Eingabe, zeichengleiche Ausgabe, damit sich
 * ein eingefügter Block durch erneutes Erzeugen gegenprüfen lässt.
 */
object PrepaymentBlock {
    private val header =
        """
        # Abschläge für die Schlussrechnung, vorbereitet von `rechnungsgenerator abschlaege`.
        # Neu berechnet mit dem heutigen Stand des Generators – nicht gegen die
        # verschickten PDFs geprüft.
        #
        # Abgesetzt wird nur, was vereinnahmt ist (§14 Abs. 5 Satz 2 UStG):
        #   - nicht vereinnahmt: den Abschlag hier streichen, er wird gemahnt oder storniert
        #   - nur teilweise vereinnahmt: Die Steueranteile gelten für den vollen Betrag
        #     und stimmen dann nicht. Diesen Fall deckt der Generator noch nicht ab.
        """.trimIndent()

    /**
     * Mit einer Leerzeile vorweg: Endet die Schlussrechnung ohne Zeilenumbruch, klebte `#` sonst an ihrer letzten
     * Zeile und wäre kein Kommentar (#506). Ohne abschließenden Zeilenumbruch – den setzt, wer den Block ausgibt.
     */
    fun render(
        entries: List<CollectedPrepayment>,
    ): String = (listOf("", header, "abschlaege:") + entries.flatMap(::entryLines)).joinToString("\n")

    private fun entryLines(entry: CollectedPrepayment): List<String> {
        val abschlag = entry.prepayment
        return listOf(
            "  # ${entry.name}",
            "  - rechnungsnummer: ${yamlScalar(abschlag.invoiceNumber)}",
            "    rechnungsdatum: ${abschlag.issueDate}",
            // Leer statt weggelassen: Die Lücke steht, wo sie zu füllen ist (ABSCHLAG_OHNE_VEREINNAHMUNG).
            abschlag.receivedOn?.let { "    vereinnahmt-am: $it" }
                ?: "    vereinnahmt-am:                 # Datum der Vereinnahmung eintragen",
            "    steueranteile:",
        ) + abschlag.taxShares.flatMap(::shareLines)
    }

    // Ohne Anführungszeichen nur, was YAML nicht besonders liest: Aus R26 #9 würde sonst still R26.
    private val plainScalar = Regex("[A-Za-z0-9][A-Za-z0-9._/-]*")

    private fun yamlScalar(text: String): String =
        if (plainScalar.matches(text)) text else "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    // Die Kategorie immer ausgeschrieben, auch S: Wer sie nicht sieht, fragt sich, ob sie vergessen wurde.
    private fun shareLines(anteil: TaxShare): List<String> =
        listOf(
            "      - steuerkategorie: ${anteil.taxCategory.code}",
            "        ust-satz: ${anteil.vatRate.valueAsPercentage.stripTrailingZeros().toPlainString()}",
            "        netto: ${anteil.net.value.toPlainString()}",
            "        ust: ${anteil.vat.value.toPlainString()}",
        )
}
