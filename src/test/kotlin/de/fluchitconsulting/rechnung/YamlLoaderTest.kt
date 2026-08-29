package de.fluchitconsulting.rechnung

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

// Testressourcen liegen in src/test/resources und landen im Classpath.
private fun resource(name: String): String = checkNotNull(YamlLoaderTest::class.java.getResource("/$name")).readText()

class YamlLoaderTest : FunSpec({

    test("liest die Stammdaten aus YAML") {
        val config = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))

        config.companyName shouldBe "Muster IT-Consulting"
        config.vatId shouldBe "DE123456789"
        config.address.postalCode shouldBe "97070"
        config.paymentTermDays shouldBe 14
    }

    test("liest die Rechnung und rechnet End-to-End") {
        val config = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))
        val invoice = YamlLoader.parseInvoice(resource("rechnung.yaml"))

        invoice.number shouldBe "R26-042"
        invoice.items.size shouldBe 2
        invoice.items.first().unitPrice shouldBe BigDecimal("95.00")

        val totals = InvoiceCalculator.calculate(invoice, config)

        // 86.5 × 95.00 = 8217.50, plus 120.00 Pauschale = 8337.50 netto;
        // 19 % darauf = 1584.125 -> 1584.13; brutto 9921.63.
        totals.netTotal shouldBe BigDecimal("8337.50")
        totals.vatGroups.single().amount shouldBe BigDecimal("1584.13")
        totals.grossTotal shouldBe BigDecimal("9921.63")
    }

    test("unbekannte YAML-Schlüssel werden abgelehnt") {
        // Strikter Modus: Tippfehler wie "nummmer" sollen laut scheitern.
        shouldThrow<Exception> {
            YamlLoader.parseInvoice(resource("rechnung.yaml").replace("nummer:", "nummmer:"))
        }
    }
})
