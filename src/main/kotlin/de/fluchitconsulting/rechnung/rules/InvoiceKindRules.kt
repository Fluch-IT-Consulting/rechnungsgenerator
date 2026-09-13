package de.fluchitconsulting.rechnung.rules

import de.fluchitconsulting.rechnung.CalculatedInvoice
import de.fluchitconsulting.rechnung.Deduction
import de.fluchitconsulting.rechnung.Finding
import de.fluchitconsulting.rechnung.InvoiceKind
import de.fluchitconsulting.rechnung.Rule
import de.fluchitconsulting.rechnung.Subject
import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.values.InvoiceType

/**
 * Die Rechnungsart als Typ – und die Regeln, ohne die er nicht baubar ist
 * (ADR-0008).
 *
 * Das Kriterium: Eine Regel gehört hierher, wenn [InvoiceKind] ohne sie nicht
 * gebaut werden kann. Das sind die Angaben, die zu einer anderen Rechnungsart
 * gehören, die, die der eigenen fehlen, und ein Abschlag ohne Vereinnahmung.
 * Alles andere prüfen die übrigen Regelgruppen; ein Abschlag mit spätem Datum
 * etwa ist baubar, nur falsch.
 *
 * Sie prüfen die flachen Angaben, nicht den gebauten Typ – sonst entfielen
 * mit einem einzigen Abschlag ohne Vereinnahmung auch die Prüfungen aller
 * anderen. Gehören die Angaben zu einer Rechnungsart, prüft ihre Gruppe sie
 * aber nur auf dieser Art (`PrepaymentRules`, `CancellationRules`,
 * `FinalInvoiceRules`, #97). Steht das Feld auf der falschen, bleibt allein der
 * Befund von hier: Die Meldungen dort sprächen von einem Dokument, das es auf
 * dem Blatt nicht gibt.
 *
 * Regel und Bau stehen in einem Durchgang, damit das Wissen über die
 * Rechnungsart nur einmal dasteht: Jeder Weg, auf dem kein Typ entsteht, liefert
 * den Befund, der erklärt, warum. Einen Zweig "kann nicht sein" gibt es nicht.
 *
 * Die einzige Regelgruppe, die mehr liefert als Befunde. Begründet, weil gerade
 * diese Regeln die Voraussetzungen des Typs sind.
 */
internal object InvoiceKindRules {
    /** Entweder die Rechnungsart oder die Befunde, warum keine entsteht. */
    sealed interface Result {
        data class Built(val kind: InvoiceKind) : Result

        data class NotBuilt(val findings: List<Finding>) : Result
    }

    // Liest den gemerkten Bau und baut nicht selbst: Dieselbe Rechnungsart prüft
    // AcceptedInvoice.check, und beide sollen aus einem Bau schöpfen (#93).
    fun check(calculated: CalculatedInvoice): List<Finding> =
        when (val result = calculated.kindResult) {
            is Result.Built -> emptyList()
            is Result.NotBuilt -> result.findings
        }

    fun build(calculated: CalculatedInvoice): Result {
        val invoice = calculated.invoice
        val fremd = foreignFields(invoice)

        // `when` über ein Enum ohne `else`: Kommt eine Rechnungsart dazu (#64),
        // übersetzt diese Stelle nicht mehr, bis sie ihren Typ bekommt.
        val eigen =
            when (invoice.type) {
                InvoiceType.INVOICE, InvoiceType.PARTIAL -> Result.Built(InvoiceKind.Ordinary(invoice.type))
                InvoiceType.PREPAYMENT -> Result.Built(InvoiceKind.PrepaymentInvoice(invoice.receivedOn))
                InvoiceType.FINAL -> finalInvoice(calculated)
                InvoiceType.CANCELLATION -> cancellation(invoice)
            }

        return when (eigen) {
            is Result.Built -> if (fremd.isEmpty()) eigen else Result.NotBuilt(fremd)
            is Result.NotBuilt -> Result.NotBuilt(fremd + eigen.findings)
        }
    }

