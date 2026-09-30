package de.fluchitconsulting.rechnungsgenerator.core.calculation

import de.fluchitconsulting.rechnungsgenerator.core.model.Discount
import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.LineItem
import de.fluchitconsulting.rechnungsgenerator.core.model.Prepayment
import de.fluchitconsulting.rechnungsgenerator.core.model.SellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.model.SkontoTier
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent
import de.fluchitconsulting.rechnungsgenerator.core.values.sumOf
import java.time.LocalDate

data class CalculatedLine(
    val item: LineItem,
) {
    // BT-137, Basis des Rabatts.
    val base: Money = (item.unitPrice * item.quantity).roundToAmount()

    // BT-136. Der Betrag übernimmt das Vorzeichen der Basis, sonst forderte ein Storno mehr zurück (ADR-0013).
    val discountAmount: Money? =
        when (val discount = item.discount) {
            null -> null
            is Discount.Rate -> base * discount.rate
            is Discount.Amount -> if (base.isNegative()) -discount.amount else discount.amount
        }

    // BT-131. Als Differenz, damit Rabatt und Netto zusammen immer die Basis ergeben.
    val net: Money = discountAmount?.let { base - it } ?: base
}

/**
 * Eine Zeile der Steueraufschlüsselung (BG-23). Gruppiert nach Kategorie *und* Satz:
 * 0 % kann eine Befreiung, Reverse Charge oder ein Nullsatz sein.
 */
data class VatGroup(
    val category: TaxCategory,
    val rate: Percent,
    val base: Money,
    val amount: Money,
    val language: Language,
    // Alle genannten Gründe, ohne Vorgabegrund – damit „uneinig“ von „keiner“ unterscheidbar bleibt (#148).
    val reasons: Set<String> = emptySet(),
) {
    /** BT-120. Sind die Positionen uneinig, keiner statt eines geratenen (BEFREIUNGSGRUENDE_UNEINHEITLICH). */
    val exemptionReason: String? get() =
        when {
            !category.requiresReason -> null
            reasons.size > 1 -> null
            else -> reasons.singleOrNull() ?: category.defaultReason(language)
        }
}

/** Ein abgesetzter Abschlag. Netto und Steuer getrennt, weil §14 Abs. 5 Satz 2 UStG beide absetzt (ADR-0002). */
data class DeductedPrepayment(
    val prepayment: Prepayment,
    val net: Money,
    val vat: Money,
    val gross: Money,
)

/**
 * Eine Rechnung, ausgerechnet mit ihren Stammdaten, aber ungeprüft (ADR-0004).
 *
 * Im Konstruktor stehen nur die Eingaben, alles andere leitet der Rumpf ab: Eine
 * Rechnung mit den Summen einer anderen ist nicht baubar, auch nicht über `copy()` (#172).
 */
