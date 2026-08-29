package de.fluchitconsulting.rechnung.render

import de.fluchitconsulting.rechnung.InvoiceTotals
import de.fluchitconsulting.rechnung.model.Address
import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.model.SellerConfig

/**
 * Setzt die LaTeX-Vorlage mit den Werten einer Rechnung zusammen.
 *
 * Der Renderer rechnet nichts – er bekommt die fertigen [InvoiceTotals] und
 * formatiert nur. Umgekehrt kennt der Calculator kein LaTeX. Diese Trennung ist
 * der Grund, warum die Steuerlogik ohne einen einzigen LaTeX-Lauf testbar ist.
 */
object InvoiceRenderer {
    private const val TEMPLATE_PATH = "/templates/invoice.tex"

    // Zeilenumbruch in LaTeX. Als Konstante, weil "\\\\" im Kotlin-Quelltext
    // schwer zu lesen ist: vier Backslashes für zwei im Ergebnis.
    private const val BR = "\\\\"

    fun render(invoice: Invoice, config: SellerConfig, totals: InvoiceTotals): String {
        val template = checkNotNull(javaClass.getResource(TEMPLATE_PATH)) { "Vorlage $TEMPLATE_PATH fehlt" }.readText()

        val values =
            mapOf(
                "FIRMA" to config.companyName.escapeLatex(),
                "INHABER" to config.ownerName.escapeLatex(),
                "ABSENDER_KONTAKT" to senderContact(config),
                "RUECKSENDEZEILE" to returnAddressLine(config),
                "EMPFAENGER" to recipient(invoice),
                "METADATEN" to metadata(invoice, totals),
                "RECHNUNGSNUMMER" to invoice.number.escapeLatex(),
                "POSITIONEN" to lineItems(totals),
                "SUMMEN" to totalsBlock(totals),
                "ZAHLUNGSHINWEIS" to paymentNote(totals),
                "SCHLUSSSATZ" to (invoice.note ?: config.closingLine).escapeLatex(),
                "BANKVERBINDUNG" to bankDetails(config),
                "STEUERANGABEN" to taxDetails(config),
                "FUSSZEILE" to footer(config),
            )

        // fold statt einer Schleife mit var: Jeder Schritt ersetzt einen
        // Platzhalter und gibt den neuen Text zurück.
        return values.entries.fold(template) { text, (key, value) -> text.replace("<<$key>>", value) }
    }

    /**
     * Die begleitende .xmpdata-Datei, aus der pdfx die XMP-Metadaten des
     * PDF/A-Dokuments baut. Ohne sie bleiben Titel und Autor im PDF leer –
     * für PDF/A-1b sind gültige Metadaten Pflicht.
     *
     * Bewusst OHNE \Keywords: Die pdfx-Version in TeX Live 2019 schreibt den
     * Eintrag ins Info-Dictionary, aber nicht als pdf:Keywords ins XMP. PDF/A-1b
     * verlangt in Klausel 6.7.3, dass beide übereinstimmen – veraPDF lehnt das
     * Dokument sonst ab. Nachgemessen: mit \Keywords genau ein Verstoß, ohne keiner.
     */
    fun renderMetadata(invoice: Invoice, config: SellerConfig): String =
        """
        \Title{Rechnung ${invoice.number.escapeLatex()}}
        \Author{${config.companyName.escapeLatex()}}
        \Subject{Rechnung ${invoice.number.escapeLatex()} vom ${GermanFormat.date(invoice.issueDate)}}
        \Language{de-DE}
        """.trimIndent()

    private fun senderContact(config: SellerConfig): String =
        buildList {
            add(config.address.street.escapeLatex())
            add("${config.address.postalCode} ${config.address.city}".escapeLatex())
            config.phone?.let { add("Tel. ${it.escapeLatex()}") }
            add(config.email.escapeLatex())
            config.website?.let { add(it.removePrefix("https://").escapeLatex()) }
        }.joinToString(BR)

    /** Die kleine Zeile über dem Empfänger, wie sie im Sichtfenster erscheint. */
    private fun returnAddressLine(config: SellerConfig): String =
        listOf(config.companyName, config.address.street, "${config.address.postalCode} ${config.address.city}")
            .joinToString(" · ")
            .escapeLatex()

