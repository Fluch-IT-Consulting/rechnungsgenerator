package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.model.LineItem
import de.fluchitconsulting.rechnung.model.SellerConfig
import de.fluchitconsulting.rechnung.values.Money
import de.fluchitconsulting.rechnung.values.Percent
import de.fluchitconsulting.rechnung.values.sumOf
import java.time.LocalDate

// Alle abgeleiteten Werte einer Rechnung. Wird aus Invoice + SellerConfig
// berechnet und ist danach unveränderlich – das Template bekommt nur noch dies.

data class CalculatedLine(
    val item: LineItem,
    // Netto der Position: Menge × Einzelpreis. Das Runden auf Cent macht Money selbst.
    val net: Money,
)

data class VatGroup(
    val rate: Percent,
    // Bemessungsgrundlage: Summe aller Positionsnetto-Beträge mit diesem Satz.
    val base: Money,
    val amount: Money,
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
        // Erst pro Position runden, dann summieren – so stimmt die Summe mit dem
        // überein, was der Leser auf der Rechnung nachrechnen kann.
        //
        // roundToAmount() ist die einzige Rundung in dieser Funktion, und der Compiler
        // erzwingt sie: unitPrice ist ein Price (exakt), net ein Money (zwei Stellen).
        val lines = invoice.items.map { CalculatedLine(it, (it.unitPrice * it.quantity).roundToAmount()) }

        val netTotal = lines.sumOf { it.net }

        // USt. je Steuersatz auf die summierte Bemessungsgrundlage berechnen
        // (nicht je Position!), so verlangt es EN 16931 (BG-23).
        // groupBy stützt sich auf equals/hashCode von Percent – dort numerisch
        // definiert, "19" und "19.00" landen also in derselben Gruppe.
        val vatGroups =
            lines
                .groupBy { it.item.vatRate }
                .map { (rate, group) ->
                    val base = group.sumOf { it.net }
                    VatGroup(rate, base, base * rate)
                }.sortedByDescending { it.rate.valueAsPercentage }

        val grossTotal = netTotal + vatGroups.sumOf { it.amount }

        // Elvis-Operator `?:` – nimm den Override, sonst den Default aus den Stammdaten.
        val termDays = invoice.paymentTermDaysOverride ?: config.paymentTermDays
        val dueDate = invoice.issueDate.plusDays(termDays.toLong())

        return InvoiceTotals(lines, netTotal, vatGroups, grossTotal, dueDate)
    }
}
