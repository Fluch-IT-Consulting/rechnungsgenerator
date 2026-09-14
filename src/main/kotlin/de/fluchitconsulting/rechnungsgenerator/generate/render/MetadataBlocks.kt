package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.calculation.InvoiceKind
import de.fluchitconsulting.rechnungsgenerator.model.Address
import de.fluchitconsulting.rechnungsgenerator.model.CancelledInvoice
import de.fluchitconsulting.rechnungsgenerator.rules.AcceptedInvoice

/**
 * Die Blöcke um den Empfänger: Anschrift und die Angaben zur Rechnung rechts
 * daneben. Hier stehen zwei der drei `when` über die Rechnungsart (#128) –
 * der Bezug einer Stornorechnung und die Vereinnahmung eines Abschlags.
 */
internal object MetadataBlocks {
    fun recipient(calculatedInvoice: CalculatedInvoice): Latex {
        val invoice = calculatedInvoice.invoice
        val anschrift =
            buildList {
                add(invoice.customer.name.escapeLatex())
                addAll(addressLines(invoice.customer.address, showCountry = !calculatedInvoice.isDomestic))
            }.joinToLatex(BR)
        // BT-48. Bei Reverse Charge und innergemeinschaftlicher Lieferung verlangt
        // §14a UStG die USt-IdNr. beider Parteien auf der Rechnung – die des
        // Ausstellers steht im Fuß, die des Empfängers hier.
        //
        // Der Abstand wird angehängt, nicht in die Liste gelegt: joinToLatex
        // setzt selbst ein \\ zwischen die Einträge, und \\ direkt gefolgt vom
        // \\ in \ustidtrenner ist in LaTeX ein Fehler ("There's no line here
        // to end").
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
                add("Geschäftsführung".escapeLatex() + Latex.raw(BR) + it.escapeLatex())
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
            // Der Bezug auf die Ursprungsrechnung (BG-3 mit BT-25 und BT-26) –
            // Pflicht auf einer Stornorechnung. Als eigene Angabe und nicht im
            // Pflichtsatz, weil EN 16931 ihn als Struktur führt und nicht als
            // Prosa (ADR-0006): Wer ihn später fürs XML aus einem Satz
            // herausklauben müsste, rät. Zuerst, weil er sagt, worauf sich das
            // ganze Blatt bezieht.
            //
            // `when` mit Subjekt macht `kind` in jedem Zweig zu dessen Variante
            // ("smart cast") – in der Cancellation ist der Bezug nicht optional.
            // Ohne `else`: Kommt eine Variante dazu (#64), übersetzt diese Stelle
            // erst, wenn entschieden ist, ob sie einen Bezug nennt (#128).
            when (kind) {
                is InvoiceKind.Cancellation ->
                    add("Ursprungsrechnung: ".escapeLatex() + precedingInvoiceReference(kind.cancelled))
                is InvoiceKind.Ordinary, is InvoiceKind.PrepaymentInvoice, is InvoiceKind.FinalInvoice -> {}
            }
            // Pflichtangabe nach §14 Abs. 4 Nr. 6 UStG. Wie der Zeitraum heißt,
            // hängt an der Rechnungsart – siehe InvoiceType.servicePeriodLabel.
            // \zusammen: Die Zeile darf hinter dem Doppelpunkt umbrechen, der
            // Zeitraum selbst nicht – warum, steht in der Vorlage. Beim Bezug
            // gilt dasselbe.
            val zeitraum = Latex.macro(TemplateMacro.ZUSAMMEN, listOf(servicePeriod(invoice.servicePeriod, invoice.type)))
            add("${invoice.type.servicePeriodLabel}: ".escapeLatex() + zeitraum)
            // BT-7: Steht der Zeitpunkt der Vereinnahmung fest, gehört er zu
            // den Pflichtangaben – deshalb hier und nicht in den Fließtext.
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
        }.joinToLatex(BR)

    /**
     * Der Bezug auf eine vorausgegangene Rechnung: Nummer und Datum, als ein
     * Stück (\zusammen – die Angabe soll nicht mitten im Datum umbrechen).
     *
     * Heute ruft ihn nur das Storno auf. Die Rechnungsberichtigung (#64) trägt
     * denselben Bezug – Code 384 und BG-3 stehen dort schon fest –, ruft dann
     * also dieselbe Funktion, statt die Zeile zu kopieren. Die Beschriftung
     * bleibt beim Aufrufer: Sie gehört zur Rechnungsart, nicht zum Bezug.
     */
    private fun precedingInvoiceReference(cancelled: CancelledInvoice): Latex =
        Latex.macro(
            TemplateMacro.ZUSAMMEN,
            listOf(
                cancelled.invoiceNumber.escapeLatex() + " vom ".escapeLatex() +
                    GermanFormat.date(cancelled.issueDate),
            ),
        )
}
