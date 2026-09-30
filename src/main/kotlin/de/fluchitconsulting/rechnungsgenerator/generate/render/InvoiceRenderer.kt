package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.accepted.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Logo
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import de.fluchitconsulting.rechnungsgenerator.generate.typesetting.PdfSources

/** Setzt die Vorlage mit den Werten einer angenommenen Rechnung zusammen. Gerechnet wird hier nichts (ADR-0017). */
object InvoiceRenderer {
    // Ohne Endung: `\input` in der Vorlage findet die Datei auch so.
    private const val LOGO_NAME = "logo"

    fun render(acceptedInvoice: AcceptedInvoice): RenderedInvoice {
        val calculated = acceptedInvoice.calculated
        val template = Template.of(calculated.config.design, calculated.invoice.language)
        val logo = includedLogo(template, acceptedInvoice)
        // includesLogo folgt aus dem Logo, nicht aus files: Dort liegt immer auch die Grundlage (#203).
        return RenderedInvoice(
            sources =
                PdfSources(
                    tex = template.fillIn(values(acceptedInvoice)),
                    xmpMetadata = renderMetadata(acceptedInvoice),
                    // Das Rechnungsdatum, nicht die Uhr: Eine neu erzeugte Rechnung soll nicht jünger aussehen als die verschickte.
                    creationDate = calculated.invoice.issueDate,
                    files = template.files + logo?.let { mapOf("$LOGO_NAME.${it.extension}" to it.content) }.orEmpty(),
                ),
            includesLogo = logo != null,
        )
    }

    /**
     * Das Logo nur, wenn die Vorlage `<<LOGO>>` setzt: Sonst ginge es in die Trailer-ID ein, und dasselbe Blatt
     * ergäbe mit und ohne Logo-Eintrag verschiedene PDFs.
     */
    private fun includedLogo(
        template: Template,
        acceptedInvoice: AcceptedInvoice,
    ): Logo? =
        acceptedInvoice.calculated.config.logo
            ?.takeIf { template.uses(TemplateValue.LOGO) }

    internal fun values(acceptedInvoice: AcceptedInvoice): Map<TemplateValue, Latex> =
        TemplateValue.entries.associateWith { value(it, acceptedInvoice) }

