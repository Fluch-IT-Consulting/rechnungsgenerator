package de.fluchitconsulting.rechnungsgenerator.generate.einvoice

import de.fluchitconsulting.rechnungsgenerator.core.accepted.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedLine
import de.fluchitconsulting.rechnungsgenerator.core.calculation.VatGroup
import de.fluchitconsulting.rechnungsgenerator.core.model.Address
import de.fluchitconsulting.rechnungsgenerator.core.model.ServicePeriod
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.rules.Finding
import de.fluchitconsulting.rechnungsgenerator.core.rules.InvoiceKind
import de.fluchitconsulting.rechnungsgenerator.core.rules.Rule
import de.fluchitconsulting.rechnungsgenerator.core.rules.Subject
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
 */
object CiiWriter {
    private const val EXTENDED = "urn:cen.eu:en16931:2017#conformant#urn:factur-x.eu:1p0:extended"

    private val namespaces =
        mapOf(
            "rsm" to "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100",
            "ram" to "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100",
            "udt" to "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100",
        )

    fun write(acceptedInvoice: AcceptedInvoice): CiiResult {
        val missing = notYetPossible(acceptedInvoice)
        return if (missing.isEmpty()) {
            CiiResult.Written(xml(acceptedInvoice.calculated))
        } else {
            CiiResult.NotYetPossible(missing)
        }
    }

    /**
     * Was diese Rechnung verlangt und der Writer noch nicht schreibt.
     *
     * Das `when` über die Rechnungsart hat kein `else`, wie im Renderer: Eine
     * neue Art muss hier entscheiden, ob der Writer sie kann (ADR-0008).
     */
    private fun notYetPossible(acceptedInvoice: AcceptedInvoice): List<Finding> {
        val calculated = acceptedInvoice.calculated
        val invoice = calculated.invoice
        return buildList {
            when (acceptedInvoice.kind) {
                is InvoiceKind.Ordinary -> {
                    Unit
                }
                is InvoiceKind.PrepaymentInvoice, is InvoiceKind.FinalInvoice, is InvoiceKind.Cancellation -> {
                    add(notYet(Subject.WholeInvoice, "Eine ${invoice.type.documentName} lässt sich noch nicht als ZUGFeRD erzeugen."))
                }
            }
            calculated.lines.forEachIndexed { index, line ->
                val subject = Subject.Item(index, line.item.name)
                if (line.item.discount != null) {
                    add(notYet(subject, "Ein Rabatt lässt sich noch nicht als ZUGFeRD erzeugen."))
                }
            }
            if (calculated.skonto.isNotEmpty()) {
                add(notYet(Subject.WholeInvoice, "Ein Skonto lässt sich noch nicht als ZUGFeRD erzeugen."))
            }
        }
    }

    private fun notYet(
        subject: Subject,
        message: String,
    ) = Finding(Rule.ZUGFERD_NOCH_NICHT_MOEGLICH, subject, message)

    private fun xml(calculated: CalculatedInvoice): String =
        indentedXml("rsm:CrossIndustryInvoice", namespaces) {
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
                listOfNotNull(calculated.invoice.introduction, calculated.invoice.note).forEach { note ->
                    element("ram:IncludedNote") { element("ram:Content", note) }
                }
            }
            element("rsm:SupplyChainTradeTransaction") {
                calculated.lines.forEachIndexed { index, line -> lineItem(index, line, calculated.vatGroupOf(line)) }
                agreement(calculated)
                delivery(calculated)
                settlement(calculated)
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
                element("ram:SpecifiedTradeSettlementLineMonetarySummation") {
                    element("ram:LineTotalAmount", amount(line.net))
                }
            }
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
                // deshalb in BT-33 und so wie im Fuß des Blatts.
                config.registerCourt?.let { element("ram:Description", "Registergericht $it") }
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

    private fun XmlElements.settlement(calculated: CalculatedInvoice) {
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
                    element("ram:RateApplicablePercent", percent(group.rate))
                }
            }
            period(servicePeriod)
            calculated.dueDate?.let { dueDate ->
                element("ram:SpecifiedTradePaymentTerms") {
                    date("ram:DueDateDateTime", dueDate)
                }
            }
            element("ram:SpecifiedTradeSettlementHeaderMonetarySummation") {
                element("ram:LineTotalAmount", amount(calculated.netTotal))
                element("ram:TaxBasisTotalAmount", amount(calculated.netTotal))
                element("ram:TaxTotalAmount", amount(calculated.vatTotal), "currencyID" to "EUR")
                element("ram:GrandTotalAmount", amount(calculated.grossTotal))
                element("ram:DuePayableAmount", amount(calculated.remainingAmount))
            }
        }
    }

    private fun XmlElements.period(period: ServicePeriod) {
        element("ram:BillingSpecifiedPeriod") {
            date("ram:StartDateTime", period.start)
            date("ram:EndDateTime", period.end)
        }
    }

    /** Ein Datum im Format 102 der Codeliste UNTDID 2379: JJJJMMTT. */
    private fun XmlElements.date(
        name: String,
        date: LocalDate,
    ) {
        element(name) {
            element("udt:DateTimeString", date.format(DateTimeFormatter.BASIC_ISO_DATE), "format" to "102")
        }
    }

    // toPlainString und nie toString: Das Schema verlangt eine Dezimalzahl, und
    // toString schriebe große oder kleine Werte als 1E+3.
    private fun amount(money: Money): String = money.value.toPlainString()

    private fun percent(percent: Percent): String = percent.valueAsPercentage.stripTrailingZeros().toPlainString()
}
