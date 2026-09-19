package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.PdfSources
import de.fluchitconsulting.rechnungsgenerator.rules.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.values.Logo

/**
 * Setzt die LaTeX-Vorlage mit den Werten einer Rechnung zusammen.
 *
 * Der Renderer rechnet nichts – er bekommt die fertige
 * [de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice] und
 * formatiert nur. Umgekehrt kennt der Calculator kein LaTeX. Diese Trennung ist
 * der Grund, warum die Steuerlogik ohne einen einzigen LaTeX-Lauf testbar ist.
 *
 * Und er bekommt sie nur angenommen, als [AcceptedInvoice] (ADR-0007): Eine
 * Rechnung mit Abbruch lässt sich nicht setzen, statt bloß nicht gesetzt zu
 * werden, weil beide Kommandos daran denken.
 *
 * Innen wird durchgehend mit [Latex] gearbeitet, nicht mit Zeichenketten: Was
 * hier zusammengesetzt wird, hat die Maskierung schon hinter sich. Ausgepackt
 * wird genau einmal, beim Einsetzen in die Vorlage.
 *
 * Die Blöcke stehen je Gruppe in einer eigenen Datei – [SellerBlocks],
 * [MetadataBlocks], [ItemBlocks], [DeductionBlocks], die geteilten Bausteine
 * in `RenderFragments.kt`, die Vorlage mit ihren Prüfungen in [Template].
 * Hier bleiben der Einstieg und die Werte der Platzhalter. Der Schnitt folgt
 * den Blöcken und nicht den Rechnungsarten (Notiz vom 14.09.2026): Designs
 * vervielfachen sich, die Arten bleiben die geschlossene Menge, die jeder
 * Renderer vollständig beantwortet.
 */
object InvoiceRenderer {
    // Der Name, unter dem das Logo neben der .tex liegt, ohne Endung: `\input`
    // und später `\includegraphics` finden die Datei auch so.
    private const val LOGO_NAME = "logo"

    fun render(acceptedInvoice: AcceptedInvoice): RenderedInvoice {
        val template = Template.of(acceptedInvoice.calculated.config.design)
        val logo = includedLogo(template, acceptedInvoice)
        // Dateien und Auskunft folgen beide aus dem eingebundenen Logo, nicht
        // das eine aus dem anderen: Kommt eine zweite Datei dazu, stimmt die
        // Auskunft weiter (#203).
        return RenderedInvoice(
            sources =
                PdfSources(
                    tex = template.fillIn(values(acceptedInvoice)),
                    xmpMetadata = renderMetadata(acceptedInvoice),
                    // Das Rechnungsdatum und nicht die Uhr: Eine neu erzeugte
                    // Rechnung soll nicht jünger aussehen als die verschickte.
                    creationDate = acceptedInvoice.calculated.invoice.issueDate,
                    files = logo?.let { mapOf("$LOGO_NAME.${it.extension}" to it.content) }.orEmpty(),
                ),
            includesLogo = logo != null,
        )
    }

    /**
     * Das Logo, wenn die Stammdaten eines nennen **und** die Vorlage es
     * einbindet (#198), sonst `null`.
     *
     * Ob sie es einbindet, zeigt allein ihr Platzhalter – der Renderer kennt
     * kein Design, er befolgt die Vorlage. Eine, die das Logo übergeht, bekommt
     * es gar nicht erst: Es ginge sonst in die Trailer-ID ein, und dieselbe
     * Rechnung ergäbe mit und ohne Logo-Eintrag verschiedene PDFs, obwohl auf
     * dem Blatt dasselbe steht.
     */
    private fun includedLogo(
        template: Template,
        acceptedInvoice: AcceptedInvoice,
    ): Logo? =
        acceptedInvoice.calculated.config.logo
            ?.takeIf { template.uses(TemplateValue.LOGO) }

    /**
     * Die Werte aller Platzhalter – die innere Naht zwischen Inhalt und
     * [Template]. Eigens abrufbar, damit ein Test fragen kann, was der Renderer
     * entschieden hat – ob eine Angabe leer ist –, ohne das aus dem gesetzten
     * Text einer bestimmten Vorlage herauszulesen.
     */
    internal fun values(acceptedInvoice: AcceptedInvoice): Map<TemplateValue, Latex> =
        TemplateValue.entries.associateWith { value(it, acceptedInvoice) }

