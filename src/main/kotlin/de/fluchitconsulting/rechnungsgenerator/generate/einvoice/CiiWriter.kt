package de.fluchitconsulting.rechnungsgenerator.generate.einvoice

import de.fluchitconsulting.rechnungsgenerator.core.accepted.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.accepted.InvoiceVariableResolver
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedLine
import de.fluchitconsulting.rechnungsgenerator.core.calculation.VatGroup
import de.fluchitconsulting.rechnungsgenerator.core.model.Address
import de.fluchitconsulting.rechnungsgenerator.core.model.CancelledInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.ServicePeriod
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.rules.Deduction
import de.fluchitconsulting.rechnungsgenerator.core.rules.InvoiceKind
import de.fluchitconsulting.rechnungsgenerator.core.values.Discount
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Schreibt eine Rechnung als CII-XML im Profil EXTENDED von ZUGFeRD (ADR-0023).
 *
 * Der Writer rechnet nichts, wie der LaTeX-Renderer: Jede Zahl kommt aus der
 * [CalculatedInvoice], damit im XML dieselbe steht wie auf dem Blatt. Das XML
 * ist die maßgebliche Fassung der E-Rechnung, und eine Abweichung um einen
 * Cent wäre dort ein Fehler der Rechnung und nicht der Darstellung.
 *
 * Die Reihenfolge der Elemente ist die des Schemas, nicht die des Blatts.
 *
 * Was eine Rechnungsart zusätzlich trägt – Vereinnahmung, Ursprungsrechnung,
 * abgesetzte Abschläge –, fragt der Writer die [InvoiceKind], je in einem
 * `when` ohne `else`: Eine neue Art muss dort entscheiden, was sie trägt
 * (ADR-0008).
 */
object CiiWriter {
    private const val EXTENDED = "urn:cen.eu:en16931:2017#conformant#urn:factur-x.eu:1p0:extended"

    private val namespaces =
        mapOf(
            "rsm" to "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100",
            "ram" to "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100",
            "qdt" to "urn:un:unece:uncefact:data:standard:QualifiedDataType:100",
            "udt" to "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100",
        )

    fun write(acceptedInvoice: AcceptedInvoice): String {
        val calculated = acceptedInvoice.calculated
        return indentedXml("rsm:CrossIndustryInvoice", namespaces) {
            element("rsm:ExchangedDocumentContext") {
                element("ram:GuidelineSpecifiedDocumentContextParameter") {
                    element("ram:ID", EXTENDED)
                }
            }
            element("rsm:ExchangedDocument") {
                element("ram:ID", calculated.invoice.number)
                element("ram:TypeCode", calculated.invoice.type.code)
                date("ram:IssueDateTime", calculated.invoice.issueDate)
                // Einleitung und Bemerkung der Rechnungsdatei, BT-22. Nicht der
                // Schlusssatz aus den Stammdaten, den die Bemerkung auf dem
                // Blatt ersetzt: Er ist eine Höflichkeit und keine Angabe zur
                // Rechnung.
                //
                // Dazu der Pflichtsatz der Rechnungsart, der erst das Dokument
                // zu dem macht, was es ist: Ohne ihn stünde der Abschlag wie
                // eine Rechnung über die Leistung da und das Storno ohne den
                // Satz, dass es die Ursprungsrechnung aufhebt.
                val mandatoryNote =
                    calculated.invoice.type
                        .mandatoryNote(calculated.invoice.language)
                        ?.toPlainText(calculated.invoice.language, InvoiceVariableResolver(acceptedInvoice))
                listOfNotNull(calculated.invoice.introduction, calculated.invoice.note, mandatoryNote).forEach { note ->
                    element("ram:IncludedNote") { element("ram:Content", note) }
                }
            }
            element("rsm:SupplyChainTradeTransaction") {
                calculated.lines.forEachIndexed { index, line -> lineItem(index, line, calculated.vatGroupOf(line)) }
                agreement(calculated)
                delivery(calculated)
                settlement(acceptedInvoice)
            }
        }
    }