    private fun recipient(invoice: Invoice): String =
        buildList {
            add(invoice.customer.name.escapeLatex())
            addAll(addressLines(invoice.customer.address))
        }.joinToString(BR)

    private fun addressLines(address: Address): List<String> =
        buildList {
            add(address.street.escapeLatex())
            add("${address.postalCode} ${address.city}".escapeLatex())
            // Das Land nur nennen, wenn es nicht Deutschland ist – sonst steht
            // auf jeder Inlandsrechnung eine überflüssige Zeile.
            if (address.countryCode != "DE") add(address.countryCode.escapeLatex())
        }

    private fun metadata(invoice: Invoice, totals: InvoiceTotals): String {
        val period = invoice.servicePeriod
        val rows =
            buildList {
                add("Rechnungsdatum" to GermanFormat.date(invoice.issueDate))
                add("Leistungszeitraum" to "${GermanFormat.shortDate(period.start)}–${GermanFormat.shortDate(period.end)}")
                add("Fällig am" to GermanFormat.date(totals.dueDate))
                invoice.customer.buyerReference?.let { add("Ihre Referenz" to it.escapeLatex()) }
            }
        return rows.joinToString(BR) { (label, value) -> "\\rubrik{$label}\\\\ $value" }
    }

    // Die Zeilen werden mit \\ VERBUNDEN, nicht damit abgeschlossen: Ein
    // Umbruch hinter der letzten Zeile erzeugt in LaTeX eine leere Extrazeile.
    private fun lineItems(totals: InvoiceTotals): String =
        totals.lines.joinToString(" $BR[1.5mm]\n") { line ->
            val item = line.item
            listOf(
                item.description.escapeLatex(),
                GermanFormat.quantity(item.quantity),
                item.unit.escapeLatex(),
                GermanFormat.priceWithCurrency(item.unitPrice),
                GermanFormat.amountWithCurrency(line.net),
            ).joinToString(" & ")
        }

    private fun totalsBlock(totals: InvoiceTotals): String =
        buildList {
            add("Nettobetrag" to GermanFormat.amountWithCurrency(totals.netTotal))
            totals.vatGroups.forEach { group ->
                // escapeLatex auch hier: GermanFormat.percent liefert "19 %", und
                // ein nacktes % kommentiert in LaTeX den Rest der Zeile aus – samt
                // Spaltentrenner und Zeilenumbruch. Nicht nur Eingaben aus der YAML
                // müssen maskiert werden, sondern jeder Text mit Sonderzeichen.
                val label = "zzgl. ${GermanFormat.percent(group.rate)} USt.".escapeLatex()
                add(label to GermanFormat.amountWithCurrency(group.amount))
            }
        }.joinToString(" $BR[1mm]\n") { (label, value) -> "  $label & $value" } +
            " $BR\n  \\midrule\n" +
            "  \\textbf{Gesamtbetrag} & \\textbf{${GermanFormat.amountWithCurrency(totals.grossTotal)}}"

    private fun paymentNote(totals: InvoiceTotals): String =
        "Bitte überweisen Sie den Gesamtbetrag bis zum " +
            "\\textbf{${GermanFormat.date(totals.dueDate)}} ohne Abzug auf das unten genannte Konto."

    private fun bankDetails(config: SellerConfig): String =
        listOf(
            config.bank.accountHolder.escapeLatex(),
            config.bank.bankName.escapeLatex(),
            "IBAN ${config.bank.iban}".escapeLatex(),
            "BIC ${config.bank.bic}".escapeLatex(),
        ).joinToString(BR)

    private fun taxDetails(config: SellerConfig): String =
        buildList {
            config.taxNumber?.let { add("Steuernummer ${it.escapeLatex()}") }
            add("USt-IdNr. ${config.vatId.escapeLatex()}")
        }.joinToString(BR)

    /** Die Pflichtangaben nach §35a GmbHG bzw. §14 UStG, als eine gesetzte Zeile. */
    private fun footer(config: SellerConfig): String =
        buildList {
            add(config.companyName.escapeLatex())
            config.managingDirector?.let { add("Geschäftsführer: ${it.escapeLatex()}") }
            config.registerCourt?.let { court ->
                val number = config.registerNumber?.let { " ${it.escapeLatex()}" } ?: ""
                add("Registergericht ${court.escapeLatex()}$number")
            }
        }.joinToString(" · ")
}
