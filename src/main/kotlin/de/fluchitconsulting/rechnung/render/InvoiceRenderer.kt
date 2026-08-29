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
                "KOPF_PERSON" to letterheadPerson(config),
                "ABSENDER_KONTAKT" to senderContact(config),
                "RUECKSENDEZEILE" to returnAddressLine(config),
                "EMPFAENGER" to recipient(invoice),
                "VERTRETUNG" to representation(config),
                "METADATEN" to metadata(invoice, totals),
                "RECHNUNGSNUMMER" to invoice.number.escapeLatex(),
                "RECHNUNGSDATUM" to GermanFormat.date(invoice.issueDate),
                "POSITIONEN" to lineItems(totals),
                "SUMMEN" to totalsBlock(totals),
                "ZAHLUNGSHINWEIS" to paymentNote(totals),
                "SCHLUSSSATZ" to (invoice.note ?: config.closingLine).escapeLatex(),
                "BANKVERBINDUNG" to bankDetails(config),
                "FUSSZEILE_OBEN" to footerCompanyLine(config),
                "FUSSZEILE_UNTEN" to footerTaxAndBankLine(config),
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

    /**
     * Der Kontaktblock im Briefkopf. Straße und Ort stehen zusammen in einer
     * Zeile, damit der Block nicht fünf unterschiedlich lange Zeilen bekommt.
     *
     * Die E-Mail bleibt unbeschriftet – das @ sagt bereits, was sie ist, und
     * ein Präfix macht die ohnehin längste Zeile des Blocks noch länger.
     * Die Webseite bekommt dagegen ein "Web": Ohne das stehen zwei Zeilen mit
     * derselben Domain untereinander und lesen sich wie eine Wiederholung.
     */
    private fun senderContact(config: SellerConfig): String =
        buildList {
            add("${config.address.street} · ${config.address.postalCode} ${config.address.city}".escapeLatex())
            config.phone?.let { add("Tel. ${it.escapeLatex()}") }
            add(config.email.escapeLatex())
            config.website?.let { add("Web ${it.removePrefix("https://").removePrefix("http://").escapeLatex()}") }
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

    /**
     * Die Angaben, die weder in den Balken noch in die Tabelle passen. Bewusst
     * als schlichte "Label: Wert"-Zeilen statt als beschriftete Spalte – der
     * frühere Block mit gesperrten Versalien hat oben rechts zu viel Unruhe
     * erzeugt. Das Rechnungsdatum steht jetzt im Titelbalken.
     */
    private fun metadata(invoice: Invoice, totals: InvoiceTotals): String =
        buildList {
            // Pflichtangabe nach §14 Abs. 4 Nr. 6 UStG.
            add("Leistungszeitraum: ${GermanFormat.servicePeriod(invoice.servicePeriod.start, invoice.servicePeriod.end)}")
            invoice.customer.buyerReference?.let { add("Ihre Referenz: ${it.escapeLatex()}") }
            add("Zahlbar bis ${GermanFormat.date(totals.dueDate)}")
        }.joinToString(BR)

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
            "Kontoinhaber: ${config.bank.accountHolder.escapeLatex()}",
            "Institut: ${config.bank.bankName.escapeLatex()}",
            "IBAN: ${config.bank.iban.escapeLatex()}",
            "BIC: ${config.bank.bic.escapeLatex()}",
        ).joinToString(BR)

    private fun taxDetails(config: SellerConfig): String =
        buildList {
            config.taxNumber?.let { add("Steuernummer ${it.escapeLatex()}") }
            add("USt-IdNr. ${config.vatId.escapeLatex()}")
        }.joinToString(BR)

    /**
     * Die Person unter dem Firmennamen im Briefkopf – aber nur dann, wenn sie
     * nicht ohnehin schon als Geschäftsführung neben dem Empfänger steht.
     * Bei einer UG sind Inhaber und Geschäftsführung meist dieselbe Person;
     * beides zu setzen nennt sie zweimal im selben Seitenviertel.
     *
     * Für Einzelunternehmen ohne Geschäftsführung bleibt der Name hier stehen:
     * §14 Abs. 4 Nr. 1 UStG verlangt den vollständigen Namen des leistenden
     * Unternehmers, und der ist dort die Person, nicht die Firmierung.
     */
    private fun letterheadPerson(config: SellerConfig): String =
        if (config.managingDirector != null) {
            ""
        } else {
            "$BR[1mm]\n  {\\small\\color{gedaempft}${config.ownerName.escapeLatex()}}"
        }

    /**
     * Die Vertretungsangabe neben dem Empfängerfeld. "Geschäftsführung" statt
     * "Geschäftsführer": Die Bezeichnung steht neben einem Namen, dessen
     * Geschlecht die Konfiguration nicht kennt.
     */
    private fun representation(config: SellerConfig): String =
        config.managingDirector?.let { "Geschäftsführung$BR${it.escapeLatex()}" } ?: ""

    /** Erste Fußzeile: Firmierung, Sitz und die Angaben nach §35a GmbHG. */
    private fun footerCompanyLine(config: SellerConfig): String =
        buildList {
            add(config.companyName.escapeLatex())
            add("${config.address.street}, ${config.address.postalCode} ${config.address.city}".escapeLatex())
            // Die Geschäftsführung steht oben neben dem Empfänger, nicht hier:
            // Sonst bricht diese Zeile um, und §35a GmbHG verlangt nur, dass die
            // Angabe auf dem Geschäftsbrief steht – nicht, dass sie im Fuß steht.
            config.registerCourt?.let { court ->
                val number = config.registerNumber?.let { ", ${it.escapeLatex()}" } ?: ""
                add("Registergericht ${court.escapeLatex()}$number")
            }
        }.joinToString(" · ")

    /** Zweite Fußzeile: steuerliche Angaben und Bankverbindung, wie bisher gewohnt. */
    private fun footerTaxAndBankLine(config: SellerConfig): String =
        buildList {
            config.taxNumber?.let { add("Steuernummer ${it.escapeLatex()}") }
            add("USt-IdNr. ${config.vatId.escapeLatex()}")
            add(config.bank.bankName.escapeLatex())
            add("BIC ${config.bank.bic.escapeLatex()}")
            add("IBAN ${config.bank.iban.escapeLatex()}")
        }.joinToString(" · ")
}
