package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.model.Address
import de.fluchitconsulting.rechnungsgenerator.rules.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.rules.InvoiceKind
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType

/**
 * Die Angaben um den Empfänger: seine Anschrift und die Kopfangaben der
 * Rechnung, jede einzeln (#188). Reihenfolge und Beschriftung bestimmt die
 * Vorlage. Hier steht eines der `when` über die Rechnungsart (#128): die
 * Vereinnahmung eines Abschlags. Den Bezug einer Stornorechnung nennt der
 * Pflichtsatz (#141).
 */
internal object MetadataBlocks {
    /**
     * Name und Anschrift des Kunden als ein Block: Die Landzeile hängt an
     * [CalculatedInvoice.isDomestic], und die Reihenfolge einer Anschrift ist
     * Postkonvention, kein Satzbild.
     *
     * Aus demselben Grund steht der Ansprechpartner (BT-56) hier und nicht als
     * eigener Platzhalter (#196): Er gehört unter den Namen und vor die Straße,
     * in jedem Design.
     */
    fun recipient(calculatedInvoice: CalculatedInvoice): Latex {
        val customer = calculatedInvoice.invoice.customer
        return buildList {
            add(customer.name.escapeLatex())
            customer.contactPoint?.let { add(it.escapeLatex()) }
            addAll(addressLines(customer.address, showCountry = !calculatedInvoice.isDomestic))
        }.joinToLatex(Latex.macro(TemplateMacro.ZEILENWECHSEL))
    }

    /**
     * Die USt-IdNr. des Kunden (BT-48), leer, wenn er keine hat. Bei Reverse
     * Charge und innergemeinschaftlicher Lieferung verlangt §14a UStG sie auf
     * der Rechnung; wo sie steht, entscheidet die Vorlage.
     */
    fun customerVatId(calculatedInvoice: CalculatedInvoice): Latex =
        calculatedInvoice.invoice.customer.vatId
            ?.formatted()
            ?.escapeLatex() ?: Latex.EMPTY

    /**
     * Die Anschrift, Zeile für Zeile, mit dem Land nur bei [showCountry].
     *
     * Ob es eine Inlandsrechnung ist, entscheidet nicht diese Funktion, sondern
     * [CalculatedInvoice.isDomestic] – dort steht auch, warum "nicht Deutschland"
     * die falsche Frage ist. Hier wird nur gesetzt (#120).
     */
    private fun addressLines(
        address: Address,
        showCountry: Boolean,
    ): List<Latex> =
        buildList {
            add(address.street.escapeLatex())
            add("${address.postalCode} ${address.city}".escapeLatex())
            // Auf einer Inlandsrechnung wäre die Landzeile überflüssig.
            if (showCountry) add(address.countryCode.value.escapeLatex())
        }

    /**
     * Wie der Leistungszeitraum heißt – die eine Beschriftung, die vom
     * Renderer kommt (#188). Sie hängt an der Rechnungsart
     * ([InvoiceType.servicePeriodLabel]): Auf einer Abschlagsrechnung ist der
     * Zeitraum eine Prognose, und eine Vorlage, die dort fest
     * "Leistungszeitraum" schriebe, wäre falsch. Den Doppelpunkt setzt die
     * Vorlage.
     */
    fun servicePeriodLabel(calculatedInvoice: CalculatedInvoice): Latex =
        calculatedInvoice.invoice.type.servicePeriodLabel
            .escapeLatex()

    /** Pflichtangabe nach §14 Abs. 4 Nr. 6 UStG, geschrieben wie die Art es verlangt. */
    fun servicePeriodValue(calculatedInvoice: CalculatedInvoice): Latex {
        val invoice = calculatedInvoice.invoice
        return servicePeriod(invoice.servicePeriod, invoice.type)
    }

    /**
     * BT-7: Steht der Zeitpunkt der Vereinnahmung fest, gehört er zu den
     * Pflichtangaben. Leer auf jeder anderen Art und solange er nicht
     * feststeht.
     *
     * `when` mit Subjekt macht `kind` in jedem Zweig zu dessen Variante
     * ("smart cast"). Ohne `else`: Kommt eine Variante dazu, übersetzt diese
     * Stelle erst, wenn entschieden ist, ob sie eine Vereinnahmung nennt
     * (#128).
     */
    fun receivedOn(acceptedInvoice: AcceptedInvoice): Latex =
        when (val kind = acceptedInvoice.kind) {
            is InvoiceKind.PrepaymentInvoice -> kind.receivedOn?.let { GermanFormat.date(it) } ?: Latex.EMPTY
            is InvoiceKind.Ordinary, is InvoiceKind.FinalInvoice, is InvoiceKind.Cancellation -> Latex.EMPTY
        }

    /** BT-10, die Referenz des Kunden, leer, wenn er keine nennt. */
    fun buyerReference(calculatedInvoice: CalculatedInvoice): Latex =
        calculatedInvoice.invoice.customer.buyerReference
            ?.escapeLatex() ?: Latex.EMPTY

    /**
     * Das Zahlungsziel. Ob es eines gibt, entscheidet der Calculator
     * ([CalculatedInvoice.dueDate]); hier wird nur gesetzt, was dasteht. Ohne
     * Zahlungsziel ist der Wert leer, und die Vorlage lässt die Zeile samt
     * Beschriftung weg.
     */
    fun dueDate(calculatedInvoice: CalculatedInvoice): Latex =
        calculatedInvoice.dueDate?.let { GermanFormat.date(it) } ?: Latex.EMPTY
}
