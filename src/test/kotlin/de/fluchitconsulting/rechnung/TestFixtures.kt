package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.model.Address
import de.fluchitconsulting.rechnung.model.BankDetails
import de.fluchitconsulting.rechnung.model.Customer
import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.model.LineItem
import de.fluchitconsulting.rechnung.model.SellerConfig
import de.fluchitconsulting.rechnung.model.ServicePeriod
import de.fluchitconsulting.rechnung.values.Percent
import de.fluchitconsulting.rechnung.values.Price
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
        vatId = "DE123456789",
        bank =
            BankDetails(
                accountHolder = "Dr. Erika Musterfrau",
                iban = "DE89 3704 0044 0532 0130 00",
                bic = "MUSTDEFFXXX",
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
) = LineItem(
    name = "Testposition",
    description = description,
    note = note,
    quantity = BigDecimal(quantity),
    unitPrice = Price(unitPrice),
    vatRate = Percent(vatRate),
)

fun testInvoice(
    items: List<LineItem> = listOf(testItem()),
    paymentTermDaysOverride: Int? = null,
) = Invoice(
    number = "R26-001",
    issueDate = LocalDate.of(2026, 8, 14),
    servicePeriod = ServicePeriod(start = LocalDate.of(2026, 7, 1), end = LocalDate.of(2026, 7, 31)),
    customer =
        Customer(
            name = "Beispiel Software GmbH",
            address = Address(street = "Industriestraße 1", postalCode = "90402", city = "Nürnberg"),
        ),
    items = items,
    paymentTermDaysOverride = paymentTermDaysOverride,
)