    /**
     * Der Wert eines Platzhalters. Ein `when` über alle [TemplateValue] ohne
     * `else`: Kommt ein Platzhalter dazu, übersetzt diese Stelle erst, wenn
     * entschieden ist, was er enthält.
     *
     * Eine Angabe, die nicht dasteht, ist leer ([Latex.EMPTY]) und nicht
     * abwesend. Der Vorlage ist es gleich, warum: weil die Eingabe sie nicht
     * nennt oder weil ein `when` hier so entschieden hat.
     */
    private fun value(
        key: TemplateValue,
        acceptedInvoice: AcceptedInvoice,
    ): Latex {
        val calculatedInvoice = acceptedInvoice.calculated
        val invoice = calculatedInvoice.invoice
        val config = calculatedInvoice.config
        val address = config.address
        val bank = config.bank
        val taxNumber = config.taxNumber
        // `orEmpty()` macht aus null eine leere Zeichenkette: Eine fehlende
        // Angabe der Stammdaten wird ein leerer Wert.
        return when (key) {
            TemplateValue.FIRMA -> config.companyName.escapeLatex()
            TemplateValue.INHABER -> config.ownerName.escapeLatex()
            TemplateValue.STRASSE -> address.street.escapeLatex()
            TemplateValue.PLZ -> address.postalCode.escapeLatex()
            TemplateValue.ORT -> address.city.escapeLatex()
            TemplateValue.EMAIL -> config.email.escapeLatex()
            TemplateValue.TELEFON -> config.phone.orEmpty().escapeLatex()
            TemplateValue.WEBSEITE -> config.website.orEmpty().escapeLatex()
            TemplateValue.USTID -> config.vatId.formatted().escapeLatex()
            TemplateValue.STEUERNUMMER -> taxNumber?.value.orEmpty().escapeLatex()
            TemplateValue.REGISTERGERICHT -> config.registerCourt.orEmpty().escapeLatex()
            TemplateValue.REGISTERNUMMER -> config.registerNumber.orEmpty().escapeLatex()
            TemplateValue.GESCHAEFTSFUEHRUNG -> config.managingDirector.orEmpty().escapeLatex()
            TemplateValue.KONTOINHABER -> bank.accountHolder.escapeLatex()
            TemplateValue.INSTITUT -> bank.bankName.escapeLatex()
            TemplateValue.IBAN -> bank.iban.formatted().escapeLatex()
            TemplateValue.BIC -> bank.bic.value.escapeLatex()
            TemplateValue.LOGO -> if (config.logo != null) LOGO_NAME.escapeLatex() else Latex.EMPTY
            TemplateValue.DOKUMENTART -> invoice.type.documentName.escapeLatex()
            TemplateValue.RECHNUNGSNUMMER -> invoice.number.escapeLatex()
            TemplateValue.RECHNUNGSDATUM -> GermanFormat.date(invoice.issueDate)
            TemplateValue.EMPFAENGER -> MetadataBlocks.recipient(calculatedInvoice)
            TemplateValue.KUNDE_USTID -> MetadataBlocks.customerVatId(calculatedInvoice)
            TemplateValue.KUNDENNUMMER -> MetadataBlocks.buyerIdentifier(calculatedInvoice)
            TemplateValue.AUFTRAGSNUMMER -> MetadataBlocks.salesOrderReference(calculatedInvoice)
            TemplateValue.ZEITRAUM_BESCHRIFTUNG -> MetadataBlocks.servicePeriodLabel(calculatedInvoice)
            TemplateValue.LEISTUNGSZEITRAUM -> MetadataBlocks.servicePeriodValue(calculatedInvoice)
            TemplateValue.VEREINNAHMT_AM -> MetadataBlocks.receivedOn(acceptedInvoice)
            TemplateValue.KUNDENREFERENZ -> MetadataBlocks.buyerReference(calculatedInvoice)
            TemplateValue.ZAHLUNGSZIEL -> MetadataBlocks.dueDate(calculatedInvoice)
            TemplateValue.EINLEITUNG -> invoice.introduction.orEmpty().escapeLatex()
            TemplateValue.RABATTSPALTE -> ItemBlocks.discountColumn(calculatedInvoice)
            TemplateValue.POSITIONEN -> ItemBlocks.lineItems(calculatedInvoice)
            TemplateValue.SUMMEN -> ItemBlocks.totalsBlock(calculatedInvoice)
            TemplateValue.ARTHINWEIS -> ItemBlocks.typeNote(acceptedInvoice)
            TemplateValue.STEUERHINWEISE -> ItemBlocks.taxNotes(calculatedInvoice)
            TemplateValue.ABSETZUNG -> DeductionBlocks.deductionBlock(acceptedInvoice)
            TemplateValue.ZAHLUNGSFRIST -> ItemBlocks.paymentTermNote(calculatedInvoice)
            TemplateValue.SKONTO -> ItemBlocks.skontoNote(calculatedInvoice)
            TemplateValue.BANKVERBINDUNG -> SellerBlocks.bankDetails(calculatedInvoice)
            TemplateValue.SCHLUSSSATZ -> (invoice.note ?: config.closingLine).escapeLatex()
        }
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
     *
     * Auch hier maskierte Werte: Ein `&` im Firmennamen zerlegte sonst die
     * Metadaten, und ohne gültige Metadaten gibt es kein PDF/A.
     */
    private fun renderMetadata(acceptedInvoice: AcceptedInvoice): String {
        val calculatedInvoice = acceptedInvoice.calculated
        val invoice = calculatedInvoice.invoice
        val document = invoice.type.documentName.escapeLatex() + " ".escapeLatex() + invoice.number.escapeLatex()
        return listOf(
            xmpField("Title", document),
            xmpField("Author", calculatedInvoice.config.companyName.escapeLatex()),
            xmpField("Subject", document + " vom ".escapeLatex() + GermanFormat.dateInMetadata(invoice.issueDate)),
            xmpField("Language", "de-DE".escapeLatex()),
        ).joinToLatex("\n").tex
    }

    /**
     * Ein Feld der .xmpdata-Datei: `\Title{…}`. Kein [TemplateMacro]: Diese
     * Felder definiert pdfx und nicht die Vorlage, die Prüfung fände sie dort
     * nicht. Deshalb privat und nur hier – bis #76 war das `Latex.macro` mit
     * einem Namen als Zeichenkette, und darüber lief auch `\mbox`.
     */
    private fun xmpField(name: String, content: Latex): Latex = Latex.raw("\\$name{") + content + Latex.raw("}")
}