    /**
     * Meist leer, weil hier nur ein Lieferdatum (BT-72) stünde, und bei einem
     * Leistungszeitraum wäre jedes Datum erfunden. Der Zeitraum steht in der
     * Abrechnung (BG-14). Das Schematron warnt deshalb mit
     * PEPPOL-EN16931-R008, das Schema verlangt das Element.
     *
     * Nur die innergemeinschaftliche Lieferung braucht ein Lieferland (BT-80,
     * BR-IC-12). Das ist das Land des Kunden: Ein eigenes Feld für eine
     * abweichende Lieferanschrift, etwa beim Reihengeschäft, kennt das Modell
     * nicht, und bei einer Beratungsrechnung kommt K praktisch nicht vor.
     */
    private fun XmlElements.delivery(calculated: CalculatedInvoice) {
        if (TaxCategory.INTRA_COMMUNITY in calculated.taxCategories) {
            element("ram:ApplicableHeaderTradeDelivery") {
                element("ram:ShipToTradeParty") {
                    element("ram:PostalTradeAddress") {
                        element("ram:CountryID", calculated.invoice.customer.address.countryCode.value)
                    }
                }
            }
        } else {
            empty("ram:ApplicableHeaderTradeDelivery")
        }
    }

    private fun XmlElements.lineItem(
        index: Int,
        line: CalculatedLine,
        group: VatGroup,
    ) {
        val item = line.item
        element("ram:IncludedSupplyChainTradeLineItem") {
            element("ram:AssociatedDocumentLineDocument") {
                element("ram:LineID", "${index + 1}")
                item.note?.let { note -> element("ram:IncludedNote") { element("ram:Content", note) } }
            }
            element("ram:SpecifiedTradeProduct") {
                element("ram:Name", item.name)
                item.description?.let { element("ram:Description", it) }
            }
            element("ram:SpecifiedLineTradeAgreement") {
                element("ram:NetPriceProductTradePrice") {
                    element("ram:ChargeAmount", item.unitPrice.value.toPlainString())
                }
            }
            element("ram:SpecifiedLineTradeDelivery") {
                element("ram:BilledQuantity", item.quantity.toPlainString(), "unitCode" to item.unit.code)
            }
            element("ram:SpecifiedLineTradeSettlement") {
                element("ram:ApplicableTradeTax") {
                    element("ram:TypeCode", "VAT")
                    // Der Grund der Gruppe und nicht der der Position: EXTENDED
                    // verlangt beide gleich (BR-FXEXT-E-08rev und die
                    // Geschwister je Kategorie), und die Gruppe trägt schon den
                    // Vorgabegrund der Kategorie.
                    group.exemptionReason?.let { element("ram:ExemptionReason", it) }
                    element("ram:CategoryCode", item.taxCategory.code)
                    // Nicht steuerbar heißt ohne Satz, nicht mit 0 % (BR-O-05).
                    // In der Aufschlüsselung darf die 0 stehen (BR-48).
                    if (item.taxCategory != TaxCategory.OUT_OF_SCOPE) {
                        element("ram:RateApplicablePercent", percent(item.vatRate))
                    }
                }
                item.servicePeriod?.let { period(it) }
                discount(line)
                element("ram:SpecifiedTradeSettlementLineMonetarySummation") {
                    element("ram:LineTotalAmount", amount(line.net))
                }
            }
        }
    }

