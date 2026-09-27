package de.fluchitconsulting.rechnungsgenerator.generate.einvoice

import de.fluchitconsulting.rechnungsgenerator.core.accepted.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.accepted.accepted
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.CancelledInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.CheckMethod
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.ServicePeriod
import de.fluchitconsulting.rechnungsgenerator.core.model.SkontoTier
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxShare
import de.fluchitconsulting.rechnungsgenerator.core.model.VatIdCheck
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testItem
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.values.CountryCode
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent
import de.fluchitconsulting.rechnungsgenerator.core.values.VatId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
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

private fun AcceptedInvoice.xml(): String = CiiWriter.write(this)

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

    // Je Art, die der Writer schreibt, eine Vergleichsdatei. Die Teilrechnung
    // unterscheidet sich von der Rechnung im XML nur im Code, sie prüft der
    // Test darunter. Dazu die englische Rechnung (#405): Mustang prüft nur,
    // ob ihr XML gilt, nicht, dass Einleitung und Befreiungsgrund darin auf
    // Englisch stehen.
    listOf("rechnung", "abschlagsrechnung", "schlussrechnung", "stornorechnung", "englische-rechnung").forEach { name ->
        test("$name.yaml ergibt das erwartete XML") {
            example(name).xml() shouldBe resource("$name-erwartet.xml")
        }

        test("das XML aus $name.yaml ist gültig, mit keiner Warnung außer PEPPOL-EN16931-R008") {
            example(name).xml().shouldBeValidExtended()
        }
    }

    // Der Pflichtsatz steht im XML in der Sprache der Rechnung, als Notiz
    // (#405). Die Vergleichsdatei der englischen Rechnung sieht ihn nicht:
    // Die gewöhnliche Rechnung hat keinen.
    test("das Storno auf Englisch trägt den englischen Pflichtsatz, und das XML ist gültig") {
        val seller = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))
        val invoice = YamlLoader.parseInvoice("sprache: en-GB\n" + resource("stornorechnung.yaml"))
        val xml = CalculatedInvoice(invoice, seller).accepted().xml()

        xml shouldContain
            "<ram:Content>This cancellation invoice cancels invoice R26-042 dated 14 August 2026 in full. " +
            "Any amount already paid will be refunded.</ram:Content>"
        xml.shouldBeValidExtended()
    }

    test("der Abschlag auf Englisch trägt den englischen Pflichtsatz, und das XML ist gültig") {
        val seller = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))
        val invoice = YamlLoader.parseInvoice("sprache: en-GB\n" + resource("abschlagsrechnung.yaml"))
        val xml = CalculatedInvoice(invoice, seller).accepted().xml()

        xml shouldContain
            "<ram:Content>The service has not yet been provided. A final invoice for the complete service " +
            "will follow, in which this advance payment will be deducted.</ram:Content>"
        xml.shouldBeValidExtended()
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
        xml shouldNotContain "Geschäftsführung"
    }

    context("BT-33 nennt Registergericht und Geschäftsführung wie das Blatt") {
        fun description(registerCourt: String?, managingDirector: String?): String {
            val config = testSellerConfig().copy(registerCourt = registerCourt, managingDirector = managingDirector)
            val xml = CalculatedInvoice(testInvoice(), config).accepted().xml()
            xml.shouldBeValidExtended()
            return xml.substringAfter("<ram:Description>").substringBefore("</ram:Description>")
        }

        test("beide, getrennt durch ein Semikolon, weil mehrere Namen selbst Kommas tragen") {
            description("Würzburg", "Dr. Erika Musterfrau, Max Beispiel") shouldBe
                "Registergericht Würzburg; Geschäftsführung: Dr. Erika Musterfrau, Max Beispiel"
        }

        test("nur das Registergericht") {
            description("Würzburg", null) shouldBe "Registergericht Würzburg"
        }

        test("nur die Geschäftsführung") {
            description(null, "Dr. Erika Musterfrau") shouldBe "Geschäftsführung: Dr. Erika Musterfrau"
        }
    }

    context("die Steuerkategorien außer S") {
        test("Z trägt keinen Befreiungsgrund") {
            val xml = invoiceIn(TaxCategory.ZERO_RATED).xml()

            xml.occurrencesOf("<ram:CategoryCode>Z</ram:CategoryCode>") shouldBe 2
            xml shouldNotContain "ram:ExemptionReason"
            xml.shouldBeValidExtended()
        }

        // Je einmal an der Position und in der Aufschlüsselung, gleich lautend.
        test("E trägt den Grund aus der Rechnungsdatei") {
            val xml = invoiceIn(TaxCategory.EXEMPT, reason = "Steuerfrei nach §4 Nr. 21 UStG").xml()

            xml.occurrencesOf("<ram:ExemptionReason>Steuerfrei nach §4 Nr. 21 UStG</ram:ExemptionReason>") shouldBe 2
            xml.shouldBeValidExtended()
        }

        listOf(TaxCategory.REVERSE_CHARGE to "AT", TaxCategory.EXPORT to "CH").forEach { (category, country) ->
            test("${category.code} trägt ohne eigenen Grund den der Kategorie") {
                val xml = invoiceIn(category, country).xml()

                xml.occurrencesOf("<ram:ExemptionReason>${category.defaultReason(Language.GERMAN)}</ram:ExemptionReason>") shouldBe 2
                xml.shouldBeValidExtended()
            }
        }

        // Der Begriff aus Anlage 8 zum UStAE ist selbst die zulässige Angabe,
        // auch in der maßgeblichen Fassung (#406).
        test("AE trägt auf Englisch den Grund Reverse charge") {
            val xml = invoiceIn(TaxCategory.REVERSE_CHARGE, "AT", language = Language.BRITISH_ENGLISH).xml()

            xml.occurrencesOf("<ram:ExemptionReason>Reverse charge</ram:ExemptionReason>") shouldBe 2
            xml.shouldBeValidExtended()
        }

        // Das Lieferland füllt den Lieferblock, und damit entfällt auch die
        // Warnung zum leeren Element.
        test("K nennt das Land des Kunden als Lieferland") {
            val xml = invoiceIn(TaxCategory.INTRA_COMMUNITY, "AT").xml()

            xml shouldContain
                """
                |    <ram:ApplicableHeaderTradeDelivery>
                |      <ram:ShipToTradeParty>
                |        <ram:PostalTradeAddress>
                |          <ram:CountryID>AT</ram:CountryID>
                """.trimMargin()
            xml.shouldBeValidExtended()
        }

        // In der Aufschlüsselung bleibt die 0 stehen, an der Position nicht.
        test("O nennt an der Position keinen Satz") {
            val xml = invoiceIn(TaxCategory.OUT_OF_SCOPE, reason = "Nicht steuerbar").xml()

            xml.occurrencesOf("<ram:RateApplicablePercent>") shouldBe 1
            xml.shouldBeValidExtended()
        }

        // EXTENDED lässt O neben anderen Kategorien zu und verlangt dann
        // nicht, dass die USt-IdNr. des Verkäufers fehlt. BR-O-02 und
        // BR-O-11 bis BR-O-14 prüft Mustang nur in BASIC und EN16931.
        test("O neben S ist gültig") {
            val invoice =
                testInvoice(
                    items =
                        listOf(
                            testItem(),
                            testItem(vatRate = "0", taxCategory = TaxCategory.OUT_OF_SCOPE, taxExemptionReason = "Nicht steuerbar"),
                        ),
                )

            CalculatedInvoice(invoice, testSellerConfig()).accepted().xml().shouldBeValidExtended()
        }

        test("gemischte Steuersätze samt E und Z sind gültig") {
            val xml = example("gemischte-steuersaetze").xml()

            listOf("S", "E", "Z").forEach { xml shouldContain "<ram:CategoryCode>$it</ram:CategoryCode>" }
            xml.shouldBeValidExtended()
        }
    }

    context("Rabatt und Skonto") {
        // Satz und Basis prüft der Validator nicht nach, nur die Summen: Ein
        // falscher Satz neben dem richtigen Betrag ginge durch. Deshalb stehen
        // beide Abschläge hier wörtlich.
        test("ein Rabatt als Satz trägt seine Basis, einer als Betrag nicht") {
            val xml = example("teilrechnung").xml()

            xml shouldContain
                """
                |        <ram:SpecifiedTradeAllowanceCharge>
                |          <ram:ChargeIndicator>
                |            <udt:Indicator>false</udt:Indicator>
                |          </ram:ChargeIndicator>
                |          <ram:CalculationPercent>30</ram:CalculationPercent>
                |          <ram:BasisAmount>800.00</ram:BasisAmount>
                |          <ram:ActualAmount>240.00</ram:ActualAmount>
                |          <ram:ReasonCode>95</ram:ReasonCode>
                |        </ram:SpecifiedTradeAllowanceCharge>
                |        <ram:SpecifiedTradeSettlementLineMonetarySummation>
                |          <ram:LineTotalAmount>560.00</ram:LineTotalAmount>
                """.trimMargin()
            xml shouldContain
                """
                |        <ram:SpecifiedTradeAllowanceCharge>
                |          <ram:ChargeIndicator>
                |            <udt:Indicator>false</udt:Indicator>
                |          </ram:ChargeIndicator>
                |          <ram:ActualAmount>100.00</ram:ActualAmount>
                |          <ram:ReasonCode>95</ram:ReasonCode>
                |        </ram:SpecifiedTradeAllowanceCharge>
                |        <ram:SpecifiedTradeSettlementLineMonetarySummation>
                |          <ram:LineTotalAmount>400.00</ram:LineTotalAmount>
                """.trimMargin()
            xml.occurrencesOf("<ram:SpecifiedTradeAllowanceCharge>") shouldBe 2
            xml.shouldBeValidExtended()
        }

        // Mustangs eigenes Modell rundet hier auf 6.208,78 € und bekäme von
        // seinem Validator eine Rechenwarnung (#363). Nach ADR-0013 sind es
        // 6.208,77 €, wie auf dem Blatt, und das nimmt er ohne Warnung an.
        test("die Beträge sind die nach ADR-0013, und der Validator rechnet sie nach") {
            val item = testItem(quantity = "42.09", unitPrice = "273.17", discount = "46")
            val xml = CalculatedInvoice(testInvoice(items = listOf(item)), testSellerConfig()).accepted().xml()

            xml shouldContain "<ram:LineTotalAmount>6208.77</ram:LineTotalAmount>"
            xml.shouldBeValidExtended()
        }

        // Nach der Frist sortiert und nicht wie in der Datei, wie auf dem
        // Blatt; das Zahlungsziel bleibt eigene Zahlungsbedingung davor.
        test("mehrere Skontostufen stehen je als eigene Zahlungsbedingung da") {
            val xml = example("skonto").xml()

            xml shouldContain
                """
                |      <ram:SpecifiedTradePaymentTerms>
                |        <ram:DueDateDateTime>
                |          <udt:DateTimeString format="102">20260828</udt:DateTimeString>
                |        </ram:DueDateDateTime>
                |      </ram:SpecifiedTradePaymentTerms>
                |      <ram:SpecifiedTradePaymentTerms>
                |        <ram:ApplicableTradePaymentDiscountTerms>
                |          <ram:BasisDateTime>
                |            <udt:DateTimeString format="102">20260814</udt:DateTimeString>
                |          </ram:BasisDateTime>
                |          <ram:BasisPeriodMeasure unitCode="DAY">7</ram:BasisPeriodMeasure>
                |          <ram:CalculationPercent>2</ram:CalculationPercent>
                |        </ram:ApplicableTradePaymentDiscountTerms>
                |      </ram:SpecifiedTradePaymentTerms>
                |      <ram:SpecifiedTradePaymentTerms>
                |        <ram:ApplicableTradePaymentDiscountTerms>
                |          <ram:BasisDateTime>
                |            <udt:DateTimeString format="102">20260814</udt:DateTimeString>
                |          </ram:BasisDateTime>
                |          <ram:BasisPeriodMeasure unitCode="DAY">10</ram:BasisPeriodMeasure>
                |          <ram:CalculationPercent>1.5</ram:CalculationPercent>
                |        </ram:ApplicableTradePaymentDiscountTerms>
                |      </ram:SpecifiedTradePaymentTerms>
                """.trimMargin()
            xml.shouldBeValidExtended()
        }

        test("eine Skontostufe ist gültig") {
            val invoice = testInvoice(skonto = listOf(SkontoTier(Percent("2"), 7)))
            val xml = CalculatedInvoice(invoice, testSellerConfig()).accepted().xml()

            xml.occurrencesOf("<ram:ApplicableTradePaymentDiscountTerms>") shouldBe 1
            xml.shouldBeValidExtended()
        }
    }

    context("Abschlag und Storno") {
        // Die Vergleichsdateien tragen den Wortlaut; hier steht, worauf es
        // bei der Art ankommt.
        test("der Abschlag nennt seinen Pflichtsatz und den Tag der Vereinnahmung") {
            val xml = example("abschlagsrechnung").xml()

            xml shouldContain "<ram:TypeCode>386</ram:TypeCode>"
            xml shouldContain "<ram:Content>Die Leistung ist noch nicht erbracht."
            xml shouldContain
                """
                |        <ram:TaxPointDate>
                |          <udt:DateString format="102">20260610</udt:DateString>
                |        </ram:TaxPointDate>
                """.trimMargin()
        }

        test("ohne Vereinnahmung fehlt der Steuerzeitpunkt, wie auf dem Blatt") {
            val invoice = example("abschlagsrechnung").calculated.invoice.copy(receivedOn = null)
            val xml = CalculatedInvoice(invoice, testSellerConfig()).accepted().xml()

            xml shouldNotContain "ram:TaxPointDate"
            xml.shouldBeValidExtended()
        }

        test("das Storno nennt die Ursprungsrechnung, im Pflichtsatz und als Bezug") {
            val xml = example("stornorechnung").xml()

            xml shouldContain "<ram:TypeCode>384</ram:TypeCode>"
            xml shouldContain "hebt die Rechnung R26-042 vom 14. August 2026 vollständig auf."
            xml shouldContain
                """
                |      <ram:InvoiceReferencedDocument>
                |        <ram:IssuerAssignedID>R26-042</ram:IssuerAssignedID>
                |        <ram:FormattedIssueDateTime>
                |          <qdt:DateTimeString format="102">20260814</qdt:DateTimeString>
                |        </ram:FormattedIssueDateTime>
                |      </ram:InvoiceReferencedDocument>
                """.trimMargin()
        }

        // Ein Storno fordert kein Geld (requestsPayment): keine Bankverbindung,
        // kein Zahlungsziel, und der Betrag steht negativ da (ADR-0006).
        test("das Storno fordert nichts") {
            val xml = example("stornorechnung").xml()

            xml shouldNotContain "ram:SpecifiedTradeSettlementPaymentMeans"
            xml shouldNotContain "ram:SpecifiedTradePaymentTerms"
            xml shouldContain "<ram:DuePayableAmount>-9921.63</ram:DuePayableAmount>"
        }

        // Der Rabatt bekommt das Vorzeichen der Basis (ADR-0013), im XML
        // also einen negativen Abschlag auf eine negative Position.
        test("ein Storno mit Rabatt ist gültig") {
            val items =
                listOf(
                    testItem(quantity = "-2", discount = "10"),
                    testItem(quantity = "-1", discountAmount = "20.00"),
                )
            val invoice =
                testInvoice(
                    type = InvoiceType.CANCELLATION,
                    items = items,
                    cancelled = CancelledInvoice("R26-000", LocalDate.of(2026, 8, 1)),
                )
            val xml = CalculatedInvoice(invoice, testSellerConfig()).accepted().xml()

            xml shouldContain "<ram:ActualAmount>-20.00</ram:ActualAmount>"
            xml.shouldBeValidExtended()
        }
    }

    context("die Schlussrechnung") {
        // Mustang rechnet nur die Summe der Abschläge gegen den Restbetrag
        // nach (BR-FXEXT-CO-16), nicht den einzelnen Abschlag. Deshalb steht
        // einer hier wörtlich, Feld für Feld wie im Block abschlaege.
        test("jeder abgesetzte Abschlag steht mit Betrag, Vereinnahmung, Steueranteil und Rechnung da") {
            val xml = example("schlussrechnung").xml()

            xml shouldContain
                """
                |      <ram:SpecifiedAdvancePayment>
                |        <ram:PaidAmount>4760.00</ram:PaidAmount>
                |        <ram:FormattedReceivedDateTime>
                |          <qdt:DateTimeString format="102">20260610</qdt:DateTimeString>
                |        </ram:FormattedReceivedDateTime>
                |        <ram:IncludedTradeTax>
                |          <ram:CalculatedAmount>760.00</ram:CalculatedAmount>
                |          <ram:TypeCode>VAT</ram:TypeCode>
                |          <ram:BasisAmount>4000.00</ram:BasisAmount>
                |          <ram:CategoryCode>S</ram:CategoryCode>
                |          <ram:RateApplicablePercent>19</ram:RateApplicablePercent>
                |        </ram:IncludedTradeTax>
                |        <ram:InvoiceSpecifiedReferencedDocument>
                |          <ram:IssuerAssignedID>R26-009</ram:IssuerAssignedID>
                |          <ram:TypeCode>386</ram:TypeCode>
                |          <ram:FormattedIssueDateTime>
                |            <qdt:DateTimeString format="102">20260615</qdt:DateTimeString>
                |          </ram:FormattedIssueDateTime>
                |        </ram:InvoiceSpecifiedReferencedDocument>
                |      </ram:SpecifiedAdvancePayment>
                """.trimMargin()
            xml.occurrencesOf("<ram:SpecifiedAdvancePayment>") shouldBe 2
        }

        test("die Summen setzen die Abschläge ab, die Steuer bleibt die der Gesamtleistung") {
            val xml = example("schlussrechnung").xml()

            listOf(
                "<ram:TaxTotalAmount currencyID=\"EUR\">3800.00</ram:TaxTotalAmount>",
                "<ram:GrandTotalAmount>23800.00</ram:GrandTotalAmount>",
                "<ram:TotalPrepaidAmount>9520.00</ram:TotalPrepaidAmount>",
                "<ram:DuePayableAmount>14280.00</ram:DuePayableAmount>",
            ).forEach { xml shouldContain it }
        }

        // Je Kombination aus Kategorie und Satz ein Steueranteil, wie in
        // steueranteile (ADR-0002).
        test("ein Abschlag mit zwei Steuersätzen trägt zwei Steueranteile und bleibt gültig") {
            val invoice =
                example("schlussrechnung").calculated.invoice.let { invoice ->
                    val first = invoice.prepayments.first()
                    val split =
                        first.copy(
                            taxShares =
                                listOf(
                                    TaxShare(vatRate = Percent("19"), net = Money("3000.00"), vat = Money("570.00")),
                                    TaxShare(vatRate = Percent("7"), net = Money("1000.00"), vat = Money("70.00")),
                                ),
                        )
                    invoice.copy(
                        items = listOf(testItem(unitPrice = "18000.00"), testItem(unitPrice = "2000.00", vatRate = "7")),
                        prepayments = listOf(split) + invoice.prepayments.drop(1),
                    )
                }
            val xml = CalculatedInvoice(invoice, testSellerConfig()).accepted().xml()

            xml.occurrencesOf("<ram:IncludedTradeTax>") shouldBe 3
            xml shouldContain "<ram:PaidAmount>4640.00</ram:PaidAmount>"
            xml.shouldBeValidExtended()
        }

        // Wie auf dem Blatt: Wer nichts mehr fordert, nennt weder Konto noch
        // Frist (requestsPayment).
        test("ausgeglichen fordert sie nichts mehr") {
            val xml = example("schlussrechnung-ausgeglichen").xml()

            xml shouldContain "<ram:DuePayableAmount>0.00</ram:DuePayableAmount>"
            xml shouldNotContain "ram:SpecifiedTradeSettlementPaymentMeans"
            xml shouldNotContain "ram:SpecifiedTradePaymentTerms"
            xml.shouldBeValidExtended()
        }
    }
})