    private fun value(
        key: TemplateValue,
        acceptedInvoice: AcceptedInvoice,
    ): Latex {
        val calculatedInvoice = acceptedInvoice.calculated
        val invoice = calculatedInvoice.invoice
        val config = calculatedInvoice.config
        val customer = invoice.customer
        val address = config.address
        val bank = config.bank
        val taxNumber = config.taxNumber
        return when (key) {
            TemplateValue.FIRMA -> {
                config.companyName.escapeLatex()
            }
            TemplateValue.INHABER -> {
                config.ownerName.escapeLatex()
            }
            TemplateValue.STRASSE -> {
                address.street.escapeLatex()
            }
            TemplateValue.PLZ -> {
                address.postalCode.escapeLatex()
            }
            TemplateValue.ORT -> {
                address.city.escapeLatex()
            }
            TemplateValue.EMAIL -> {
                config.email.escapeLatex()
            }
            TemplateValue.TELEFON -> {
                config.phone.orEmpty().escapeLatex()
            }
            TemplateValue.WEBSEITE -> {
                config.website.orEmpty().escapeLatex()
            }
            TemplateValue.USTID -> {
                config.vatId.formatted().escapeLatex()
            }
            TemplateValue.STEUERNUMMER -> {
                taxNumber?.value.orEmpty().escapeLatex()
            }
            TemplateValue.REGISTERGERICHT -> {
                config.registerCourt.orEmpty().escapeLatex()
            }
            TemplateValue.REGISTERNUMMER -> {
                config.registerNumber.orEmpty().escapeLatex()
            }
            TemplateValue.GESCHAEFTSFUEHRUNG -> {
                config.managingDirector.orEmpty().escapeLatex()
            }
            TemplateValue.KONTOINHABER -> {
                bank.accountHolder.escapeLatex()
            }
            TemplateValue.INSTITUT -> {
                bank.bankName.escapeLatex()
            }
            TemplateValue.IBAN -> {
                bank.iban.formatted().escapeLatex()
            }
            TemplateValue.BIC -> {
                bank.bic.value.escapeLatex()
            }
            TemplateValue.LOGO -> {
                if (config.logo != null) LOGO_NAME.escapeLatex() else Latex.EMPTY
            }
            TemplateValue.AKZENTFARBE -> {
                config.accentColor
                    ?.value
                    .orEmpty()
                    .escapeLatex()
            }
            TemplateValue.DOKUMENTART -> {
                invoice.type.documentName(invoice.language).escapeLatex()
            }
            TemplateValue.RECHNUNGSNUMMER -> {
                invoice.number.escapeLatex()
            }
            TemplateValue.RECHNUNGSDATUM -> {
                DateFormat(invoice.language).date(invoice.issueDate)
            }
            TemplateValue.WAEHRUNG -> {
                invoice.currency.name.escapeLatex()
            }
            TemplateValue.EMPFAENGER -> {
                MetadataBlocks.recipient(calculatedInvoice)
            }
            // BT-48
            TemplateValue.KUNDE_USTID -> {
                customer.vatId?.formatted()?.escapeLatex() ?: Latex.EMPTY
            }
            // BT-46 und BT-14
            TemplateValue.KUNDENNUMMER -> {
                customer.buyerIdentifier?.escapeLatex() ?: Latex.EMPTY
            }
            TemplateValue.AUFTRAGSNUMMER -> {
                invoice.salesOrderReference?.escapeLatex() ?: Latex.EMPTY
            }
            TemplateValue.ZEITRAUM_BESCHRIFTUNG -> {
                invoice.type.servicePeriodLabel(invoice.language).escapeLatex()
            }
            // §14 Abs. 4 Nr. 6 UStG
            TemplateValue.LEISTUNGSZEITRAUM -> {
                servicePeriod(invoice.servicePeriod, invoice)
            }
            TemplateValue.VEREINNAHMT_AM -> {
                MetadataBlocks.receivedOn(acceptedInvoice)
            }
            // BT-10
            TemplateValue.KUNDENREFERENZ -> {
                customer.buyerReference?.escapeLatex() ?: Latex.EMPTY
            }
            TemplateValue.ZAHLUNGSZIEL -> {
                calculatedInvoice.dueDate?.let { DateFormat(invoice.language).date(it) } ?: Latex.EMPTY
            }
            TemplateValue.EINLEITUNG -> {
                invoice.introduction.orEmpty().escapeLatex()
            }
            TemplateValue.RABATTSPALTE -> {
                ItemBlocks.discountColumn(calculatedInvoice)
            }
            TemplateValue.POSITIONEN -> {
                ItemBlocks.lineItems(calculatedInvoice)
            }
            TemplateValue.SUMMEN -> {
                ItemBlocks.totalsBlock(calculatedInvoice)
            }
            TemplateValue.ARTHINWEIS -> {
                ItemBlocks.typeNote(acceptedInvoice)
            }
            TemplateValue.STEUERHINWEISE -> {
                ItemBlocks.taxNotes(calculatedInvoice)
            }
            TemplateValue.UMRECHNUNG -> {
                ItemBlocks.conversionNote(calculatedInvoice)
            }
            TemplateValue.ABSETZUNG -> {
                DeductionBlocks.deductionBlock(acceptedInvoice)
            }
            TemplateValue.ZAHLUNGSFRIST -> {
                ItemBlocks.paymentTermNote(calculatedInvoice)
            }
            TemplateValue.SKONTO -> {
                ItemBlocks.skontoNote(calculatedInvoice)
            }
            TemplateValue.BANKVERBINDUNG -> {
                SellerBlocks.bankDetails(calculatedInvoice)
            }
            TemplateValue.SCHLUSSSATZ -> {
                (invoice.note ?: config.closingLine(invoice.language)).escapeLatex()
            }
        }
    }

    /**
     * Die .xmpdata-Datei, aus der pdfx die XMP-Metadaten baut. Bewusst ohne `\Keywords`: pdfx schreibt sie ins
     * Info-Dictionary, aber nicht ins XMP, und PDF/A-1b verlangt Übereinstimmung (6.7.3) – nachgemessen ein Verstoß.
     */
    private fun renderMetadata(acceptedInvoice: AcceptedInvoice): String {
        val calculatedInvoice = acceptedInvoice.calculated
        val invoice = calculatedInvoice.invoice
        val language = invoice.language
        val document = "${invoice.type.documentName(language)} ${invoice.number}".escapeXmpData()
        val dated =
            when (language) {
                Language.GERMAN -> " vom "
                Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> " dated "
            }
        return listOf(
            xmpField("Title", document),
            xmpField("Author", calculatedInvoice.config.companyName.escapeXmpData()),
            xmpField("Subject", document + dated.escapeXmpData() + DateFormat(language).dateInMetadata(invoice.issueDate)),
            xmpField("Language", language.tag.escapeXmpData()),
        ).joinToLatex("\n").tex
    }

    /** `\Title{…}`. Kein [TemplateMacro]: Diese Felder definiert pdfx, die Prüfung fände sie in der Vorlage nicht. */
    private fun xmpField(name: String, content: Latex): Latex = Latex.raw("\\$name{") + content + Latex.raw("}")
}