    /**
     * Der Rabatt als Abschlag auf die Position (BG-27, ADR-0013). Ein Satz
     * steht mit seiner Basis da, ein vereinbarter Betrag allein: Eine Basis
     * ohne Satz sagte nichts, woraus der Betrag folgt.
     *
     * Nur der Code 95 (Discount, UNTDID 5189) und kein Text: BR-42 verlangt
     * eines von beiden, und ein Text müsste nach BR-CO-07 dasselbe sagen wie
     * der Code.
     */
    private fun XmlElements.discount(line: CalculatedLine) {
        val discount = line.item.discount ?: return
        element("ram:SpecifiedTradeAllowanceCharge") {
            element("ram:ChargeIndicator") { element("udt:Indicator", "false") }
            when (discount) {
                is Discount.Rate -> {
                    element("ram:CalculationPercent", percent(discount.rate))
                    element("ram:BasisAmount", amount(line.base))
                }
                is Discount.Amount -> {
                    // Vereinbart ist nur der Betrag; Satz und Basis kämen
                    // aus einer Rechnung, die niemand vereinbart hat.
                }
            }
            element("ram:ActualAmount", amount(checkNotNull(line.discountAmount)))
            element("ram:ReasonCode", "95")
        }
    }

    private fun XmlElements.agreement(calculated: CalculatedInvoice) {
        val config = calculated.config
        val customer = calculated.invoice.customer
        element("ram:ApplicableHeaderTradeAgreement") {
            customer.buyerReference?.let { element("ram:BuyerReference", it) }
            element("ram:SellerTradeParty") {
                element("ram:Name", config.companyName)
                // Die Registernummer allein ist nicht eindeutig, erst mit dem
                // Gericht; BT-30 hat aber nur ein Feld, das Gericht steht
                // deshalb in BT-33. Dort auch die Geschäftsführung, die das
                // Blatt nennt (§35a GmbHG): Das XML ist die maßgebliche
                // Fassung (ADR-0023). BT-33 gibt es nur einmal; getrennt wird
                // mit Semikolon, weil mehrere Namen schon Kommas tragen.
                listOfNotNull(
                    config.registerCourt?.let { "Registergericht $it" },
                    config.managingDirector?.let { "Geschäftsführung: $it" },
                ).takeIf { it.isNotEmpty() }
                    ?.let { element("ram:Description", it.joinToString("; ")) }
                config.registerNumber?.let { number ->
                    element("ram:SpecifiedLegalOrganization") { element("ram:ID", number) }
                }
                element("ram:DefinedTradeContact") {
                    element("ram:PersonName", config.ownerName)
                    config.phone?.let { phone ->
                        element("ram:TelephoneUniversalCommunication") { element("ram:CompleteNumber", phone) }
                    }
                    element("ram:EmailURIUniversalCommunication") { element("ram:URIID", config.email) }
                }
                address(config.address)
                element("ram:URIUniversalCommunication") {
                    element("ram:URIID", config.email, "schemeID" to "EM")
                }
                element("ram:SpecifiedTaxRegistration") {
                    element("ram:ID", config.vatId.value, "schemeID" to "VA")
                }
                config.taxNumber?.let { taxNumber ->
                    element("ram:SpecifiedTaxRegistration") {
                        element("ram:ID", taxNumber.value, "schemeID" to "FC")
                    }
                }
            }
            element("ram:BuyerTradeParty") {
                customer.buyerIdentifier?.let { element("ram:ID", it) }
                element("ram:Name", customer.name)
                customer.contactPoint?.let { contact ->
                    element("ram:DefinedTradeContact") { element("ram:PersonName", contact) }
                }
                address(customer.address)
                customer.vatId?.let { vatId ->
                    element("ram:SpecifiedTaxRegistration") { element("ram:ID", vatId.value, "schemeID" to "VA") }
                }
            }
            calculated.invoice.salesOrderReference?.let { reference ->
                element("ram:SellerOrderReferencedDocument") { element("ram:IssuerAssignedID", reference) }
            }
        }
    }

    private fun XmlElements.address(address: Address) {
        element("ram:PostalTradeAddress") {
            element("ram:PostcodeCode", address.postalCode)
            element("ram:LineOne", address.street)
            element("ram:CityName", address.city)
            element("ram:CountryID", address.countryCode.value)
        }
    }

