package de.fluchitconsulting.rechnungsgenerator.model

import de.fluchitconsulting.rechnungsgenerator.values.Bic
import de.fluchitconsulting.rechnungsgenerator.values.Iban
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceNumberPattern
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.values.Money
import de.fluchitconsulting.rechnungsgenerator.values.Percent
import de.fluchitconsulting.rechnungsgenerator.values.Price
import de.fluchitconsulting.rechnungsgenerator.values.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.values.VatId
import java.math.BigDecimal
import java.time.LocalDate

// Baukasten für Tests: Top-Level-Funktionen mit Default-Argumenten, sodass jeder
// Test nur die Werte angibt, um die es ihm geht ("Object Mother"-Muster).

fun testSellerConfig(paymentTermDays: Int = 14, invoiceNumberPattern: InvoiceNumberPattern? = null) =
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
        invoiceNumberPattern = invoiceNumberPattern,
    )

fun testItem(
    quantity: String = "1",
    unitPrice: String = "100.00",
    vatRate: String = "19",
    description: String? = null,
    note: String? = null,
    taxCategory: TaxCategory = TaxCategory.STANDARD,
    taxExemptionReason: String? = null,
    discount: String? = null,
) = LineItem(
    name = "Testposition",
    description = description,
    note = note,
    quantity = BigDecimal(quantity),
    unitPrice = Price(unitPrice),
    vatRate = Percent(vatRate),
    taxCategory = taxCategory,
    taxExemptionReason = taxExemptionReason,
    discountRate = discount?.let { Percent(it) },
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

fun testCancelled(
    invoiceNumber: String = "R26-042",
    issueDate: LocalDate = LocalDate.of(2026, 7, 15),
) = CancelledInvoice(invoiceNumber = invoiceNumber, issueDate = issueDate)

fun testInvoice(
    number: String = "R26-001",
    items: List<LineItem> = listOf(testItem()),
    paymentTermDaysOverride: Int? = null,
    customerVatId: VatId? = null,
    type: InvoiceType = InvoiceType.INVOICE,
    receivedOn: LocalDate? = null,
    prepayments: List<Prepayment> = emptyList(),
    cancelled: CancelledInvoice? = null,
    skonto: List<SkontoTier> = emptyList(),
    contactPoint: String? = null,
    buyerIdentifier: String? = null,
    salesOrderReference: String? = null,
    introduction: String? = null,
) = Invoice(
    number = number,
    issueDate = LocalDate.of(2026, 8, 14),
    type = type,
    receivedOn = receivedOn,
    servicePeriod = ServicePeriod(start = LocalDate.of(2026, 7, 1), end = LocalDate.of(2026, 7, 31)),
    customer =
        Customer(
            name = "Beispiel Software GmbH",
            contactPoint = contactPoint,
            buyerIdentifier = buyerIdentifier,
            address = Address(street = "Industriestraße 1", postalCode = "90402", city = "Nürnberg"),
            vatId = customerVatId,
        ),
    items = items,
    prepayments = prepayments,
    cancelled = cancelled,
    paymentTermDaysOverride = paymentTermDaysOverride,
    skonto = skonto,
    salesOrderReference = salesOrderReference,
    introduction = introduction,
)

/**
 * Die Beispielrechnung je Rechnungsart, als Name ohne Endung: `rechnung` steht
 * für `rechnung.yaml` unter `src/test/resources` und für die Dateien, die davon
 * abgeleitet sind. Einmal hier, damit das Pflichtangaben-Netz und die
 * Inhalts-Dateien des Renderers dieselben Beispiele meinen (#204). Dass keine
 * Art fehlt, prüft `InvoiceRendererTest`, der auch ohne die Werkzeuge läuft.
 */
val exampleInvoices =
    mapOf(
        InvoiceType.INVOICE to "rechnung",
        InvoiceType.PARTIAL to "teilrechnung",
        InvoiceType.PREPAYMENT to "abschlagsrechnung",
        InvoiceType.FINAL to "schlussrechnung",
        InvoiceType.CANCELLATION to "stornorechnung",
    )