/**
 * Eine Rechnung, deren einzige Position in [category] steht, an einen Kunden
 * in [country]. Verlangt die Kategorie die USt-IdNr. des Kunden, trägt er eine
 * samt Nachweis der Bestätigungsabfrage, sonst lehnten die Regeln ab.
 */
private fun invoiceIn(
    category: TaxCategory,
    country: String = "DE",
    reason: String? = null,
    language: Language = Language.GERMAN,
): AcceptedInvoice {
    val invoice =
        testInvoice(
            items = listOf(testItem(vatRate = "0", taxCategory = category, taxExemptionReason = reason)),
            language = language,
        )
    val customer =
        invoice.customer.copy(
            address = invoice.customer.address.copy(countryCode = CountryCode(country)),
            vatId = VatId("ATU12345678").takeIf { category.requiresCustomerVatId },
            vatIdCheck =
                VatIdCheck(LocalDate.of(2026, 8, 1), CheckMethod.QUALIFIED, "BZSt-Protokoll 4711")
                    .takeIf { category.requiresCustomerVatId },
        )
    return CalculatedInvoice(invoice.copy(customer = customer), testSellerConfig()).accepted()
}

private fun String.occurrencesOf(text: String): Int = split(text).size - 1

/**
 * Gültig heißt für Mustang auch "gültig mit Warnungen". Erlaubt ist nur die
 * zum leeren `ApplicableHeaderTradeDelivery` (siehe `CiiWriter`), und die
 * fehlt, sobald der Block ein Lieferland trägt. Jede andere wäre ein Befund,
 * der sonst unbemerkt durchginge.
 *
 * Gezählt wird jede Warnung und nicht nur die mit Kennung: Mustangs eigene
 * Rechenprüfung meldet eine abweichende Summe als Warnung ohne `[ID …]`, und
 * gerade sie hält die Beträge des XML an denen des Blatts.
 */
private fun String.shouldBeValidExtended() {
    val validator = ZUGFeRDValidator()
    val report = validator.validate(toByteArray(), "factur-x.xml")

    withClue(report) {
        validator.wasCompletelyValid() shouldBe true
        warnings(report).filterNot { "[ID PEPPOL-EN16931-R008]" in it }.shouldBeEmpty()
    }
}

private fun warnings(report: String): List<String> =
    Regex("""<warning[^>]*>([^<]*)""").findAll(report).map { it.groupValues[1].trim() }.toList()
