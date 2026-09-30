package de.fluchitconsulting.rechnungsgenerator.generate.einvoice

import de.fluchitconsulting.rechnungsgenerator.core.accepted.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.accepted.InvoiceVariableResolver
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedLine
import de.fluchitconsulting.rechnungsgenerator.core.calculation.VatGroup
import de.fluchitconsulting.rechnungsgenerator.core.model.CancelledInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Discount
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.PostalAddress
import de.fluchitconsulting.rechnungsgenerator.core.model.ServicePeriod
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.rules.Deduction
import de.fluchitconsulting.rechnungsgenerator.core.rules.InvoiceKind
import de.fluchitconsulting.rechnungsgenerator.core.values.Currency
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * CII im Profil EXTENDED (ADR-0023). Jede Zahl kommt aus der [CalculatedInvoice], damit im XML dieselbe steht wie
 * auf dem Blatt. Die Reihenfolge ist die des Schemas; was eine Art zusätzlich trägt, fragt ein `when` ohne `else`.
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
                // BT-22: Einleitung, Bemerkung und der Pflichtsatz der Art, nicht der Schlusssatz der Stammdaten, der nur eine
                // Höflichkeit ist.
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
     * BT-80 ist der Leistungsort nach BR-IC-12, nicht der Ort des Empfängers (#472): bei S, Z und E das Inland, sonst
     * der Kunde; K geht vor. BR-FX-DE-04 verlangt das Land ohne Lieferdatum, das es bei einem Zeitraum nicht gibt. Auch
     * auf der Abschlagsrechnung: Ohne Land bliebe der Block leer (PEPPOL-EN16931-R008).
     */
    private fun XmlElements.delivery(calculated: CalculatedInvoice) {
        val categories = calculated.taxCategories
        val domestic = TaxCategory.INTRA_COMMUNITY !in categories && categories.any { it.isSuppliedDomestically() }
        val address = if (domestic) calculated.config.address else calculated.invoice.customer.address
        element("ram:ApplicableHeaderTradeDelivery") {
            element("ram:ShipToTradeParty") {
                element("ram:PostalTradeAddress") {
                    element("ram:CountryID", address.countryCode.value)
                }
            }
        }
    }

    private fun TaxCategory.isSuppliedDomestically(): Boolean =
        when (this) {
            TaxCategory.STANDARD, TaxCategory.ZERO_RATED, TaxCategory.EXEMPT -> true
            TaxCategory.REVERSE_CHARGE, TaxCategory.INTRA_COMMUNITY, TaxCategory.EXPORT, TaxCategory.OUT_OF_SCOPE -> false
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
                    // Der Grund der Gruppe: EXTENDED verlangt ihn an Position und Gruppe gleich (BR-FXEXT-E-08rev).
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
     * BG-27 (ADR-0013). Ein Satz mit seiner Basis, ein Betrag allein. Nur Code 95 und kein Text: Ein Text müsste nach
     * BR-CO-07 dasselbe sagen.
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
                    // Satz und Basis kämen aus einer Rechnung, die niemand vereinbart hat.
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
                // BT-30 hat nur ein Feld, das Gericht steht deshalb in BT-33, dort auch die Geschäftsführung (§35a GmbHG).
                // Getrennt mit Semikolon, weil Namen schon Kommas tragen.
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

    private fun XmlElements.address(address: PostalAddress) {
        element("ram:PostalTradeAddress") {
            // Ohne Postleitzahl kein Element; ein leeres warnte PEPPOL-EN16931-R008 (#512).
            address.postalCode?.let { element("ram:PostcodeCode", it) }
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
        val currency = calculated.invoice.currency.name
        element("ram:ApplicableHeaderTradeSettlement") {
            // BT-6 nur mit Umrechnung, dann verlangt BR-53 die Steuer in Euro. Das Schema stellt BT-6 vor BT-5.
            calculated.vatTotalInEuro?.let { element("ram:TaxCurrencyCode", Currency.EUR.name) }
            // BT-5
            element("ram:InvoiceCurrencyCode", currency)
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
            // Je Skontostufe eigene Zahlungsbedingungen, weil jede nur eine trägt; kein Skontobetrag, wie auf dem Blatt.
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
                element("ram:TaxTotalAmount", amount(calculated.vatTotal), "currencyID" to currency)
                // BT-111. Mustang rechnet sie nicht nach, das hält die Vergleichsdatei.
                calculated.vatTotalInEuro?.let { element("ram:TaxTotalAmount", amount(it), "currencyID" to Currency.EUR.name) }
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

    /** Ohne Netto des Anteils: EXTENDED hat in BG-X-46 kein Feld dafür, und das Schematron meldet es (#453). */
    private fun XmlElements.advancePayment(deduction: Deduction) {
        val prepayment = deduction.deducted.prepayment
        element("ram:SpecifiedAdvancePayment") {
            element("ram:PaidAmount", amount(deduction.deducted.gross))
            date("ram:FormattedReceivedDateTime", deduction.receivedOn, "qdt:DateTimeString")
            prepayment.taxShares.forEach { share ->
                element("ram:IncludedTradeTax") {
                    element("ram:CalculatedAmount", amount(share.vat))
                    element("ram:TypeCode", "VAT")
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

    private fun deductionsOf(acceptedInvoice: AcceptedInvoice): List<Deduction> =
        when (val kind = acceptedInvoice.kind) {
            is InvoiceKind.FinalInvoice -> kind.deductions
            is InvoiceKind.Ordinary, is InvoiceKind.PrepaymentInvoice, is InvoiceKind.Cancellation -> emptyList()
        }

    /** BT-7: Die Steuer auf eine Anzahlung entsteht mit ihrem Eingang. Steht er nicht fest, fehlt er, wie auf dem Blatt. */
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

    /** Format 102 (JJJJMMTT); das innere Element hängt am Typ, den das Schema für [name] vorsieht. */
    private fun XmlElements.date(
        name: String,
        date: LocalDate,
        inner: String = "udt:DateTimeString",
    ) {
        element(name) {
            element(inner, date.format(DateTimeFormatter.BASIC_ISO_DATE), "format" to "102")
        }
    }

    // toPlainString, nie toString: Das Schema verlangt eine Dezimalzahl und kein 1E+3.
    private fun amount(money: Money): String = money.value.toPlainString()

    private fun percent(percent: Percent): String = percent.valueAsPercentage.stripTrailingZeros().toPlainString()
}
