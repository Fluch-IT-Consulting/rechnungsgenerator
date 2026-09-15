package de.fluchitconsulting.rechnungsgenerator.calculation

import de.fluchitconsulting.rechnungsgenerator.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.model.LineItem
import de.fluchitconsulting.rechnungsgenerator.model.Prepayment
import de.fluchitconsulting.rechnungsgenerator.model.SellerConfig
import de.fluchitconsulting.rechnungsgenerator.values.EuMemberStates
import de.fluchitconsulting.rechnungsgenerator.values.Money
import de.fluchitconsulting.rechnungsgenerator.values.Percent
import de.fluchitconsulting.rechnungsgenerator.values.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.values.sumOf
import java.time.LocalDate

// Eine Rechnung samt allem, was sich aus ihr und den Stammdaten ausrechnet.
// Unveränderlich; der Renderer bekommt nur noch dies.

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
    /**
     * Die Befreiungsgründe, die die Positionen dieser Gruppe ausdrücklich
     * nennen – ohne den Vorgabegrund der Kategorie.
     *
     * Die Gruppe trägt alle und nicht nur den einen, der auf das PDF kommt:
     * Sonst gäbe [exemptionReason] `null` für "kein Grund" ebenso wie für
     * "uneinig", und die Regel BEFREIUNGSGRUENDE_UNEINHEITLICH müsste den
     * Gruppenschlüssel nachbauen, um das eine vom anderen zu unterscheiden.
     * Änderte sich dann die Gruppierung, prüfte sie still andere Gruppen (#148).
     */
    val reasons: Set<String> = emptySet(),
) {
    /**
     * BT-120, nur bei Kategorien, die einen Grund verlangen. Alle Positionen
     * einer Gruppe müssen sich einig sein: BG-23 trägt genau einen Grund je
     * Kategorie und Satz.
     *
     * Ohne Einigkeit gibt es keinen Grund: Einen der beiden zu wählen hieße
     * raten, welcher gemeint war. Gemeldet wird das als
     * BEFREIUNGSGRUENDE_UNEINHEITLICH.
     */
    val exemptionReason: String? get() =
        when {
            !category.requiresReason -> null
            reasons.size > 1 -> null
            else -> reasons.singleOrNull() ?: category.defaultReason
        }
}

/**
 * Ein abgesetzter Abschlag mit seinen Summen.
 *
 * Netto und Steuer bleiben getrennt (ADR-0002): §14 Abs. 5 Satz 2 UStG setzt
 * das Entgelt *und* die darauf entfallende Steuer ab, und aus einem
 * Bruttobetrag ließe sich beides nicht zurückgewinnen.
 */
data class DeductedPrepayment(
    val prepayment: Prepayment,
    val net: Money,
    val vat: Money,
    val gross: Money,
)

/**
 * Eine Rechnung, wie sie sich mit ihren Stammdaten ausrechnet: die Eingaben und
 * alles Abgeleitete in einem Wert.
 *
 * Vorher reisten Rechnung und Summen als Paar, das nichts zusammenhielt. Jeder
 * Verbraucher nahm beide entgegen und verließ sich darauf, dass sie zueinander
 * gehören; zwei Eingaben standen sicherheitshalber noch einmal in den Summen.
 * Jetzt ist ein Paar aus einer Rechnung und den Summen einer anderen nicht mehr
 * baubar – nicht bloß ungebaut, derselbe Zug wie beim Zahlungsziel.
 */
