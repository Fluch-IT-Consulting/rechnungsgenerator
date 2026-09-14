package de.fluchitconsulting.rechnungsgenerator.prepayments

import de.fluchitconsulting.rechnungsgenerator.model.TaxShare

/**
 * Der `abschlaege`-Block einer Schlussrechnung als YAML-Text – das, was
 * `rechnungsgenerator abschlaege` ausgibt (ADR-0003).
 *
 * Von Hand zusammengesetzt statt über kaml: kaml schreibt keine Kommentare,
 * und ein fehlendes `vereinnahmt-am` käme als `null` heraus statt als leeres
 * Feld mit Hinweis. Ob der Text trotzdem das Format trifft, das der Einleser
 * erwartet, prüft der Test durch Zurücklesen – wer hier eine Schreibweise
 * ändert, merkt es dort.
 *
 * Gleiche Eingabe, zeichengleiche Ausgabe: kein Datum, keine Version, kein
 * Pfad. Nur so lässt sich ein eingefügter Block später durch erneutes
 * Erzeugen gegenprüfen.
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

    /** Ohne abschließenden Zeilenumbruch – den setzt, wer den Block ausgibt. */
    fun render(
        entries: List<CollectedPrepayment>,
    ): String = (listOf(header, "abschlaege:") + entries.flatMap(::entryLines)).joinToString("\n")

    // flatMap: Jeder Abschlag liefert mehrere Zeilen, und flatMap hängt diese
    // Listen aneinander, statt eine Liste von Listen zu bauen.
    private fun entryLines(entry: CollectedPrepayment): List<String> {
        val abschlag = entry.prepayment
        return listOf(
            "  # ${entry.name}",
            "  - rechnungsnummer: ${yamlScalar(abschlag.invoiceNumber)}",
            "    rechnungsdatum: ${abschlag.issueDate}",
            // Leer statt weggelassen: So steht die Lücke dort, wo sie zu füllen
            // ist, und ABSCHLAG_OHNE_VEREINNAHMUNG fordert sie beim Erzeugen ein.
            abschlag.receivedOn?.let { "    vereinnahmt-am: $it" }
                ?: "    vereinnahmt-am:                 # Datum der Vereinnahmung eintragen",
            "    steueranteile:",
        ) + abschlag.taxShares.flatMap(::shareLines)
    }

    // Was nur aus Buchstaben, Ziffern und . _ / - besteht und nicht mit einem
    // Zeichen beginnt, das YAML besonders liest, bleibt ohne Anführungszeichen –
    // eine gewöhnliche Nummer sieht so aus wie von Hand geschrieben. Alles
    // andere kommt in doppelte Anführungszeichen: Ohne sie begänne mit " #" ein
    // Kommentar, und aus "R26 #9" würde still "R26".
    private val plainScalar = Regex("[A-Za-z0-9][A-Za-z0-9._/-]*")

    private fun yamlScalar(text: String): String =
        if (plainScalar.matches(text)) text else "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    // Die Schreibweisen wie in MoneySerializer und PercentSerializer: Beträge mit
    // zwei Stellen, der Satz ohne nachlaufende Nullen. Die Kategorie immer
    // ausgeschrieben, auch die Vorgabe S – wer sie nicht sieht, fragt sich, ob
    // sie vergessen wurde.
    private fun shareLines(anteil: TaxShare): List<String> =
        listOf(
            "      - steuerkategorie: ${anteil.taxCategory.code}",
            "        ust-satz: ${anteil.vatRate.valueAsPercentage.stripTrailingZeros().toPlainString()}",
            "        netto: ${anteil.net.value.toPlainString()}",
            "        ust: ${anteil.vat.value.toPlainString()}",
        )
}
