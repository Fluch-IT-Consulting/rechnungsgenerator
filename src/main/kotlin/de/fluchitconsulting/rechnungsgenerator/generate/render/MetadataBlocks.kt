package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.accepted.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Address
import de.fluchitconsulting.rechnungsgenerator.core.rules.InvoiceKind

/**
 * Die Angaben um den Empfänger, die mehr sind als der Pfad zu einem Wert: die
 * Anschrift als Block und die Vereinnahmung mit ihrem `when` über die
 * Rechnungsart (#128). Den Bezug einer Stornorechnung nennt der Pflichtsatz
 * (#141).
 *
 * Eine **einzelne Angabe** steht dagegen direkt im `when` von
 * [InvoiceRenderer.values] – auch mit Formatierschritt (#244). Dort stehen
 * `formatted()` und `GermanFormat.date` seit jeher bei USTID, IBAN und
 * RECHNUNGSDATUM; eine Zwischenstufe daneben, die nur weiterreicht, hieße
 * beim Lesen von `values()` einen Sprung in diese Datei, nur um zu erfahren,
 * dass hier keine Entscheidung steht.
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
     * BT-7: Steht der Zeitpunkt der Vereinnahmung fest, gehört er zu den
     * Pflichtangaben. Leer auf jeder anderen Art und solange er nicht
     * feststeht.
     *
     * Ohne `else`: Kommt eine Variante dazu, übersetzt diese Stelle erst, wenn
     * entschieden ist, ob sie eine Vereinnahmung nennt (#128).
     */
    fun receivedOn(acceptedInvoice: AcceptedInvoice): Latex =
        when (val kind = acceptedInvoice.kind) {
            is InvoiceKind.PrepaymentInvoice -> kind.receivedOn?.let { GermanFormat.date(it) } ?: Latex.EMPTY
            is InvoiceKind.Ordinary, is InvoiceKind.FinalInvoice, is InvoiceKind.Cancellation -> Latex.EMPTY
        }
}
