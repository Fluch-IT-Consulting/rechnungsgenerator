package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.model.LineItem
import de.fluchitconsulting.rechnung.model.SellerConfig
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

// Alle abgeleiteten Werte einer Rechnung. Wird aus Invoice + SellerConfig
// berechnet und ist danach unveränderlich – das Template bekommt nur noch dies.

data class CalculatedLine(
    val item: LineItem,
    // Netto der Position: Menge × Einzelpreis, kaufmännisch auf Cent gerundet.
    val net: BigDecimal,
)

data class VatGroup(
    val ratePercent: BigDecimal,
    // Bemessungsgrundlage: Summe aller Positionsnetto-Beträge mit diesem Satz.
    val base: BigDecimal,
    val amount: BigDecimal,
)

data class InvoiceTotals(
    val lines: List<CalculatedLine>,
    val netTotal: BigDecimal,
    val vatGroups: List<VatGroup>,
    val grossTotal: BigDecimal,
    val dueDate: LocalDate,
)

object InvoiceCalculator {
    // Private Extension-Funktion: erweitert BigDecimal nur innerhalb dieser Datei
    // um eine Rundungsmethode. HALF_UP = kaufmännisches Runden.
    private fun BigDecimal.roundToCents(): BigDecimal = setScale(2, RoundingMode.HALF_UP)

    fun calculate(
        invoice: Invoice,
        config: SellerConfig,
    ): InvoiceTotals {
        // Erst pro Position runden, dann summieren – so stimmt die Summe mit dem
        // überein, was der Leser auf der Rechnung nachrechnen kann.
        val lines = invoice.items.map { CalculatedLine(it, (it.quantity * it.unitPrice).roundToCents()) }

        // sumOf hat eine BigDecimal-Überladung – kein fold nötig.
        val netTotal = lines.sumOf { it.net }

        // USt. je Steuersatz auf die summierte Bemessungsgrundlage berechnen
        // (nicht je Position!), so verlangt es EN 16931 (BG-23).
        val vatGroups =
            lines
                .groupBy { it.item.vatRatePercent }
                .map { (rate, group) ->
                    val base = group.sumOf { it.net }
                    // movePointLeft(2) teilt durch 100, exakt und ohne Rundungsverlust.
                    VatGroup(rate, base, (base * rate).movePointLeft(2).roundToCents())
                }.sortedByDescending { it.ratePercent }

        val grossTotal = netTotal + vatGroups.sumOf { it.amount }

        // Elvis-Operator `?:` – nimm den Override, sonst den Default aus den Stammdaten.
        val termDays = invoice.paymentTermDaysOverride ?: config.paymentTermDays
        val dueDate = invoice.issueDate.plusDays(termDays.toLong())

        return InvoiceTotals(lines, netTotal, vatGroups, grossTotal, dueDate)
    }
}
