package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.calculation.InvoiceKind
import de.fluchitconsulting.rechnungsgenerator.model.Address
import de.fluchitconsulting.rechnungsgenerator.rules.AcceptedInvoice

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
     * Der Block rechts neben dem Empfängerfeld: Vertretung und die Angaben zur
     * Rechnung. Beides stand vorher an zwei verschiedenen Stellen – die
     * Rechnungsangaben unter dem Balken, die Vertretung hier. Zusammengefasst
     * bleibt der Bereich unter dem Balken frei für die Positionen.
     *
     * "Geschäftsführung" statt "Geschäftsführer": Die Bezeichnung steht neben
     * einem Namen, dessen Geschlecht die Konfiguration nicht kennt.
     */
    fun recipientSideBlock(acceptedInvoice: AcceptedInvoice): Latex =
        buildList {
            val config = acceptedInvoice.calculated.config
            config.managingDirector?.let {
                add("Geschäftsführung".escapeLatex() + Latex.macro(TemplateMacro.ZEILENWECHSEL) + it.escapeLatex())
            }
            add(metadata(acceptedInvoice))
        }.joinToLatex(Latex.macro(TemplateMacro.BLOCKTRENNER))

    /**
     * Die Angaben, die weder in den Balken noch in die Tabelle passen. Bewusst
     * als schlichte "Label: Wert"-Zeilen statt als beschriftete Spalte – der
     * frühere Block mit gesperrten Versalien hat oben rechts zu viel Unruhe
     * erzeugt. Das Rechnungsdatum steht jetzt im Titelbalken.
     */
    private fun metadata(acceptedInvoice: AcceptedInvoice): Latex =
        buildList {
            val calculatedInvoice = acceptedInvoice.calculated
            val invoice = calculatedInvoice.invoice
            val kind = acceptedInvoice.kind
            // Pflichtangabe nach §14 Abs. 4 Nr. 6 UStG. Wie der Zeitraum heißt,
            // hängt an der Rechnungsart – siehe InvoiceType.servicePeriodLabel.
            // \zusammen: Die Zeile darf hinter dem Doppelpunkt umbrechen, der
            // Zeitraum selbst nicht – warum, steht in der Vorlage.
            val zeitraum = Latex.macro(TemplateMacro.ZUSAMMEN, listOf(servicePeriod(invoice.servicePeriod, invoice.type)))
            add("${invoice.type.servicePeriodLabel}: ".escapeLatex() + zeitraum)
            // BT-7: Steht der Zeitpunkt der Vereinnahmung fest, gehört er zu
            // den Pflichtangaben – deshalb hier und nicht in den Fließtext.
            //
            // `when` mit Subjekt macht `kind` in jedem Zweig zu dessen Variante
            // ("smart cast"). Ohne `else`: Kommt eine Variante dazu, übersetzt
            // diese Stelle erst, wenn entschieden ist, ob sie eine Vereinnahmung
            // nennt (#128).
            when (kind) {
                is InvoiceKind.PrepaymentInvoice ->
                    kind.receivedOn?.let { add("Abschlag vereinnahmt am ".escapeLatex() + GermanFormat.date(it)) }
                is InvoiceKind.Ordinary, is InvoiceKind.FinalInvoice, is InvoiceKind.Cancellation -> {}
            }
            invoice.customer.buyerReference?.let { add("Ihre Referenz: ".escapeLatex() + it.escapeLatex()) }
            // "ohne Abzug" stand bisher im Zahlungssatz unter den Summen. Der Satz
            // ist entfallen, weil er das Fälligkeitsdatum ein zweites Mal nannte –
            // der Hinweis, dass kein Skonto gewährt wird, gehört aber erhalten.
            //
            // Ob es ein Zahlungsziel gibt, entscheidet der Calculator; hier wird
            // nur gesetzt, was dasteht.
            calculatedInvoice.dueDate?.let { add("Zahlbar ohne Abzug bis ".escapeLatex() + GermanFormat.date(it)) }
        }.joinToLatex(Latex.macro(TemplateMacro.ZEILENWECHSEL))
}
