package de.fluchitconsulting.rechnung

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class YamlLoaderTest {
    // Testressourcen liegen in src/test/resources und landen im Classpath.
    private fun resource(name: String): String = checkNotNull(javaClass.getResource("/$name")).readText()

    @Test
    fun `parses seller config from yaml`() {
        val config = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))

        assertEquals("Muster IT-Consulting", config.companyName)
        assertEquals("DE123456789", config.vatId)
        assertEquals("97070", config.address.postalCode)
        assertEquals(14, config.paymentTermDays)
    }

    @Test
    fun `parses invoice and calculates end-to-end totals`() {
        val config = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))
        val invoice = YamlLoader.parseInvoice(resource("rechnung.yaml"))

        assertEquals("R26-042", invoice.number)
        assertEquals(2, invoice.items.size)
        assertEquals(BigDecimal("95.00"), invoice.items.first().unitPrice)

        val totals = InvoiceCalculator.calculate(invoice, config)

        // 86.5 × 95.00 = 8217.50, plus 120.00 Pauschale = 8337.50 netto;
        // 19 % darauf = 1584.125 → 1584.13; brutto 9921.63.
        assertEquals(BigDecimal("8337.50"), totals.netTotal)
        assertEquals(BigDecimal("1584.13"), totals.vatGroups.single().amount)
        assertEquals(BigDecimal("9921.63"), totals.grossTotal)
    }

    @Test
    fun `unknown yaml keys are rejected`() {
        // Strikter Modus: Tippfehler wie "nummmer" sollen laut scheitern.
        assertFailsWith<Exception> {
            YamlLoader.parseInvoice(resource("rechnung.yaml").replace("nummer:", "nummmer:"))
        }
    }
}
