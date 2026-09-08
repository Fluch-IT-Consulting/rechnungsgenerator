package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.model.Address
import de.fluchitconsulting.rechnung.model.BankDetails
import de.fluchitconsulting.rechnung.model.Customer
import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.model.LineItem
import de.fluchitconsulting.rechnung.model.Prepayment
import de.fluchitconsulting.rechnung.model.SellerConfig
import de.fluchitconsulting.rechnung.model.ServicePeriod
import de.fluchitconsulting.rechnung.model.TaxShare
import de.fluchitconsulting.rechnung.values.Bic
import de.fluchitconsulting.rechnung.values.Iban
import de.fluchitconsulting.rechnung.values.InvoiceType
import de.fluchitconsulting.rechnung.values.Money
import de.fluchitconsulting.rechnung.values.Percent
import de.fluchitconsulting.rechnung.values.Price
import de.fluchitconsulting.rechnung.values.TaxCategory
import de.fluchitconsulting.rechnung.values.VatId
import java.math.BigDecimal
import java.time.LocalDate

// Baukasten für Tests: Top-Level-Funktionen mit Default-Argumenten, sodass jeder
// Test nur die Werte angibt, um die es ihm geht ("Object Mother"-Muster).

fun testSellerConfig(paymentTermDays: Int = 14) =
    SellerConfig(
        companyName = "Muster IT-Consulting",
        ownerName = "Dr. Erika Musterfrau",
        address = Address(street = "Beispielweg 42", postalCode = "97070", city = "Würzburg"),
        email = "rechnung@muster-it-consulting.example",
        vatId = VatId("DE123456788"),
        bank =
            BankDetails(
                accountHolder = "Dr. Erika Musterfrau",
                iban = Iban("DE89 3704 0044 0532 0130 00"),
                bic = Bic("MUSTDEFFXXX"),
                bankName = "Musterbank",
            ),
        paymentTermDays = paymentTermDays,
    )

fun testItem(
    quantity: String = "1",
    unitPrice: String = "100.00",
    vatRate: String = "19",
    description: String? = null,
    note: String? = null,
    taxCategory: TaxCategory = TaxCategory.STANDARD,
    taxExemptionReason: String? = null,
) = LineItem(
    name = "Testposition",
    description = description,
    note = note,
    quantity = BigDecimal(quantity),
    unitPrice = Price(unitPrice),
    vatRate = Percent(vatRate),
    taxCategory = taxCategory,
    taxExemptionReason = taxExemptionReason,
)

fun testPrepayment(
    invoiceNumber: String = "R26-009",
    issueDate: LocalDate = LocalDate.of(2026, 6, 15),
    receivedOn: LocalDate? = LocalDate.of(2026, 6, 10),
    net: String = "4000.00",
    vat: String = "760.00",
    vatRate: String = "19",
    taxCategory: TaxCategory = TaxCategory.STANDARD,
    taxShares: List<TaxShare>? = null,
) = Prepayment(
    invoiceNumber = invoiceNumber,
    issueDate = issueDate,
    receivedOn = receivedOn,
    taxShares =
        taxShares
            ?: listOf(
                TaxShare(taxCategory = taxCategory, vatRate = Percent(vatRate), net = Money(net), vat = Money(vat)),
            ),
)

fun testInvoice(
    items: List<LineItem> = listOf(testItem()),
    paymentTermDaysOverride: Int? = null,
    customerVatId: VatId? = null,
    type: InvoiceType = InvoiceType.INVOICE,
    receivedOn: LocalDate? = null,
    prepayments: List<Prepayment> = emptyList(),
) = Invoice(
    number = "R26-001",
    issueDate = LocalDate.of(2026, 8, 14),
    type = type,
    receivedOn = receivedOn,
    servicePeriod = ServicePeriod(start = LocalDate.of(2026, 7, 1), end = LocalDate.of(2026, 7, 31)),
    customer =
        Customer(
            name = "Beispiel Software GmbH",
            address = Address(street = "Industriestraße 1", postalCode = "90402", city = "Nürnberg"),
            vatId = customerVatId,
        ),
    items = items,
    prepayments = prepayments,
    paymentTermDaysOverride = paymentTermDaysOverride,
)
