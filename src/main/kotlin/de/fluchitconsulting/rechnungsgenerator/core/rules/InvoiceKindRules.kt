package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType

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
 * dem Blatt nicht gibt – etwa von "dieser Schlussrechnung" auf einem Blatt, das
 * keine ist, dieselbe Sorte Meldung, die #47 bei RESTBETRAG_NEGATIV behoben hat.
 *
 * Diese Zuordnung trifft jede Gruppe selbst, als `when` über alle Arten ohne
 * `else`, ebenso das Vorzeichen in `CancellationRules` (#121). Eine neue Art
 * übersetzt dort erst, wenn entschieden ist, ob die Gruppe auf ihr spricht –
 * mit `if (type != X)` fiele sie still heraus. Nicht zentral in
 * `InvoiceRules.check`: Dann müsste der Aufrufer wissen, auf welcher Art eine
 * Gruppe aufgerufen werden darf, und den gebauten Typ hineinzureichen verbietet
 * das oben Gesagte.
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
        /** Die Befunde, warum keine Rechnungsart entsteht – leer, wenn sie gebaut ist. */
        val findings: List<Finding>

        data class Built(val kind: InvoiceKind) : Result {
            override val findings: List<Finding> get() = emptyList()
        }

        data class NotBuilt(override val findings: List<Finding>) : Result
    }

    fun check(calculated: CalculatedInvoice): Result {
        val invoice = calculated.invoice
        val foreign = foreignFields(invoice)

        // `when` über ein Enum ohne `else`: Kommt eine Rechnungsart dazu,
        // übersetzt diese Stelle nicht mehr, bis sie ihren Typ bekommt.
        val own =
            when (invoice.type) {
                InvoiceType.INVOICE, InvoiceType.PARTIAL -> Result.Built(InvoiceKind.Ordinary(invoice.type))
                InvoiceType.PREPAYMENT -> Result.Built(InvoiceKind.PrepaymentInvoice(invoice.receivedOn))
                InvoiceType.FINAL -> finalInvoice(calculated)
                InvoiceType.CANCELLATION -> cancellation(invoice)
            }

        return when (own) {
            is Result.Built -> if (foreign.isEmpty()) own else Result.NotBuilt(foreign)
            is Result.NotBuilt -> Result.NotBuilt(foreign + own.findings)
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
        calculated.prepayments.forEach { deducted ->
            // §14 Abs. 5 Satz 2 UStG setzt ab, was vereinnahmt wurde, nicht was
            // berechnet wurde.
            val vereinnahmtAm = deducted.prepayment.receivedOn
            if (vereinnahmtAm != null) {
                deductions += Deduction(deducted, vereinnahmtAm)
            } else {
                findings +=
                    Finding(
                        Rule.ABSCHLAG_OHNE_VEREINNAHMUNG,
                        Subject.Prepayment(deducted.prepayment.invoiceNumber),
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