    private fun XmlElements.settlement(acceptedInvoice: AcceptedInvoice) {
        val calculated = acceptedInvoice.calculated
        val deductions = deductionsOf(acceptedInvoice)
        val bank = calculated.config.bank
        val servicePeriod = calculated.invoice.servicePeriod
        element("ram:ApplicableHeaderTradeSettlement") {
            element("ram:InvoiceCurrencyCode", "EUR")
            if (calculated.requestsPayment) {
                element("ram:SpecifiedTradeSettlementPaymentMeans") {
                    // 58: SEPA-Überweisung (UNTDID 4461)
                    element("ram:TypeCode", "58")
                    element("ram:PayeePartyCreditorFinancialAccount") {
                        element("ram:IBANID", bank.iban.value)
                        element("ram:AccountName", bank.accountHolder)
                    }
                    element("ram:PayeeSpecifiedCreditorFinancialInstitution") {
                        element("ram:BICID", bank.bic.value)
                    }
                }
            }
            calculated.vatGroups.forEach { group ->
                element("ram:ApplicableTradeTax") {
                    element("ram:CalculatedAmount", amount(group.amount))
                    element("ram:TypeCode", "VAT")
                    group.exemptionReason?.let { element("ram:ExemptionReason", it) }
                    element("ram:BasisAmount", amount(group.base))
                    element("ram:CategoryCode", group.category.code)
                    receivedOn(acceptedInvoice)?.let { date("ram:TaxPointDate", it, "udt:DateString") }
                    element("ram:RateApplicablePercent", percent(group.rate))
                }
            }
            period(servicePeriod)
            calculated.dueDate?.let { dueDate ->
                element("ram:SpecifiedTradePaymentTerms") {
                    date("ram:DueDateDateTime", dueDate)
                }
            }
            // Je Skontostufe eigene Zahlungsbedingungen, weil jede nur eine
            // Stufe trägt; die Frist zählt ab dem Rechnungsdatum, wie auf dem
            // Blatt. Kein Skontobetrag, aus demselben Grund wie dort: Bei
            // mehreren Steuersätzen müsste er aufgeteilt werden.
            calculated.skonto.forEach { stufe ->
                element("ram:SpecifiedTradePaymentTerms") {
                    element("ram:ApplicableTradePaymentDiscountTerms") {
                        date("ram:BasisDateTime", calculated.invoice.issueDate)
                        element("ram:BasisPeriodMeasure", "${stufe.days}", "unitCode" to "DAY")
                        element("ram:CalculationPercent", percent(stufe.rate))
                    }
                }
            }
            element("ram:SpecifiedTradeSettlementHeaderMonetarySummation") {
                element("ram:LineTotalAmount", amount(calculated.netTotal))
                element("ram:TaxBasisTotalAmount", amount(calculated.netTotal))
                element("ram:TaxTotalAmount", amount(calculated.vatTotal), "currencyID" to "EUR")
                element("ram:GrandTotalAmount", amount(calculated.grossTotal))
                if (deductions.isNotEmpty()) {
                    element("ram:TotalPrepaidAmount", amount(calculated.prepaymentTotal))
                }
                element("ram:DuePayableAmount", amount(calculated.remainingAmount))
            }
            cancelled(acceptedInvoice)?.let { cancelled ->
                element("ram:InvoiceReferencedDocument") {
                    element("ram:IssuerAssignedID", cancelled.invoiceNumber)
                    date("ram:FormattedIssueDateTime", cancelled.issueDate, "qdt:DateTimeString")
                }
            }
            deductions.forEach { advancePayment(it) }
        }
    }

