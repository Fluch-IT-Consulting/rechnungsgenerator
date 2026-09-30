package de.fluchitconsulting.rechnungsgenerator.prepayments

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxShare
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.core.model.testPrepayment
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.rules.Rule
import de.fluchitconsulting.rechnungsgenerator.core.rules.messageOf
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate

private fun resource(name: String): String =
    checkNotNull(PrepaymentBlockTest::class.java.getResource("/$name")).readText()

// So, wie `>>` den Block anhängt.
private fun finalInvoiceWith(block: String): String =
    resource("schlussrechnung.yaml").substringBefore("abschlaege:") + block + "\n"

class PrepaymentBlockTest : FunSpec({

    val vereinnahmt = CollectedPrepayment("Rechnung_R26-009-beispiel.yaml", testPrepayment())
    val notReceived =
        CollectedPrepayment(
            "Rechnung_R26-011-beispiel.yaml",
            testPrepayment(invoiceNumber = "R26-011", issueDate = LocalDate.of(2026, 8, 20), receivedOn = null),
        )

    test("der Block hat die in #41 vereinbarte Gestalt") {
        // Zeichengleich, damit weder ein Datum noch ein Pfad hineinrutscht.
        PrepaymentBlock.render(listOf(vereinnahmt, notReceived)) shouldBe
            """
            |
            |# Abschläge für die Schlussrechnung, vorbereitet von `rechnungsgenerator abschlaege`.
            |# Neu berechnet mit dem heutigen Stand des Generators – nicht gegen die
            |# verschickten PDFs geprüft.
            |#
            |# Abgesetzt wird nur, was vereinnahmt ist (§14 Abs. 5 Satz 2 UStG):
            |#   - nicht vereinnahmt: den Abschlag hier streichen, er wird gemahnt oder storniert
            |#   - nur teilweise vereinnahmt: Die Steueranteile gelten für den vollen Betrag
            |#     und stimmen dann nicht. Diesen Fall deckt der Generator noch nicht ab.
            |abschlaege:
            |  # Rechnung_R26-009-beispiel.yaml
            |  - rechnungsnummer: R26-009
            |    rechnungsdatum: 2026-06-15
            |    vereinnahmt-am: 2026-06-10
            |    steueranteile:
            |      - steuerkategorie: S
            |        ust-satz: 19
            |        netto: 4000.00
            |        ust: 760.00
            |  # Rechnung_R26-011-beispiel.yaml
            |  - rechnungsnummer: R26-011
            |    rechnungsdatum: 2026-08-20
            |    vereinnahmt-am:                 # Datum der Vereinnahmung eintragen
            |    steueranteile:
            |      - steuerkategorie: S
            |        ust-satz: 19
            |        netto: 4000.00
            |        ust: 760.00
            """.trimMargin()
    }

    test("angehängt und zurückgelesen ergibt der Block dieselben Abschläge") {
        val mixed =
            CollectedPrepayment(
                "gemischt.yaml",
                testPrepayment(
                    invoiceNumber = "R26-012",
                    receivedOn = null,
                    taxShares =
                        listOf(
                            TaxShare(TaxCategory.STANDARD, Percent("19"), Money("1000.00"), Money("190.00")),
                            TaxShare(TaxCategory.STANDARD, Percent("7"), Money("500.00"), Money("35.00")),
                        ),
                ),
            )
        val entries = listOf(vereinnahmt, mixed)

        val parsed = YamlLoader.parseInvoice(finalInvoiceWith(PrepaymentBlock.render(entries)))

        parsed.prepayments shouldBe entries.map { it.prepayment }
        parsed.prepayments[1].receivedOn shouldBe null
    }

    test("ein leeres vereinnahmt-am wird als fehlende Vereinnahmung gemeldet, mit Begründung") {
        val finalInvoice =
            YamlLoader.parseInvoice(finalInvoiceWith(PrepaymentBlock.render(listOf(vereinnahmt, notReceived))))

        val message =
            CalculatedInvoice(finalInvoice, testSellerConfig())
                .messageOf(Rule.ABSCHLAG_OHNE_VEREINNAHMUNG)

        message shouldContain "Abschlag R26-011: ohne 'vereinnahmt-am' keine Absetzung"
    }

    test("eine Rechnungsnummer mit YAML-Sonderzeichen kommt unverändert zurück") {
        // Ohne Anführungszeichen läse YAML aus "R26 #9" still "R26".
        listOf("R26 #9", "R26: 9", "-R26", "R26 \"neu\"", "R26\\9").forEach { number ->
            val entry = CollectedPrepayment("x.yaml", testPrepayment(invoiceNumber = number))

            val parsed = YamlLoader.parseInvoice(finalInvoiceWith(PrepaymentBlock.render(listOf(entry))))

            parsed.prepayments.single().invoiceNumber shouldBe number
        }
    }
})
