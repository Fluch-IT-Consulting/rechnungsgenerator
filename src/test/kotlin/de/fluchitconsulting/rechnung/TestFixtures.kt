package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.model.Address
import de.fluchitconsulting.rechnung.model.BankDetails
import de.fluchitconsulting.rechnung.model.CancelledInvoice
import de.fluchitconsulting.rechnung.model.Customer
import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.model.LineItem
import de.fluchitconsulting.rechnung.model.Prepayment
import de.fluchitconsulting.rechnung.model.SellerConfig
import de.fluchitconsulting.rechnung.model.ServicePeriod
import de.fluchitconsulting.rechnung.model.TaxShare
import de.fluchitconsulting.rechnung.values.Bic
import de.fluchitconsulting.rechnung.values.Iban
import de.fluchitconsulting.rechnung.values.InvoiceNumberPattern
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
) = Invoice(
    number = number,
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
    cancelled = cancelled,
    paymentTermDaysOverride = paymentTermDaysOverride,
)

// Wie die Regeltests prüfen, seit der Calculator sammelt statt zu werfen (#49):
// nach der Kennung der eigenen Regel, nicht nach dem Wortlaut irgendeiner
// Meldung. Andere Befunde stören den Test damit nicht mehr – vorher musste jede
// Testrechnung alle übrigen Regeln bestehen und durfte keinen Hinweis auslösen.

fun CalculatedInvoice.aborts(): List<Finding> = findings.filter { it.severity == Severity.ABORT }

fun CalculatedInvoice.hints(): List<Finding> = findings.filter { it.severity == Severity.HINT }

fun List<Finding>.rules(): List<Rule> = map { it.rule }

/** Die Zeile zu einer Regel, so wie ein Mensch sie zu sehen bekommt. */
fun List<Finding>.messageOf(rule: Rule): String =
    firstOrNull { it.rule == rule }?.describe()
        ?: error("Kein Befund zu $rule. Gefunden: ${rules()}")

fun CalculatedInvoice.rules(): List<Rule> = findings.rules()

fun CalculatedInvoice.messageOf(rule: Rule): String = findings.messageOf(rule)

/**
 * Die angenommene Rechnung für Tests, die setzen oder einen Abschlag übernehmen
 * (ADR-0007). Hat die Testrechnung einen Abbruch, scheitert der Test hier – mit
 * den Kennungen, statt später an einer Stelle, die davon nichts weiß.
 */
fun CalculatedInvoice.accepted(): AcceptedInvoice =
    when (val checked = AcceptedInvoice.check(this)) {
        is Checked.Accepted -> checked.invoice
        is Checked.Rejected -> error("Die Testrechnung hat Abbrüche: ${checked.aborts.rules()}")
    }
