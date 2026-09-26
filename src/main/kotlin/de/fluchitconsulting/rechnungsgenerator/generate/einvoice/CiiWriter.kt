package de.fluchitconsulting.rechnungsgenerator.generate.einvoice

import de.fluchitconsulting.rechnungsgenerator.core.accepted.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedLine
import de.fluchitconsulting.rechnungsgenerator.core.model.Address
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
            }
            element("rsm:SupplyChainTradeTransaction") {
                calculated.lines.forEachIndexed { index, line -> lineItem(index, line) }
                agreement(calculated)
                // Leer, weil hier nur ein Lieferdatum (BT-72) stünde, und bei
                // einem Leistungszeitraum wäre jedes Datum erfunden. Der Zeitraum
                // steht in der Abrechnung (BG-14). Das Schematron warnt deshalb
                // mit PEPPOL-EN16931-R008, das Schema verlangt das Element.
                empty("ram:ApplicableHeaderTradeDelivery")
                settlement(calculated)
            }
        }
    }

    private fun XmlElements.lineItem(
        index: Int,
        line: CalculatedLine,
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
                    element("ram:CategoryCode", item.taxCategory.code)
                    element("ram:RateApplicablePercent", percent(item.vatRate))
                }
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
                element("ram:Name", customer.name)
                address(customer.address)
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
                    element("ram:BasisAmount", amount(group.base))
                    element("ram:CategoryCode", group.category.code)
                    element("ram:RateApplicablePercent", percent(group.rate))
                }
            }
            element("ram:BillingSpecifiedPeriod") {
                date("ram:StartDateTime", servicePeriod.start)
                date("ram:EndDateTime", servicePeriod.end)
            }
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
