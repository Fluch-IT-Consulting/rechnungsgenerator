package de.fluchitconsulting.rechnungsgenerator.generate.einvoice

import de.fluchitconsulting.rechnungsgenerator.core.accepted.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.accepted.accepted
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.ServicePeriod
import de.fluchitconsulting.rechnungsgenerator.core.model.SkontoTier
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.core.model.exampleInvoices
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testItem
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.rules.Rule
import de.fluchitconsulting.rechnungsgenerator.core.rules.Subject
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent
import de.fluchitconsulting.rechnungsgenerator.core.values.VatId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.mustangproject.validator.ZUGFeRDValidator
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.readText

private fun resource(name: String): String = Path.of("src/test/resources/$name").readText()

private fun example(name: String): AcceptedInvoice {
    val seller = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))
    val invoice = YamlLoader.parseInvoice(resource("$name.yaml"))
    return CalculatedInvoice(invoice, seller).accepted()
}

private fun AcceptedInvoice.xml(): String = CiiWriter.write(this).shouldBeInstanceOf<CiiResult.Written>().xml

/**
 * Das XML einer Rechnung, zeichengleich und geprüft – und was der Writer noch
 * nicht schreibt.
 *
 * Die Vergleichsdatei ist wie `rechnung-erwartet.tex` eine Frage und keine
 * Aufgabe: Wird der Test rot, erst nachsehen, ob die Änderung gewollt ist.
 * Der Vergleich allein sagt aber nicht, ob das XML gilt – deshalb prüft
 * Mustang es daneben, so wie ihn auch `erzeuge --zugferd` prüft (ADR-0023).
 */
class CiiWriterTest : FunSpec({

    test("die Beispielrechnung ergibt das erwartete XML") {
        example("rechnung").xml() shouldBe resource("rechnung-erwartet.xml")
    }

    /**
     * Gültig heißt für Mustang auch "gültig mit Warnungen". Erlaubt ist genau
     * eine, die zum leeren `ApplicableHeaderTradeDelivery` (siehe `CiiWriter`);
     * jede weitere wäre ein Befund, der sonst unbemerkt durchginge.
     */
    test("das XML ist gültig, mit keiner Warnung außer PEPPOL-EN16931-R008") {
        example("rechnung").xml().shouldBeValidExtended()
    }

    // Nicht teilrechnung.yaml: Die trägt Rabatte, und die gehen noch nicht.
    test("die Teilrechnung trägt den Code 326 und ist gültig") {
        val xml = CalculatedInvoice(testInvoice(type = InvoiceType.PARTIAL), testSellerConfig()).accepted().xml()

        xml shouldContain "<ram:TypeCode>326</ram:TypeCode>"
        xml.shouldBeValidExtended()
    }

    test("die freiwilligen Angaben stehen im XML, und es bleibt gültig") {
        val item =
            testItem(note = "Nachtrag").copy(
                servicePeriod = ServicePeriod(start = LocalDate.of(2026, 7, 6), end = LocalDate.of(2026, 7, 10)),
            )
        val invoice =
            testInvoice(
                items = listOf(item),
                customerVatId = VatId("DE987654328"),
                contactPoint = "Petra Beispiel",
                buyerIdentifier = "K-4711",
                salesOrderReference = "A-2026-17",
                introduction = "Für Projekt Nord berechnen wir:",
            ).copy(note = "Bitte die Rechnungsnummer angeben.")
        val config =
            testSellerConfig().copy(phone = "+49 931 1234567", registerCourt = "Würzburg", registerNumber = "HRB 12345")

        val xml = CalculatedInvoice(invoice, config).accepted().xml()

        listOf(
            "<ram:Content>Für Projekt Nord berechnen wir:</ram:Content>",
            "<ram:Content>Bitte die Rechnungsnummer angeben.</ram:Content>",
            "<udt:DateTimeString format=\"102\">20260706</udt:DateTimeString>",
            "<ram:Description>Registergericht Würzburg</ram:Description>",
            "<ram:ID>HRB 12345</ram:ID>",
            "<ram:CompleteNumber>+49 931 1234567</ram:CompleteNumber>",
            "<ram:ID>K-4711</ram:ID>",
            "<ram:PersonName>Petra Beispiel</ram:PersonName>",
            "<ram:ID schemeID=\"VA\">DE987654328</ram:ID>",
            "<ram:IssuerAssignedID>A-2026-17</ram:IssuerAssignedID>",
        ).forEach { xml shouldContain it }
        xml.shouldBeValidExtended()
    }

    test("ohne Registerangaben steht nichts davon im XML") {
        val xml = CalculatedInvoice(testInvoice(), testSellerConfig()).accepted().xml()

        xml shouldNotContain "Registergericht"
        xml shouldNotContain "ram:SpecifiedLegalOrganization"
    }

    context("was noch nicht geht, bricht ab statt still zu fehlen") {
        listOf(InvoiceType.PREPAYMENT, InvoiceType.FINAL, InvoiceType.CANCELLATION).forEach { type ->
            test("eine ${type.documentName}") {
                val result = CiiWriter.write(example(exampleInvoices.getValue(type)))

                val findings = result.shouldBeInstanceOf<CiiResult.NotYetPossible>().findings
                findings.map { it.rule to it.subject } shouldBe listOf(Rule.ZUGFERD_NOCH_NICHT_MOEGLICH to Subject.WholeInvoice)
            }
        }

        test("Rabatt, eine andere Steuerkategorie als S und Skonto, alle auf einmal") {
            val invoice =
                testInvoice(
                    items =
                        listOf(
                            testItem(discount = "10"),
                            testItem(
                                vatRate = "0",
                                taxCategory = TaxCategory.EXEMPT,
                                taxExemptionReason = "Steuerfrei nach §4 Nr. 21 UStG",
                            ),
                        ),
                    skonto = listOf(SkontoTier(Percent("2"), 7)),
                )
            val result = CiiWriter.write(CalculatedInvoice(invoice, testSellerConfig()).accepted())

            val findings = result.shouldBeInstanceOf<CiiResult.NotYetPossible>().findings
            findings.map { it.rule }.toSet() shouldBe setOf(Rule.ZUGFERD_NOCH_NICHT_MOEGLICH)
            findings.map { it.subject } shouldBe
                listOf(
                    Subject.Item(0, "Testposition"),
                    Subject.Item(1, "Testposition"),
                    Subject.WholeInvoice,
                )
        }
    }
})

private fun String.shouldBeValidExtended() {
    val validator = ZUGFeRDValidator()
    val report = validator.validate(toByteArray(), "factur-x.xml")

    withClue(report) {
        validator.wasCompletelyValid() shouldBe true
        warningIds(report) shouldBe listOf("PEPPOL-EN16931-R008")
    }
}

/** Die Kennungen der Warnungen im Bericht, etwa `[ID PEPPOL-EN16931-R008]`. */
private fun warningIds(report: String): List<String> =
    Regex("""<warning[^>]*>[^<]*\[ID ([^\]]+)]""").findAll(report).map { it.groupValues[1] }.toList()
