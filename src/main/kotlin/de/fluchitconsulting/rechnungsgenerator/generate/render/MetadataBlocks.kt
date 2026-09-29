package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.accepted.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Address
import de.fluchitconsulting.rechnungsgenerator.core.rules.InvoiceKind
import de.fluchitconsulting.rechnungsgenerator.core.values.CountryCode
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import java.util.Locale

/** Angaben, die mehr sind als ein einzelner Wert. Einzelne Angaben stehen direkt in [InvoiceRenderer] (#244). */
internal object MetadataBlocks {
    /** Ein Block, weil die Reihenfolge einer Anschrift Postkonvention ist; deshalb auch der Ansprechpartner (BT-56, #196). */
    fun recipient(calculatedInvoice: CalculatedInvoice): Latex {
        val customer = calculatedInvoice.invoice.customer
        return buildList {
            add(customer.name.escapeLatex())
            customer.contactPoint?.let { add(it.escapeLatex()) }
            addAll(addressLines(customer.address, showCountry = !calculatedInvoice.isDomestic, calculatedInvoice.invoice.language))
        }.joinToLatex(Latex.macro(TemplateMacro.ZEILENWECHSEL))
    }

    private fun addressLines(
        address: Address,
        showCountry: Boolean,
        language: Language,
    ): List<Latex> =
        buildList {
            add(address.street.escapeLatex())
            add("${address.postalCode} ${address.city}".escapeLatex())
            if (showCountry) add(Latex.macro(TemplateMacro.LANDZEILE, listOf(countryName(address.countryCode, language).escapeLatex())))
        }

    /**
     * Der Name aus dem JDK, in der Sprache der Rechnung – aber nur für Codes aus [Locale.getISOCountries], sonst
     * der Code selbst: Für `ZZ` stünde „Unbekannte Region“ auf dem Blatt (#374).
     */
    private fun countryName(code: CountryCode, language: Language): String =
        if (code.value in ISO_COUNTRIES) Locale.of("", code.value).getDisplayCountry(language.locale) else code.value

    private val ISO_COUNTRIES: Set<String> = Locale.getISOCountries().toSet()

    /** BT-7, nur auf der Abschlagsrechnung und erst, wenn die Vereinnahmung feststeht. */
    fun receivedOn(acceptedInvoice: AcceptedInvoice): Latex =
        when (val kind = acceptedInvoice.kind) {
            is InvoiceKind.PrepaymentInvoice -> {
                kind.receivedOn?.let { DateFormat(acceptedInvoice.calculated.invoice.language).date(it) }
                    ?: Latex.EMPTY
            }
            is InvoiceKind.Ordinary, is InvoiceKind.FinalInvoice, is InvoiceKind.Cancellation -> {
                Latex.EMPTY
            }
        }
}
