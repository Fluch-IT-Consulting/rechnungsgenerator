package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.model.LineItem
import de.fluchitconsulting.rechnung.model.SellerConfig
import de.fluchitconsulting.rechnung.values.Money
import de.fluchitconsulting.rechnung.values.Percent
import de.fluchitconsulting.rechnung.values.TaxCategory
import de.fluchitconsulting.rechnung.values.sumOf
import java.time.LocalDate

// Alle abgeleiteten Werte einer Rechnung. Wird aus Invoice + SellerConfig
// berechnet und ist danach unveränderlich – das Template bekommt nur noch dies.

/** Wird geworfen, wenn die Rechnung in sich nicht stimmig ist. */
class InvoiceValidationException(message: String) : Exception(message)

data class CalculatedLine(
    val item: LineItem,
    // Netto der Position: Menge × Einzelpreis. Das Runden auf Cent macht Money selbst.
    val net: Money,
)

/**
 * Eine Zeile der Steueraufschlüsselung – EN 16931 BG-23.
 *
 * Gruppiert wird nach Kategorie *und* Satz, nicht nur nach Satz: Zwei
 * Positionen mit 0 % können steuerlich völlig Verschiedenes bedeuten
 * (steuerbefreit, Reverse Charge, nicht steuerbar) und gehören dann in
 * getrennte Zeilen.
 */
data class VatGroup(
    val category: TaxCategory,
    val rate: Percent,
    // Bemessungsgrundlage: Summe aller Positionsnetto-Beträge dieser Gruppe.
    val base: Money,
    val amount: Money,
    /** BT-120, nur bei Kategorien ohne Steuer. */
    val exemptionReason: String? = null,
)

data class InvoiceTotals(
    val lines: List<CalculatedLine>,
    val netTotal: Money,
    val vatGroups: List<VatGroup>,
    val grossTotal: Money,
    val dueDate: LocalDate,
)

object InvoiceCalculator {
    fun calculate(invoice: Invoice, config: SellerConfig): InvoiceTotals {
        validate(invoice)

        // Erst pro Position runden, dann summieren – so stimmt die Summe mit dem
        // überein, was der Leser auf der Rechnung nachrechnen kann.
        //
        // roundToAmount() ist die einzige Rundung in dieser Funktion, und der Compiler
        // erzwingt sie: unitPrice ist ein Price (exakt), net ein Money (zwei Stellen).
        val lines = invoice.items.map { CalculatedLine(it, (it.unitPrice * it.quantity).roundToAmount()) }

        val netTotal = lines.sumOf { it.net }

        // USt. je Gruppe auf die summierte Bemessungsgrundlage berechnen
        // (nicht je Position!), so verlangt es EN 16931 (BG-23).
        val vatGroups =
            lines
                .groupBy { it.item.taxCategory to it.item.vatRate }
                .map { (schluessel, group) ->
                    val (category, rate) = schluessel
                    val base = group.sumOf { it.net }
                    VatGroup(
                        category = category,
                        rate = rate,
                        base = base,
                        amount = base * rate,
                        exemptionReason = reasonFor(category, group),
                    )
                }.sortedWith(compareBy({ it.category.ordinal }, { -it.rate.valueAsPercentage.toDouble() }))

        val grossTotal = netTotal + vatGroups.sumOf { it.amount }

        // Elvis-Operator `?:` – nimm den Override, sonst den Default aus den Stammdaten.
        val termDays = invoice.paymentTermDaysOverride ?: config.paymentTermDays
        val dueDate = invoice.issueDate.plusDays(termDays.toLong())

        return InvoiceTotals(lines, netTotal, vatGroups, grossTotal, dueDate)
    }

    /**
     * Die Regeln, die EN 16931 an die Steuerkategorie knüpft. Sie hier zu prüfen
     * und nicht erst beim ZUGFeRD-Export hat einen praktischen Grund: Eine
     * Rechnung mit "AE" und 19 % ginge sonst als PDF hinaus und fiele erst dem
     * Empfänger auf – dann ist sie schon verschickt.
     */
    private fun validate(invoice: Invoice) {
        invoice.items.forEachIndexed { index, item ->
            val position = "Position ${index + 1} (${item.name})"
            val kategorie = item.taxCategory

            if (kategorie.requiresZeroRate && item.vatRate.valueAsMultiplier.signum() != 0) {
                throw InvoiceValidationException(
                    "$position: Kategorie ${kategorie.code} (${kategorie.label}) verlangt einen Steuersatz von 0 %, " +
                        "angegeben ist ${item.vatRate}.",
                )
            }
            if (kategorie == TaxCategory.STANDARD && item.vatRate.valueAsMultiplier.signum() == 0) {
                throw InvoiceValidationException(
                    "$position: Kategorie S (Regelsatz) mit 0 % ist widersprüchlich. Gemeint ist vermutlich " +
                        "Z (Nullsatz), E (steuerbefreit) oder AE (Steuerschuldnerschaft des Leistungsempfängers).",
                )
            }
            if (!kategorie.requiresReason && item.taxExemptionReason != null) {
                throw InvoiceValidationException(
                    "$position: Kategorie ${kategorie.code} trägt keinen Steuerbefreiungsgrund.",
                )
            }
            if (kategorie.requiresReason && item.taxExemptionReason == null && kategorie.defaultReason == null) {
                throw InvoiceValidationException(
                    "$position: Kategorie ${kategorie.code} (${kategorie.label}) verlangt einen " +
                        "Steuerbefreiungsgrund – bitte 'steuerbefreiungsgrund' angeben.",
                )
            }
        }

        // BT-48: Ohne USt-IdNr. des Empfängers lässt sich weder die
        // Steuerschuldnerschaft noch eine innergemeinschaftliche Lieferung belegen.
        val brauchtKundenUstId =
            invoice.items
                .map { it.taxCategory }
                .filter { it == TaxCategory.REVERSE_CHARGE || it == TaxCategory.INTRA_COMMUNITY }
        if (brauchtKundenUstId.isNotEmpty() && invoice.customer.vatId == null) {
            throw InvoiceValidationException(
                "Kategorie ${brauchtKundenUstId.first().code} verlangt die USt-IdNr. des Kunden – " +
                    "bitte 'umsatzsteuer-id' unter 'kunde' angeben.",
            )
        }
    }

    /**
     * Der Befreiungsgrund einer Gruppe. Alle Positionen einer Gruppe müssen sich
     * einig sein: BG-23 trägt genau einen Grund je Kategorie und Satz, zwei
     * verschiedene Begründungen ließen sich dort nicht abbilden.
     */
    private fun reasonFor(category: TaxCategory, group: List<CalculatedLine>): String? {
        if (!category.requiresReason) return null
        val gruende = group.mapNotNull { it.item.taxExemptionReason }.distinct()
        if (gruende.size > 1) {
            throw InvoiceValidationException(
                "Kategorie ${category.code} hat zwei verschiedene Befreiungsgründe: " +
                    gruende.joinToString(" / ") + ". Die Steueraufschlüsselung trägt nur einen je Kategorie.",
            )
        }
        return gruende.firstOrNull() ?: category.defaultReason
    }
}
