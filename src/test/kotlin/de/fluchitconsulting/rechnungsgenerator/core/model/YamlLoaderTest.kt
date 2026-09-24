package de.fluchitconsulting.rechnungsgenerator.core.model

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.values.Design
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent
import de.fluchitconsulting.rechnungsgenerator.core.values.Price
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.SerializationException
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.createDirectories
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

    test("ohne design gilt helsinki, mit design das genannte") {
        // ADR-0011: Die Wahl steht in den Stammdaten, Vorgabe ist das heutige Design.
        YamlLoader.parseSellerConfig(resource("stammdaten.yaml")).design shouldBe Design.HELSINKI
        YamlLoader.parseSellerConfig(resource("stammdaten.yaml") + "design: prag\n").design shouldBe Design.PRAG
    }

    test("ein unbekanntes design bricht ab und nennt die bekannten") {
        val fehler =
            shouldThrow<Exception> {
                YamlLoader.parseSellerConfig(resource("stammdaten.yaml") + "design: gitter\n")
            }

        fehler.message!! shouldContain "gitter"
        fehler.message!! shouldContain "helsinki, prag"
    }

    test("die Namen vor #324 sind unbekannte Designs wie jeder andere Wert") {
        // Nachtrag zu ADR-0011: keine Zuordnung zu den neuen Namen, auch keine
        // Warnung – wer sie noch stehen hat, ändert die Zeile in den Stammdaten.
        listOf("balken", "raster").forEach { alt ->
            withClue(alt) {
                val fehler =
                    shouldThrow<Exception> {
                        YamlLoader.parseSellerConfig(resource("stammdaten.yaml") + "design: $alt\n")
                    }

                fehler.message!! shouldContain "Unbekanntes Design"
                fehler.message!! shouldContain alt
            }
        }
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

        val totals = CalculatedInvoice(invoice, config)

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

    test("ansprechpartner liest die Kontaktstelle des Kunden, wörtlich") {
        // BT-56. Wörtlich heißt: Das Programm setzt kein "z. Hd." davor.
        val yaml =
            resource("rechnung.yaml").replace(
                "  name: Beispiel Software GmbH",
                "  name: Beispiel Software GmbH\n  ansprechpartner: Kreditorenbuchhaltung",
            )

        YamlLoader.parseInvoice(yaml).customer.contactPoint shouldBe "Kreditorenbuchhaltung"
        YamlLoader.parseInvoice(resource("rechnung.yaml")).customer.contactPoint shouldBe null
    }

    test("kundennummer liest die Kundennummer, als Text") {
        // BT-46. Als Text, auch wenn sie nur aus Ziffern besteht: Eine führende
        // Null gehört zur Nummer.
        val yaml =
            resource("rechnung.yaml").replace(
                "  name: Beispiel Software GmbH",
                "  name: Beispiel Software GmbH\n  kundennummer: \"0017\"",
            )

        YamlLoader.parseInvoice(yaml).customer.buyerIdentifier shouldBe "0017"
        YamlLoader.parseInvoice(resource("rechnung.yaml")).customer.buyerIdentifier shouldBe null
    }

    test("auftragsnummer liest die eigene Auftragsnummer") {
        // BT-14, auf oberster Ebene und nicht unter kunde: Vergeben hat sie
        // der Rechnungssteller.
        val yaml = "auftragsnummer: A-2026-014\n" + resource("rechnung.yaml")

        YamlLoader.parseInvoice(yaml).salesOrderReference shouldBe "A-2026-014"
        YamlLoader.parseInvoice(resource("rechnung.yaml")).salesOrderReference shouldBe null
    }

    test("einleitung liest den Satz über den Positionen") {
        // >- faltet die Zeilen mit Leerzeichen und lässt den Zeilenumbruch am
        // Ende weg – so, wie das Beispiel im Skill rechnung es schreibt.
        val yaml = "einleitung: >-\n  Für den Workshop\n  berechnen wir:\n" + resource("rechnung.yaml")

        YamlLoader.parseInvoice(yaml).introduction shouldBe "Für den Workshop berechnen wir:"
        YamlLoader.parseInvoice(resource("rechnung.yaml")).introduction shouldBe null
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
    test("skonto liest die Skontostufen in der Reihenfolge der Datei") {
        val yaml =
            resource("rechnung.yaml").replace(
                "positionen:",
                "skonto:\n  - satz: 1.5\n    tage: 10\n  - satz: 2\n    tage: 7\npositionen:",
            )

        YamlLoader.parseInvoice(yaml).skonto shouldBe
            listOf(SkontoTier(rate = Percent("1.5"), days = 10), SkontoTier(rate = Percent("2"), days = 7))
    }

    test("eine Rechnungsdatei ohne skonto hat keine Skontostufen") {
        YamlLoader.parseInvoice(resource("rechnung.yaml")).skonto shouldBe emptyList()
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

        val totals = CalculatedInvoice(invoice, config)

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
        val file = createTempDirectory("loader-test").resolve("stammdaten.yaml")
        file.writeText(resource("stammdaten.yaml").replace("0532 0130 00", "0532 0130 01"))

        val failure = shouldThrow<InputFileException> { YamlLoader.loadSellerConfig(file) }

        failure.message!! shouldContain file.toString()
        failure.message!! shouldContain "Prüfsumme"
    }

    test("ein Muster, das kein regulärer Ausdruck ist, nennt die Datei") {
        // Der Tippfehler {2-3} statt {2,3} fällt beim Laden auf, nicht erst bei
        // der ersten Rechnung, die daran scheitert.
        val file = createTempDirectory("loader-test").resolve("stammdaten.yaml")
        file.writeText(resource("stammdaten.yaml").replace("R\\d{2}-\\d{3}", "R\\d{2-3}"))

        val failure = shouldThrow<InputFileException> { YamlLoader.loadSellerConfig(file) }

        failure.message!! shouldContain file.toString()
        failure.message!! shouldContain "Kein gültiger regulärer Ausdruck"
    }

    context("logo") {
        // Stammdaten mit Logo-Eintrag in einem eigenen Verzeichnis; das Logo
        // liegt, wo der Test es hinlegt.
        fun stammdatenMitLogo(eintrag: String): Path {
            val file = createTempDirectory("loader-test").resolve("stammdaten.yaml")
            file.writeText(resource("stammdaten.yaml") + "logo: $eintrag\n")
            return file
        }

        test("ohne Eintrag gibt es kein Logo") {
            val file = createTempDirectory("loader-test").resolve("stammdaten.yaml")
            file.writeText(resource("stammdaten.yaml"))

            YamlLoader.loadSellerConfig(file).logo shouldBe null
        }

        test("ein relativer Pfad gilt ab dem Verzeichnis der Stammdaten") {
            // Nicht ab dem Arbeitsverzeichnis des Tests – das ist ein anderes.
            val file = stammdatenMitLogo("marke/logo.tex")
            val logoDatei = file.resolveSibling("marke").createDirectories().resolve("logo.tex")
            logoDatei.writeText("\\fill (0,0) circle (1);")

            val logo = YamlLoader.loadSellerConfig(file).logo!!

            logo.source shouldBe logoDatei.toAbsolutePath()
            logo.content.decodeToString() shouldBe "\\fill (0,0) circle (1);"
        }

        test("ein absoluter Pfad gilt, wie er dasteht") {
            val logoDatei = createTempDirectory("loader-logo").resolve("logo.tex")
            logoDatei.writeText("\\fill (0,0) circle (1);")

            YamlLoader.loadSellerConfig(stammdatenMitLogo(logoDatei.toAbsolutePath().toString())).logo!!.source shouldBe
                logoDatei.toAbsolutePath()
        }

        test("eine fehlende Logodatei nennt beide Dateien") {
            val file = stammdatenMitLogo("gibtsnicht.tex")

            val failure = shouldThrow<InputFileException> { YamlLoader.loadSellerConfig(file) }

            failure.message!! shouldContain file.toString()
            failure.message!! shouldContain file.resolveSibling("gibtsnicht.tex").toAbsolutePath().toString()
            failure.message!! shouldContain "es gibt sie nicht"
        }

        test("eine andere Endung als .tex bricht ab, bevor gelesen wird") {
            // Die Datei gibt es nicht einmal: Die Endung entscheidet vorher.
            val file = stammdatenMitLogo("logo.png")

            val failure = shouldThrow<InputFileException> { YamlLoader.loadSellerConfig(file) }

            failure.message!! shouldContain file.toString()
            failure.message!! shouldContain "zulässig ist .tex"
        }

        test("die Endung zählt ohne Groß- und Kleinschreibung, weiter geht es klein") {
            // macOS unterscheidet die Schreibweise im Dateinamen nicht, und
            // eine Datei Logo.TEX ist eine .tex-Datei. Weitergereicht wird die
            // Endung klein: Die Vorlage liest \input{logo}, und das findet
            // unter Linux nur logo.tex.
            val file = stammdatenMitLogo("Logo.TEX")
            file.resolveSibling("Logo.TEX").writeText("\\fill (0,0) circle (1);")

            YamlLoader.loadSellerConfig(file).logo!!.extension shouldBe "tex"
        }

        test("parseSellerConfig lehnt ein Logo ab, weil es keinen Ort kennt") {
            val failure =
                shouldThrow<SerializationException> {
                    YamlLoader.parseSellerConfig(resource("stammdaten.yaml") + "logo: logo.tex\n")
                }

            failure.message!! shouldContain "loadSellerConfig"
        }
    }

    test("ein YAML-Fehler nennt die Datei") {
        val file = createTempDirectory("loader-test").resolve("kaputt.yaml")
        file.writeText("nummer: R26-001\ngibtsnicht: 1\n")

        val failure = shouldThrow<InputFileException> { YamlLoader.loadInvoice(file) }

        failure.message!! shouldContain file.toString()
        failure.message!! shouldContain "gibtsnicht"
    }
})