data class CalculatedInvoice(
    val invoice: Invoice,
    val config: SellerConfig,
) {
    val lines: List<CalculatedLine> = invoice.items.map(::CalculatedLine)

    // BT-106, aus den je Position gerundeten Beträgen – so rechnet der Leser nach.
    val netTotal: Money = lines.sumOf { it.net }

    // Steuer je Gruppe auf die summierte Basis, nicht je Position (BG-23). Die Reihenfolge
    // hängt nicht an der Datei, sonst änderten vertauschte Positionen Blatt und `abschlaege`.
    val vatGroups: List<VatGroup> =
        lines
            .groupBy(::vatGroupKey)
            .map { (key, group) ->
                val (category, rate) = key
                val base = group.sumOf { it.net }
                VatGroup(
                    category = category,
                    rate = rate,
                    base = base,
                    amount = base * rate,
                    language = invoice.language,
                    reasons = group.mapNotNull { it.item.taxExemptionReason }.toSet(),
                )
            }.sortedWith(compareBy<VatGroup> { it.category }.thenByDescending { it.rate.valueAsPercentage })

    /** Die Gruppe einer Position. Hier fragen, statt den Schlüssel nachzubauen (#148). */
    fun vatGroupOf(line: CalculatedLine): VatGroup = vatGroups.single { (it.category to it.rate) == vatGroupKey(line) }

    // BT-110
    val vatTotal: Money = vatGroups.sumOf { it.amount }

    val grossTotal: Money = netTotal + vatTotal

    // BT-111. Umgerechnet wird die Summe, nicht jede Gruppe – mit genau einer Rundung (ADR-0027).
    val vatTotalInEuro: Money? = invoice.conversion?.let { it.rate.toEuro(vatTotal) }

    val prepayments: List<DeductedPrepayment> =
        invoice.prepayments.map { abschlag ->
            val net = abschlag.taxShares.sumOf { it.net }
            val vat = abschlag.taxShares.sumOf { it.vat }
            DeductedPrepayment(abschlag, net = net, vat = vat, gross = net + vat)
        }

    val paymentTermDays: Int get() = invoice.paymentTermDaysOverride ?: config.paymentTermDays

    // Einmal sortiert, damit Regeln und Blatt dieselbe Liste lesen.
    val skonto: List<SkontoTier> get() = invoice.skonto.sortedBy { it.days }

    /** Der letzte Tag, an dem diese Stufe gilt. */
    fun skontoUntil(stufe: SkontoTier): LocalDate = invoice.issueDate.plusDays(stufe.days.toLong())

    val prepaymentTotal: Money get() = prepayments.sumOf { it.gross }

    val remainingAmount: Money get() = grossTotal - prepaymentTotal

    // Die Abschläge decken den Betrag genau; dann steht ein Satz statt eines Restbetrags von null.
    val settledByPrepayments: Boolean get() = prepayments.isNotEmpty() && remainingAmount.isZero()

    /**
     * BT-113. Mit `vereinnahmt-am`, das nur die Abschlagsrechnung trägt, ist der ganze Abschlag vereinnahmt, nicht
     * ein Teil: So lesen es das Glossar, `abschlaege` und die Schlussrechnung (#503).
     */
    val paidAmount: Money get() = if (invoice.receivedOn != null) grossTotal else prepaymentTotal

    // BT-115
    val amountDue: Money get() = grossTotal - paidAmount

    /**
     * Fordert das Dokument Geld? Daran hängen Zahlungsziel und Bankverbindung. Nicht dasselbe wie
     * [InvoiceType.chargesForService]: Eine ausgeglichene Schlussrechnung oder eine vereinnahmte Abschlagsrechnung
     * rechnet ab, fordert aber nichts. Positiv statt „nicht null“, damit die Aussage nicht an den Regeln gegen
     * negative Beträge hängt (#130).
     */
    val requestsPayment: Boolean get() = invoice.type.chargesForService && amountDue.isPositive()

    // Glossar *Inland*: im Land des Rechnungsstellers, nicht „in Deutschland“.
    val isDomestic: Boolean get() = invoice.customer.address.countryCode == config.address.countryCode

    // Glossar *Ausland*: Ausland und EU sind zwei verschiedene Fragen.
    val customerInEu: Boolean get() = EuMemberStates.contains(invoice.customer.address.countryCode)

    val taxCategories: Set<TaxCategory> get() = vatGroups.map { it.category }.toSet()

    val dueDate: LocalDate? get() =
        if (!requestsPayment) {
            null
        } else {
            invoice.issueDate.plusDays(paymentTermDays.toLong())
        }

    /**
     * Jobname für LuaLaTeX, etwa „Abschlagsrechnung-R26-009“; `null`, wenn von der Nummer nichts
     * übrig bleibt (RECHNUNGSNUMMER_OHNE_DATEINAMEN). Nicht beliebig: pdfx bildet daraus die
     * DocumentID im XMP (ADR-0005).
     */
    val fileBaseName: String? get() =
        invoice.number
            .replace(Regex("[^A-Za-z0-9._-]"), "-")
            .trim('-')
            .takeIf { it.isNotEmpty() }
            ?.let { "${invoice.type.documentName}-$it" }
}

private fun vatGroupKey(line: CalculatedLine) = line.item.taxCategory to line.item.vatRate
