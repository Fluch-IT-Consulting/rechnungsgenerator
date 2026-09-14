package de.fluchitconsulting.rechnungsgenerator.model

import de.fluchitconsulting.rechnungsgenerator.calculation.InvoiceCalculator
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.values.Money
import de.fluchitconsulting.rechnungsgenerator.values.Price
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

// Testressourcen liegen in src/test/resources und landen im Classpath.
private fun resource(name: String): String = checkNotNull(YamlLoaderTest::class.java.getResource("/$name")).readText()

class YamlLoaderTest : FunSpec({

    test("liest die Stammdaten aus YAML") {
        val config = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))

        config.companyName shouldBe "Muster IT-Consulting UG (haftungsbeschränkt)"
        config.vatId.value shouldBe "DE123456788"
        config.address.postalCode shouldBe "97070"
        config.paymentTermDays shouldBe 14
        // Das Muster der Rechnungsnummern (ADR-0005): geprüft wird die Nummer
        // erst im Calculator, gelesen wird sie hier.
        checkNotNull(config.invoiceNumberPattern).matches("R26-042") shouldBe true
    }

    test("liest die Rechnung und rechnet End-to-End") {
        val config = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))
        val invoice = YamlLoader.parseInvoice(resource("rechnung.yaml"))

        invoice.number shouldBe "R26-042"
        invoice.items.size shouldBe 2
        invoice.items.first().unitPrice shouldBe Price("95.00")
        invoice.items.first().name shouldBe "IT-Beratungsleistungen"
        invoice.items.first().description shouldBe "Rahmenvertrag vom 01.02.2026"
        // Nicht gesetzte Zusatzfelder bleiben null, statt zu einem leeren String zu werden.
        invoice.items.first().note shouldBe null

        val totals = InvoiceCalculator.calculate(invoice, config)

        // 86.5 × 95.00 = 8217.50, plus 120.00 Pauschale = 8337.50 netto;
        // 19 % darauf = 1584.125 -> 1584.13; brutto 9921.63.
        totals.netTotal shouldBe Money("8337.50")
        totals.vatGroups.single().amount shouldBe Money("1584.13")
        totals.grossTotal shouldBe Money("9921.63")
    }

    test("unbekannte YAML-Schlüssel werden abgelehnt") {
        // Strikter Modus: Tippfehler wie "nummmer" sollen laut scheitern.
        shouldThrow<Exception> {
            YamlLoader.parseInvoice(resource("rechnung.yaml").replace("nummer:", "nummmer:"))
        }
    }

    test("eine Rechnungsdatei ohne art ist eine gewöhnliche Rechnung") {
        // Der negative Test dieses Schritts: Bestehende Dateien kennen den
        // Schlüssel nicht und sollen sich trotzdem verhalten wie bisher.
        YamlLoader.parseInvoice(resource("rechnung.yaml")).type shouldBe InvoiceType.INVOICE
    }

    test("art wählt die Rechnungsart") {
        val yaml = resource("rechnung.yaml").replace("nummer:", "art: abschlagsrechnung\nnummer:")

        YamlLoader.parseInvoice(yaml).type shouldBe InvoiceType.PREPAYMENT
    }

    test("eine unbekannte art wird abgelehnt") {
        shouldThrow<Exception> {
            YamlLoader.parseInvoice(resource("rechnung.yaml").replace("nummer:", "art: anzahlung\nnummer:"))
        }
    }
    test("die Abschlagsrechnung bringt Art und Vereinnahmung mit") {
        val invoice = YamlLoader.parseInvoice(resource("abschlagsrechnung.yaml"))

        invoice.type shouldBe InvoiceType.PREPAYMENT
        invoice.receivedOn shouldBe LocalDate.of(2026, 6, 10)
    }
    test("die Schlussrechnung bringt ihre Abschläge mit und rechnet den Restbetrag") {
        val config = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))
        val invoice = YamlLoader.parseInvoice(resource("schlussrechnung.yaml"))

        invoice.type shouldBe InvoiceType.FINAL
        invoice.prepayments.map { it.invoiceNumber } shouldBe listOf("R26-009", "R26-011")
        invoice.prepayments
            .first()
            .taxShares
            .single()
            .vat shouldBe Money("760.00")

        val totals = InvoiceCalculator.calculate(invoice, config)

        // 20.000 netto + 19 % = 23.800 brutto, abzüglich zweimal 4.760.
        totals.grossTotal shouldBe Money("23800.00")
        totals.prepaymentTotal shouldBe Money("9520.00")
        totals.remainingAmount shouldBe Money("14280.00")
    }

    test("eine unlesbare Datei nennt die Datei und den Grund") {
        // Clikt prüft mit mustExist nur, dass es die Datei gibt. Ob sie lesbar
        // ist, zeigt sich erst hier. (Als root ist auch chmod 000 lesbar,
        // dieser Test schlüge dort fehl.)
        val unlesbar = createTempDirectory("loader-test").resolve("unlesbar.yaml")
        unlesbar.writeText(resource("rechnung.yaml"))
        unlesbar.toFile().setReadable(false)

        val failure = shouldThrow<InputFileException> { YamlLoader.loadInvoice(unlesbar) }

        failure.message!! shouldContain unlesbar.toString()
        failure.message!! shouldContain "keine Leseberechtigung"
    }

    test("ein Wert, der seine Prüfung nicht besteht, nennt die Datei") {
        // Die Meldung des Wertobjekts nennt die IBAN, aber nicht, in welcher
        // Datei sie steht – bei zwei Eingabedateien ist das die halbe Auskunft.
        val datei = createTempDirectory("loader-test").resolve("stammdaten.yaml")
        datei.writeText(resource("stammdaten.yaml").replace("0532 0130 00", "0532 0130 01"))

        val failure = shouldThrow<InputFileException> { YamlLoader.loadSellerConfig(datei) }

        failure.message!! shouldContain datei.toString()
        failure.message!! shouldContain "Prüfsumme"
    }

    test("ein Muster, das kein regulärer Ausdruck ist, nennt die Datei") {
        // Der Tippfehler {2-3} statt {2,3} fällt beim Laden auf, nicht erst bei
        // der ersten Rechnung, die daran scheitert.
        val datei = createTempDirectory("loader-test").resolve("stammdaten.yaml")
        datei.writeText(resource("stammdaten.yaml").replace("R\\d{2}-\\d{3}", "R\\d{2-3}"))

        val failure = shouldThrow<InputFileException> { YamlLoader.loadSellerConfig(datei) }

        failure.message!! shouldContain datei.toString()
        failure.message!! shouldContain "Kein gültiger regulärer Ausdruck"
    }

    test("ein YAML-Fehler nennt die Datei") {
        val datei = createTempDirectory("loader-test").resolve("kaputt.yaml")
        datei.writeText("nummer: R26-001\ngibtsnicht: 1\n")

        val failure = shouldThrow<InputFileException> { YamlLoader.loadInvoice(datei) }

        failure.message!! shouldContain datei.toString()
        failure.message!! shouldContain "gibtsnicht"
    }
})
