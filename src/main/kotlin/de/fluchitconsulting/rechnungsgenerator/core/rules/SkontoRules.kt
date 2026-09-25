package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.SkontoTier
import java.math.BigDecimal

/**
 * Das Skonto (#183): Die Skontostufen dürfen sich weder selbst noch dem
 * Zahlungsziel widersprechen.
 *
 * Eine eigene Gruppe und nicht [InvoiceKindRules]: Dorthin gehört eine Regel,
 * wenn ohne sie keine Rechnungsart gebaut werden kann (ADR-0008). Skonto ist
 * keine Angabe einer Rechnungsart, [InvoiceKind] kennt es nicht.
 *
 * Die Stufen kommen sortiert aus [CalculatedInvoice.skonto]. Deshalb genügt
 * es, benachbarte Stufen zu vergleichen, und die Reihenfolge in der Datei
 * braucht keine eigene Regel.
 */
internal object SkontoRules {
    fun check(calculated: CalculatedInvoice): List<Finding> {
        val invoice = calculated.invoice
        val skonto = calculated.skonto
        if (skonto.isEmpty()) return emptyList()

        // Ohne `else`, wie bei den übrigen Gruppen (#121): Eine neue Art
        // übersetzt hier erst, wenn entschieden ist, ob sie Skonto tragen darf.
        // Auf der falschen Art bleibt es bei diesem einen Befund – Sätze über die
        // Fristen eines Skontos, das es nicht geben darf, lenkten nur ab (#97).
        when (invoice.type) {
            InvoiceType.INVOICE, InvoiceType.PARTIAL -> {}
            InvoiceType.PREPAYMENT, InvoiceType.FINAL, InvoiceType.CANCELLATION -> {
                return listOf(
                    Finding(
                        Rule.SKONTO_AUF_DIESER_ART_NICHT_MOEGLICH,
                        Subject.WholeInvoice,
                        "Skonto steht nur auf einer Rechnung oder Teilrechnung; " +
                            "dieses Dokument ist eine ${invoice.type.documentName}. Bitte 'skonto' weglassen.",
                    ),
                )
            }
        }

        // Aus demselben Grund nur dieser Befund: Ohne Zahlungsziel sagt ein
        // Vergleich der Fristen mit ihm nichts.
        if (!calculated.requestsPayment) {
            return listOf(
                Finding(
                    Rule.SKONTO_OHNE_ZAHLUNGSAUFFORDERUNG,
                    Subject.WholeInvoice,
                    "Dieses Dokument fordert nicht zur Zahlung auf, ein Skonto hätte keinen Gegenstand. " +
                        "Bitte 'skonto' weglassen.",
                ),
            )
        }

        return skonto.flatMap { tier(it, calculated.paymentTermDays) } +
            // `zipWithNext` liefert die Paare benachbarter Elemente: (1, 2), (2, 3), …
            skonto.zipWithNext().mapNotNull { (earlier, later) -> neighbours(earlier, later) }
    }

    /** Was an einer Stufe für sich falsch sein kann. */
    private fun tier(
        stufe: SkontoTier,
        paymentTermDays: Int,
    ): List<Finding> =
        buildList {
            val percentage = stufe.rate.valueAsPercentage
            // Zwei Nachkommastellen, wie die XRechnung sie für den Satz schreibt
            // (`PROZENT=2.00`). `stripTrailingZeros`, weil Percent intern mehr führt.
            if (percentage <= BigDecimal.ZERO || percentage >= HUNDRED || percentage.stripTrailingZeros().scale() > 2) {
                add(
                    Finding(
                        Rule.SKONTOSATZ_UNGUELTIG,
                        Subject.WholeInvoice,
                        "Der Skontosatz ${stufe.rate} ist ungültig. Zulässig sind Sätze über 0% und " +
                            "unter 100% mit höchstens zwei Nachkommastellen.",
                    ),
                )
            }
            if (stufe.days < 0) {
                add(
                    Finding(
                        Rule.SKONTOFRIST_NEGATIV,
                        Subject.WholeInvoice,
                        "Die Skontofrist von ${stufe.days} Tagen endete vor dem Rechnungsdatum. " +
                            "Bitte 'tage' prüfen; 0 heißt bei Zahlung am Rechnungsdatum.",
                    ),
                )
            } else if (paymentTermDays >= 0 && stufe.days >= paymentTermDays) {
                // Ein negatives Zahlungsziel meldet ZAHLUNGSZIEL_NEGATIV; der
                // Vergleich mit ihm wäre ein zweiter Befund über denselben Fehler.
                add(
                    Finding(
                        Rule.SKONTOFRIST_NICHT_VOR_ZAHLUNGSZIEL,
                        Subject.WholeInvoice,
                        "Die Skontofrist von ${stufe.days} Tagen endet nicht vor dem Zahlungsziel von " +
                            "$paymentTermDays Tagen. Skonto gilt für eine Zahlung vor dem Zahlungsziel; " +
                            "bitte 'tage' oder 'zahlungsziel-tage' prüfen.",
                    ),
                )
            }
        }

    /** Was zwei benachbarte Stufen zusammen falsch machen können. */
    private fun neighbours(
        earlier: SkontoTier,
        later: SkontoTier,
    ): Finding? =
        when {
            earlier.days == later.days -> {
                Finding(
                    Rule.SKONTOFRIST_DOPPELT,
                    Subject.WholeInvoice,
                    "Zwei Skontostufen haben dieselbe Frist von ${earlier.days} Tagen " +
                        "(${earlier.rate} und ${later.rate}). Welcher Satz gilt, bliebe offen.",
                )
            }
            // Wer später zahlt, bekäme mindestens so viel – die Stufe mit der
            // kürzeren Frist nutzte niemand. Meist sind die Sätze vertauscht.
            later.rate.valueAsPercentage >= earlier.rate.valueAsPercentage -> {
                Finding(
                    Rule.SKONTOSTUFE_WERTLOS,
                    Subject.WholeInvoice,
                    "Die Skontostufe ${earlier.rate} bis ${earlier.days} Tage ist wertlos: Bis " +
                        "${later.days} Tage gelten schon ${later.rate}. Sind die Sätze vertauscht?",
                )
            }
            else -> {
                null
            }
        }

    private val HUNDRED = BigDecimal(100)
}