    /**
     * Ein abgesetzter Abschlag (ADR-0002), Feld für Feld wie im Block
     * `abschlaege` der Schlussrechnung (ADR-0001): gezahlter Betrag, Tag der
     * Vereinnahmung, je Steueranteil Kategorie, Satz, Netto und Steuer, und
     * die Abschlagsrechnung, auf die er zurückgeht.
     */
    private fun XmlElements.advancePayment(deduction: Deduction) {
        val prepayment = deduction.deducted.prepayment
        element("ram:SpecifiedAdvancePayment") {
            element("ram:PaidAmount", amount(deduction.deducted.gross))
            date("ram:FormattedReceivedDateTime", deduction.receivedOn, "qdt:DateTimeString")
            prepayment.taxShares.forEach { share ->
                element("ram:IncludedTradeTax") {
                    element("ram:CalculatedAmount", amount(share.vat))
                    element("ram:TypeCode", "VAT")
                    element("ram:BasisAmount", amount(share.net))
                    element("ram:CategoryCode", share.taxCategory.code)
                    element("ram:RateApplicablePercent", percent(share.vatRate))
                }
            }
            element("ram:InvoiceSpecifiedReferencedDocument") {
                element("ram:IssuerAssignedID", prepayment.invoiceNumber)
                element("ram:TypeCode", InvoiceType.PREPAYMENT.code)
                date("ram:FormattedIssueDateTime", prepayment.issueDate, "qdt:DateTimeString")
            }
        }
    }

    /** Die abgesetzten Abschläge einer Schlussrechnung, bei jeder anderen Art keine. */
    private fun deductionsOf(acceptedInvoice: AcceptedInvoice): List<Deduction> =
        when (val kind = acceptedInvoice.kind) {
            is InvoiceKind.FinalInvoice -> kind.deductions
            is InvoiceKind.Ordinary, is InvoiceKind.PrepaymentInvoice, is InvoiceKind.Cancellation -> emptyList()
        }

    /**
     * Der Tag der Vereinnahmung eines Abschlags (§14 Abs. 4 Nr. 6 UStG). Im
     * XML ist das der Steuerzeitpunkt (BT-7): Die Steuer auf eine Anzahlung
     * entsteht mit ihrem Eingang. Steht er noch nicht fest, fehlt er auch
     * hier, wie auf dem Blatt.
     */
    private fun receivedOn(acceptedInvoice: AcceptedInvoice): LocalDate? =
        when (val kind = acceptedInvoice.kind) {
            is InvoiceKind.PrepaymentInvoice -> kind.receivedOn
            is InvoiceKind.Ordinary, is InvoiceKind.FinalInvoice, is InvoiceKind.Cancellation -> null
        }

    /** Die Rechnung, die ein Storno aufhebt (BG-3, ADR-0006). */
    private fun cancelled(acceptedInvoice: AcceptedInvoice): CancelledInvoice? =
        when (val kind = acceptedInvoice.kind) {
            is InvoiceKind.Cancellation -> kind.cancelled
            is InvoiceKind.Ordinary, is InvoiceKind.PrepaymentInvoice, is InvoiceKind.FinalInvoice -> null
        }

    private fun XmlElements.period(period: ServicePeriod) {
        element("ram:BillingSpecifiedPeriod") {
            date("ram:StartDateTime", period.start)
            date("ram:EndDateTime", period.end)
        }
    }

    /**
     * Ein Datum im Format 102 der Codeliste UNTDID 2379: JJJJMMTT. Das
     * innere Element hängt am Typ, den das Schema für [name] vorsieht.
     */
    private fun XmlElements.date(
        name: String,
        date: LocalDate,
        inner: String = "udt:DateTimeString",
    ) {
        element(name) {
            element(inner, date.format(DateTimeFormatter.BASIC_ISO_DATE), "format" to "102")
        }
    }

    // toPlainString und nie toString: Das Schema verlangt eine Dezimalzahl, und
    // toString schriebe große oder kleine Werte als 1E+3.
    private fun amount(money: Money): String = money.value.toPlainString()

    private fun percent(percent: Percent): String = percent.valueAsPercentage.stripTrailingZeros().toPlainString()
}