data class CalculatedInvoice(
    val invoice: Invoice,
    val config: SellerConfig,
    val lines: List<CalculatedLine>,
    val netTotal: Money,
    val vatGroups: List<VatGroup>,
    val grossTotal: Money,
    /** Die abgesetzten Abschläge, in der Reihenfolge der Rechnungsdatei. */
    val prepayments: List<DeductedPrepayment> = emptyList(),
) {
    /**
     * Die vereinbarte Zahlungsfrist in Tagen: die der Rechnung, sonst die der
     * Stammdaten. Abgeleitet und nicht kopiert – als eigenes Feld wäre es
     * dieselbe Angabe ein zweites Mal, und die zweite kann abweichen.
     */
    val paymentTermDays: Int get() = invoice.paymentTermDaysOverride ?: config.paymentTermDays

    // Abgeleitet statt gespeichert: Zwei Felder, die dasselbe sagen wie die
    // Liste, könnten von ihr abweichen.
    val prepaymentTotal: Money get() = prepayments.sumOf { it.gross }

    /** Was nach der Absetzung noch zu zahlen ist. Null ist zulässig, negativ nicht. */
    val remainingAmount: Money get() = grossTotal - prepaymentTotal

    /**
     * Fordert dieses Dokument zur Zahlung auf?
     *
     * Nicht dasselbe wie [InvoiceType.chargesForService]: Eine Schlussrechnung,
     * deren Abschläge den Betrag decken, rechnet die Leistung ab – sie *ist* die
     * Rechnung darüber –, fordert aber kein Geld mehr. Die Stornorechnung tut
     * beides nicht (ADR-0006).
     *
     * Aus dieser einen Aussage folgen Zahlungsziel und Bankverbindung. Vorher
     * fragte jedes für sich, und unter "es verbleibt kein Restbetrag" stand ein
     * Konto (#94). Die Entscheidung fällt hier und nicht im Renderer – der setzt,
     * was dasteht, und entscheidet nicht, was dastehen soll.
     *
     * Positiv und nicht bloß "nicht null": Ein negativer Betrag fordert kein
     * Geld, gleich was die Art abrechnet. Auf ein PDF kommt er heute ohnehin
     * nicht, GESAMTBETRAG_NEGATIV und RESTBETRAG_NEGATIV lehnen ihn ab. Die
     * Aussage soll aber am Wert selbst hängen und nicht daran, dass Regeln in
     * anderen Gruppen zu ihr passen (#130).
     */
    val requestsPayment: Boolean get() = invoice.type.chargesForService && remainingAmount.isPositive()

    /**
     * Ist das eine Inlandsrechnung? Glossar *Inland*: Der Kunde sitzt im Land
     * des Rechnungsstellers – nicht "in Deutschland". Beides fällt nur
     * zusammen, solange der Rechnungssteller in Deutschland sitzt.
     *
     * Die Verwechslung ist schon passiert: Im Renderer stand ein fester
     * Vergleich mit "DE". Ein kleingeschriebenes `land: de` bekam dort eine
     * Landzeile, während die Regeln dieselbe Rechnung als Inlandsrechnung
     * behandelten, und ein Rechnungssteller in Österreich hätte "AT" unter jede
     * seiner Inlandsanschriften gesetzt. Danach rechneten Renderer und Regeln
     * den Vergleich je für sich – ein Umzug musste an zwei Stellen richtig
     * sein. Jetzt steht er hier einmal (#120). Ein schlichtes `==` genügt, weil
     * `CountryCode` die Schreibweise vereinheitlicht.
     */
    val isDomestic: Boolean get() = invoice.customer.address.countryCode == config.address.countryCode

    /**
     * Sitzt der Kunde in einem EU-Mitgliedstaat? Glossar *Ausland*: Ausland
     * und EU sind zwei Fragen, nicht eine – ein Kunde in Deutschland ist für
     * einen Rechnungssteller in der Schweiz Ausland *und* EU.
     *
     * Davon hängt ab, ob die USt-IdNr. über VIES oder das BZSt überhaupt
     * geprüft werden kann.
     */
    val customerInEu: Boolean get() = EuMemberStates.contains(invoice.customer.address.countryCode)

    /**
     * Bis wann zu zahlen ist – oder `null`, wenn das Dokument nicht zur Zahlung
     * auffordert ([requestsPayment]). Bei einer Schlussrechnung ohne Restbetrag
     * wäre ein Zahlungsziel neben "es verbleibt kein Restbetrag" die zweite,
     * andere Aussage desselben Blattes.
     *
     * Abgeleitet und nicht gesetzt, aus demselben Grund wie [remainingAmount]:
     * Vorher entstand das Ergebnis erst ohne Zahlungsziel und wurde danach
     * kopiert, und wer eines in der Hand hielt, sah ihm nicht an, ob die
     * Entscheidung schon gefallen war. Jetzt ist ein Ergebnis mit Zahlungsziel
     * *und* Restbetrag null nicht mehr baubar – nicht bloß ungebaut.
     */
    val dueDate: LocalDate? get() =
        if (!requestsPayment) {
            null
        } else {
            invoice.issueDate.plusDays(paymentTermDays.toLong())
        }
}

/**
 * Rechnet eine Rechnung aus – und prüft sie nicht.
 *
 * Geprüft wird in den Regelgruppen unter `rules`, jede gegen das fertige
 * Ergebnis (#71). Vorher stand beides hier ineinander: Vorzeichen und
 * Zahlungsziel wurden mitten zwischen den Rechenschritten geprüft, ein
 * Befund entstand als Seiteneffekt der Gruppierung, und die Datei war auf 920
 * Zeilen gewachsen, von denen ein Sechstel rechnete.
 */
object InvoiceCalculator {
    fun calculate(invoice: Invoice, config: SellerConfig): CalculatedInvoice {
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
                        reasons = group.mapNotNull { it.item.taxExemptionReason }.toSet(),
                    )
                }.sortedWith(compareBy({ it.category.ordinal }, { -it.rate.valueAsPercentage.toDouble() }))

        val grossTotal = netTotal + vatGroups.sumOf { it.amount }

        val prepayments =
            invoice.prepayments.map { abschlag ->
                val net = abschlag.taxShares.sumOf { it.net }
                val vat = abschlag.taxShares.sumOf { it.vat }
                DeductedPrepayment(abschlag, net = net, vat = vat, gross = net + vat)
            }

        return CalculatedInvoice(invoice, config, lines, netTotal, vatGroups, grossTotal, prepayments)
    }
}
