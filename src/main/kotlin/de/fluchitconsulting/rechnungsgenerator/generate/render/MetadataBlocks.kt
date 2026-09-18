package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.model.Address
import de.fluchitconsulting.rechnungsgenerator.model.SellerConfig
import de.fluchitconsulting.rechnungsgenerator.rules.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.rules.InvoiceKind

/**
 * Die Blöcke um den Empfänger: Anschrift und die Angaben zur Rechnung rechts
 * daneben. Hier steht eines der `when` über die Rechnungsart (#128): die
 * Vereinnahmung eines Abschlags. Den Bezug einer Stornorechnung nennt der
 * Pflichtsatz (#141).
 */
internal object MetadataBlocks {
    fun recipient(calculatedInvoice: CalculatedInvoice): Latex {
        val invoice = calculatedInvoice.invoice
        val anschrift =
            buildList {
                add(invoice.customer.name.escapeLatex())
                addAll(addressLines(invoice.customer.address, showCountry = !calculatedInvoice.isDomestic))
            }.joinToLatex(Latex.macro(TemplateMacro.ZEILENWECHSEL))
        // BT-48. Bei Reverse Charge und innergemeinschaftlicher Lieferung verlangt
        // §14a UStG die USt-IdNr. beider Parteien auf der Rechnung – die des
        // Ausstellers steht im Fuß, die des Empfängers hier.
        //
        // Der Abstand wird angehängt, nicht in die Liste gelegt: joinToLatex
        // setzt \zeilenwechsel zwischen die Einträge, und zwei Zeilenenden
        // hintereinander – \zeilenwechsel{}\ustidtrenner{} – sind in LaTeX ein
        // Fehler ("There's no line here to end").
        val ustId =
            invoice.customer.vatId
                ?.let { Latex.macro(TemplateMacro.USTIDTRENNER) + "USt-IdNr. ".escapeLatex() + it.formatted().escapeLatex() }
                ?: Latex.EMPTY
        return anschrift + ustId
    }

    /**
     * Die Anschrift, Zeile für Zeile, mit dem Land nur bei [showCountry].
     *
     * Ob es eine Inlandsrechnung ist, entscheidet nicht diese Funktion, sondern
     * [CalculatedInvoice.isDomestic] – dort steht auch, warum "nicht Deutschland"
     * die falsche Frage ist. Hier wird nur gesetzt (#120).
     */
    private fun addressLines(address: Address, showCountry: Boolean): List<Latex> =
        buildList {
            add(address.street.escapeLatex())
            add("${address.postalCode} ${address.city}".escapeLatex())
            // Auf einer Inlandsrechnung wäre die Landzeile überflüssig.
            if (showCountry) add(address.countryCode.value.escapeLatex())
        }

    /**
     * Die Vertretung – nur, wenn die Stammdaten eine Geschäftsführung führen.
     * Überschrift und Abstand stehen in der Vorlage (`\vertretung`), ebenso,
     * wo sie steht: neben dem Empfänger oder im Fuß (#188).
     */
    fun representation(config: SellerConfig): Latex =
        config.managingDirector
            ?.let { Latex.macro(TemplateMacro.VERTRETUNG, listOf(it.escapeLatex())) }
            ?: Latex.EMPTY

    /**
     * Die Angaben zur Rechnung, die weder Nummer noch Datum sind – die haben
     * eigene Platzhalter, weil ein Design sie in den Titel setzt. Jede ist ein
     * `\kopfangabe{Beschriftung}{Wert}`; wie das aussieht, als Zeile oder als
     * Tabellenzeile, entscheidet die Vorlage (#188).
     */
    fun headerDetails(acceptedInvoice: AcceptedInvoice): Latex =
        buildList {
            val calculatedInvoice = acceptedInvoice.calculated
            val invoice = calculatedInvoice.invoice
            val kind = acceptedInvoice.kind
            // Pflichtangabe nach §14 Abs. 4 Nr. 6 UStG. Wie der Zeitraum heißt,
            // hängt an der Rechnungsart – siehe InvoiceType.servicePeriodLabel.
            // \zusammen: Die Zeile darf hinter dem Doppelpunkt umbrechen, der
            // Zeitraum selbst nicht – warum, steht in der Vorlage.
            val zeitraum = Latex.macro(TemplateMacro.ZUSAMMEN, listOf(servicePeriod(invoice.servicePeriod, invoice.type)))
            add(headerDetail("${invoice.type.servicePeriodLabel}:", zeitraum))
            // BT-7: Steht der Zeitpunkt der Vereinnahmung fest, gehört er zu
            // den Pflichtangaben – deshalb hier und nicht in den Fließtext.
            //
            // `when` mit Subjekt macht `kind` in jedem Zweig zu dessen Variante
            // ("smart cast"). Ohne `else`: Kommt eine Variante dazu, übersetzt
            // diese Stelle erst, wenn entschieden ist, ob sie eine Vereinnahmung
            // nennt (#128).
            when (kind) {
                is InvoiceKind.PrepaymentInvoice -> {
                    kind.receivedOn?.let { add(headerDetail("Abschlag vereinnahmt am", GermanFormat.date(it))) }
                }
                is InvoiceKind.Ordinary, is InvoiceKind.FinalInvoice, is InvoiceKind.Cancellation -> {}
            }
            invoice.customer.buyerReference?.let { add(headerDetail("Ihre Referenz:", it.escapeLatex())) }
            // "ohne Abzug" stand bisher im Zahlungssatz unter den Summen. Der Satz
            // ist entfallen, weil er das Fälligkeitsdatum ein zweites Mal nannte –
            // der Hinweis, dass kein Skonto gewährt wird, gehört aber erhalten.
            //
            // Ob es ein Zahlungsziel gibt, entscheidet der Calculator; hier wird
            // nur gesetzt, was dasteht.
            calculatedInvoice.dueDate?.let { add(headerDetail("Zahlbar ohne Abzug bis", GermanFormat.date(it))) }
        }.joinToLatex(Latex.macro(TemplateMacro.ZEILENWECHSEL))

    private fun headerDetail(label: String, value: Latex): Latex =
        Latex.macro(TemplateMacro.KOPFANGABE, listOf(label.escapeLatex(), value))
}
