package de.fluchitconsulting.rechnung.render

import de.fluchitconsulting.rechnung.InvoiceCalculator
import de.fluchitconsulting.rechnung.InvoiceValidationException
import de.fluchitconsulting.rechnung.YamlLoader
import de.fluchitconsulting.rechnung.model.TaxShare
import de.fluchitconsulting.rechnung.testPrepayment
import de.fluchitconsulting.rechnung.testSellerConfig
import de.fluchitconsulting.rechnung.values.Money
import de.fluchitconsulting.rechnung.values.Percent
import de.fluchitconsulting.rechnung.values.TaxCategory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate

private fun resource(name: String): String =
    checkNotNull(PrepaymentBlockTest::class.java.getResource("/$name")).readText()

/**
 * Die Beispiel-Schlussrechnung ohne ihren `abschlaege`-Block, mit dem erzeugten
 * Block dahinter – so, wie `>>` ihn anhängt.
 */
private fun finalInvoiceWith(block: String): String =
    resource("schlussrechnung.yaml").substringBefore("abschlaege:") + block + "\n"

/**
 * Prüft den Text, den `rechnungsgenerator abschlaege` ausgibt – ohne Dateien
 * und ohne Kommando. Woher die Abschläge kommen, ist hier egal; es geht nur
 * darum, wie sie hingeschrieben werden und ob sie so wieder gelesen werden.
 */
class PrepaymentBlockTest : FunSpec({

    val vereinnahmt = PrepaymentBlock.Entry("Rechnung_R26-009-beispiel.yaml", testPrepayment())
    val nichtVereinnahmt =
        PrepaymentBlock.Entry(
            "Rechnung_R26-011-beispiel.yaml",
            testPrepayment(invoiceNumber = "R26-011", issueDate = LocalDate.of(2026, 8, 20), receivedOn = null),
        )

    test("der Block hat die in #41 vereinbarte Gestalt") {
        // Zeichengleich verglichen: Damit ist zugleich gesichert, dass weder ein
        // Datum noch ein Pfad hineinrutscht – sonst ergäbe derselbe Aufruf
        // morgen oder aus einem anderen Ordner einen anderen Text.
        PrepaymentBlock.render(listOf(vereinnahmt, nichtVereinnahmt)) shouldBe
            """
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
        // Ein gemischter Abschlag mit zwei Steueranteilen, damit auch die Liste
        // unter steueranteile mehr als einen Eintrag trägt.
        val mixed =
            PrepaymentBlock.Entry(
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
        // Schon in der Gleichheit enthalten, hier aber eigens genannt: Das leere
        // Feld wird als fehlend gelesen, nicht als Fehler im YAML.
        parsed.prepayments[1].receivedOn shouldBe null
    }

    test("ein leeres vereinnahmt-am lässt Regel 3 mit ihrer Begründung zuschlagen") {
        val schlussrechnung =
            YamlLoader.parseInvoice(finalInvoiceWith(PrepaymentBlock.render(listOf(vereinnahmt, nichtVereinnahmt))))

        val failure =
            shouldThrow<InvoiceValidationException> { InvoiceCalculator.calculate(schlussrechnung, testSellerConfig()) }

        failure.message!! shouldContain "Abschlag R26-011: ohne 'vereinnahmt-am' keine Absetzung"
    }

    test("eine Rechnungsnummer mit YAML-Sonderzeichen kommt unverändert zurück") {
        // "R26 #9" ist der gefährliche Fall: Ohne Anführungszeichen beginnt mit
        // " #" ein Kommentar, und YAML liest still "R26" – der Abschlag stünde
        // mit falscher Nummer in der Schlussrechnung, und keine Regel merkte es.
        listOf("R26 #9", "R26: 9", "-R26", "R26 \"neu\"", "R26\\9").forEach { number ->
            val entry = PrepaymentBlock.Entry("x.yaml", testPrepayment(invoiceNumber = number))

            val parsed = YamlLoader.parseInvoice(finalInvoiceWith(PrepaymentBlock.render(listOf(entry))))

            parsed.prepayments.single().invoiceNumber shouldBe number
        }
    }
})