    /**
     * Angaben, die zu einer anderen Rechnungsart gehören. Die Meldung spricht von
     * der Art des Dokuments, nicht von einem überzähligen YAML-Schlüssel – das
     * ist der Grund, warum das flache Modell diese Kombinationen zulässt.
     */
    private fun foreignFields(invoice: Invoice): List<Finding> =
        buildList {
            if (invoice.type != InvoiceType.CANCELLATION && invoice.cancelled != null) {
                add(
                    Finding(
                        Rule.BEZUG_OHNE_STORNO,
                        Subject.WholeInvoice,
                        "'storniert' gehört nur auf eine Stornorechnung (art: stornorechnung); " +
                            "dieses Dokument ist eine ${invoice.type.documentName}.",
                    ),
                )
            }
            // Der Zeitpunkt der Vereinnahmung (BT-7) gehört auf die
            // Abschlagsrechnung – und nur dorthin. Auf einer Rechnung über eine
            // erbrachte Leistung ist die Umsatzsteuer mit der Leistung entstanden,
            // nicht mit dem Geldeingang; die Angabe wäre dort eine falsche.
            if (invoice.type != InvoiceType.PREPAYMENT && invoice.receivedOn != null) {
                add(
                    Finding(
                        Rule.VEREINNAHMUNG_FALSCHE_ART,
                        Subject.WholeInvoice,
                        "'vereinnahmt-am' gehört nur auf eine Abschlagsrechnung (art: abschlagsrechnung); " +
                            "dieses Dokument ist eine ${invoice.type.documentName}.",
                    ),
                )
            }
            // Sobald abgesetzt wird, *ist* das Dokument eine Schlussrechnung.
            if (invoice.type != InvoiceType.FINAL && invoice.prepayments.isNotEmpty()) {
                add(
                    Finding(
                        Rule.ABSCHLAEGE_FALSCHE_ART,
                        Subject.WholeInvoice,
                        "Abschläge werden nur in einer Schlussrechnung abgesetzt (art: schlussrechnung); " +
                            "dieses Dokument ist eine ${invoice.type.documentName}.",
                    ),
                )
            }
        }

    /**
     * Die Schlussrechnung: Sie setzt Abschläge ab, und jeder davon ist vereinnahmt.
     *
     * Die beiden Listen entstehen in einer Schleife über dieselbe Bedingung –
     * ein Abschlag landet entweder bei den Absetzungen oder bei den Befunden,
     * nie in keiner und nie in beiden.
     */
    private fun finalInvoice(calculated: CalculatedInvoice): Result {
        // Fehlt die Absetzung, ist es entweder keine Schlussrechnung – oder sie
        // ist unvollständig.
        if (calculated.prepayments.isEmpty()) {
            return Result.NotBuilt(
                listOf(
                    Finding(
                        Rule.ABSCHLAEGE_FEHLEN,
                        Subject.WholeInvoice,
                        "Eine Schlussrechnung setzt die vereinnahmten Abschläge ab, hier ist keiner angegeben. " +
                            "Bitte 'abschlaege' füllen – oder, wenn es keine gab, 'art: rechnung' schreiben.",
                    ),
                ),
            )
        }

        val deductions = mutableListOf<Deduction>()
        val findings = mutableListOf<Finding>()
        calculated.prepayments.forEach { abgesetzt ->
            // §14 Abs. 5 Satz 2 UStG setzt ab, was vereinnahmt wurde, nicht was
            // berechnet wurde.
            val vereinnahmtAm = abgesetzt.prepayment.receivedOn
            if (vereinnahmtAm != null) {
                deductions += Deduction(abgesetzt, vereinnahmtAm)
            } else {
                findings +=
                    Finding(
                        Rule.ABSCHLAG_OHNE_VEREINNAHMUNG,
                        Subject.Prepayment(abgesetzt.prepayment.invoiceNumber),
                        "ohne 'vereinnahmt-am' keine Absetzung. Abgesetzt wird, was vereinnahmt " +
                            "wurde – ein berechneter, aber unbezahlter Abschlag ist eine offene Forderung " +
                            "und wird gemahnt oder storniert.",
                    )
            }
        }
        return if (findings.isEmpty()) Result.Built(InvoiceKind.FinalInvoice(deductions)) else Result.NotBuilt(findings)
    }

    /**
     * Die Stornorechnung und ihr Bezug. Im flachen Modell optional, fachlich
     * Pflicht – ein Storno ohne Angabe, was es aufhebt, ist wertlos.
     */
    private fun cancellation(invoice: Invoice): Result =
        invoice.cancelled?.let { Result.Built(InvoiceKind.Cancellation(it)) }
            ?: Result.NotBuilt(
                listOf(
                    Finding(
                        Rule.STORNO_OHNE_BEZUG,
                        Subject.WholeInvoice,
                        "Eine Stornorechnung nennt die Rechnung, die sie aufhebt – 'storniert' fehlt. " +
                            "Ohne Nummer und Datum der Ursprungsrechnung ist sie wertlos (EN 16931 BG-3).",
                    ),
                ),
            )
}
