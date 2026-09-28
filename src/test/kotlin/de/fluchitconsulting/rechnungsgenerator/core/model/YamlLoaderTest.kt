package de.fluchitconsulting.rechnungsgenerator.core.model

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.values.Currency
import de.fluchitconsulting.rechnungsgenerator.core.values.ExchangeRate
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent
import de.fluchitconsulting.rechnungsgenerator.core.values.Price
import de.fluchitconsulting.rechnungsgenerator.core.values.RgbColor
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.SerializationException
import java.nio.file.Path
import java.time.LocalDate
import java.time.YearMonth
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
        val error =
            shouldThrow<Exception> {
                YamlLoader.parseSellerConfig(resource("stammdaten.yaml") + "design: gitter\n")
            }

        error.message!! shouldContain "gitter"
        error.message!! shouldContain "helsinki, prag"
    }

    test("balken und raster sind unbekannte Designs wie jeder andere Wert") {
        // Nachtrag zu ADR-0011: keine Zuordnung zu helsinki und prag, auch
        // keine Warnung – wer einen davon stehen hat, ändert die Zeile in den
        // Stammdaten.
        listOf("balken", "raster").forEach { old ->
            withClue(old) {
                val error =
                    shouldThrow<Exception> {
                        YamlLoader.parseSellerConfig(resource("stammdaten.yaml") + "design: $old\n")
                    }

                error.message!! shouldContain "Unbekanntes Design"
                error.message!! shouldContain old
            }
        }
    }

    context("schlusssatz (#377)") {
        fun configWith(table: String): SellerConfig = YamlLoader.parseSellerConfig(resource("stammdaten.yaml") + "schlusssatz:\n$table")

        test("ohne Eintrag gilt die Vorgabe der Sprache") {
            val config = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))

            config.closingLine(Language.GERMAN) shouldBe "Ich bedanke mich für das Vertrauen und die angenehme Zusammenarbeit."
            config.closingLine(Language.BRITISH_ENGLISH) shouldBe "Thank you for your trust and the pleasant collaboration."
        }

        test("die Tabelle gilt je Sprache, und eine fehlende fällt auf ihre Vorgabe") {
            val config = configWith("  de: Danke.\n")

            config.closingLine(Language.GERMAN) shouldBe "Danke."
            config.closingLine(Language.BRITISH_ENGLISH) shouldBe "Thank you for your trust and the pleasant collaboration."
        }

        test("ein unbekannter oder doppelter Schlüssel bricht ab, mit Datei und Schlüssel") {
            listOf("  fr: Merci.\n", "  en-AU: Thanks.\n", "  de-DE: Danke.\n  DE-de: Vielen Dank.\n").forEach { table ->
                withClue(table) {
                    val file = createTempDirectory("loader-test").resolve("stammdaten.yaml")
                    file.writeText(resource("stammdaten.yaml") + "schlusssatz:\n$table")

                    val failure = shouldThrow<InputFileException> { YamlLoader.loadSellerConfig(file) }

                    failure.message!! shouldContain file.toString()
                    failure.message!! shouldContain "schlusssatz: "
                }
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
        // Die Vorgabe hält jede Rechnungsdatei gültig, die den Schlüssel nicht
        // kennt.
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

    context("sprache (#377)") {
        fun invoiceIn(language: String): String = resource("rechnung.yaml").replace("nummer:", "sprache: $language\nnummer:")

        test("ohne sprache ist die Rechnung deutsch") {
            YamlLoader.parseInvoice(resource("rechnung.yaml")).language shouldBe Language.GERMAN
        }

        test("sprache wählt die Sprache, ohne Rücksicht auf die Schreibweise") {
            assertSoftly {
                YamlLoader.parseInvoice(invoiceIn("en-GB")).language shouldBe Language.BRITISH_ENGLISH
                YamlLoader.parseInvoice(invoiceIn("EN-GB")).language shouldBe Language.BRITISH_ENGLISH
                YamlLoader.parseInvoice(invoiceIn("en-us")).language shouldBe Language.AMERICAN_ENGLISH
                YamlLoader.parseInvoice(invoiceIn("de-de")).language shouldBe Language.GERMAN
            }
        }

        test("ohne Variante oder ausgeschrieben bricht das Lesen ab und nennt Datei und Wert") {
            // Die Variante gehört zum Wert: en ließe offen, ob en-GB oder en-US.
            listOf("en", "englisch", "de", "en-AU").forEach { language ->
                withClue(language) {
                    val file = createTempDirectory("loader-test").resolve("rechnung.yaml")
                    file.writeText(invoiceIn(language))

                    val failure = shouldThrow<InputFileException> { YamlLoader.loadInvoice(file) }

                    failure.message!! shouldContain file.toString()
                    failure.message!! shouldContain "Unbekannte Sprache '$language'"
                    failure.message!! shouldContain "de-DE, en-GB, en-US"
                }
            }
        }
    }

    context("waehrung (#409)") {
        fun invoiceIn(currency: String): String = resource("rechnung.yaml").replace("nummer:", "waehrung: $currency\nnummer:")

        test("ohne waehrung ist die Rechnung in Euro") {
            YamlLoader.parseInvoice(resource("rechnung.yaml")).currency shouldBe Currency.EUR
        }

        test("waehrung wählt die Währung, ohne Rücksicht auf die Schreibweise") {
            assertSoftly {
                YamlLoader.parseInvoice(invoiceIn("USD")).currency shouldBe Currency.USD
                YamlLoader.parseInvoice(invoiceIn("gbp")).currency shouldBe Currency.GBP
                YamlLoader.parseInvoice(invoiceIn("EUR")).currency shouldBe Currency.EUR
            }
        }

        test("eine andere Währung bricht das Lesen ab und nennt Datei und Wert") {
            // Auch das Zeichen ist kein Code: $ ließe offen, welcher Dollar.
            listOf("CHF", "$", "Euro").forEach { currency ->
                withClue(currency) {
                    val file = createTempDirectory("loader-test").resolve("rechnung.yaml")
                    file.writeText(invoiceIn("'$currency'"))

                    val failure = shouldThrow<InputFileException> { YamlLoader.loadInvoice(file) }

                    failure.message!! shouldContain file.toString()
                    failure.message!! shouldContain "Unbekannte Währung '$currency'"
                    failure.message!! shouldContain "EUR, USD, GBP"
                }
            }
        }
    }
    context("umrechnung (#418)") {
        fun invoiceWith(conversion: String): String =
            resource("rechnung.yaml").replace("nummer:", "waehrung: USD\numrechnung:\n$conversion\nnummer:")

        test("ohne umrechnung gibt es keine") {
            YamlLoader.parseInvoice(resource("rechnung.yaml")).conversion shouldBe null
        }

        test("umrechnung liest Kurs und Monat") {
            val conversion = YamlLoader.parseInvoice(invoiceWith("  kurs: 1.1685\n  monat: 2026-08")).conversion

            conversion shouldBe CurrencyConversion(ExchangeRate("1.1685"), YearMonth.of(2026, 8))
        }

        test("ein Monat ohne führende Null bricht das Lesen ab und nennt Datei und Wert") {
            val file = createTempDirectory("loader-test").resolve("rechnung.yaml")
            file.writeText(invoiceWith("  kurs: 1.1685\n  monat: 2026-8"))

            val failure = shouldThrow<InputFileException> { YamlLoader.loadInvoice(file) }

            failure.message!! shouldContain file.toString()
            failure.message!! shouldContain "Kein gültiger Monat: '2026-8'"
        }

        test("ein Kurs von null bricht das Lesen ab und nennt Datei und Wert") {
            val file = createTempDirectory("loader-test").resolve("rechnung.yaml")
            file.writeText(invoiceWith("  kurs: 0\n  monat: 2026-08"))

            val failure = shouldThrow<InputFileException> { YamlLoader.loadInvoice(file) }

            failure.message!! shouldContain file.toString()
            failure.message!! shouldContain "Kein gültiger Kurs: '0'"
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
        val unreadable = createTempDirectory("loader-test").resolve("unlesbar.yaml")
        unreadable.writeText(resource("rechnung.yaml"))
        unreadable.toFile().setReadable(false)

        val failure = shouldThrow<InputFileException> { YamlLoader.loadInvoice(unreadable) }

        failure.message!! shouldContain unreadable.toString()
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

    context("akzentfarbe (#371)") {
        test("ohne Eintrag keine, mit Eintrag die genannte") {
            YamlLoader.parseSellerConfig(resource("stammdaten.yaml")).accentColor shouldBe null
            YamlLoader.parseSellerConfig(resource("stammdaten.yaml") + "akzentfarbe: 31ae34\n").accentColor shouldBe
                RgbColor("31AE34")
        }

        test("sechs Ziffern ohne Buchstaben bleiben eine Farbe und werden keine Zahl") {
            YamlLoader.parseSellerConfig(resource("stammdaten.yaml") + "akzentfarbe: 000000\n").accentColor shouldBe
                RgbColor("000000")
        }

        test("eine ungültige Farbe nennt Datei und Wert") {
            val file = createTempDirectory("loader-test").resolve("stammdaten.yaml")
            file.writeText(resource("stammdaten.yaml") + "akzentfarbe: grün\n")

            val failure = shouldThrow<InputFileException> { YamlLoader.loadSellerConfig(file) }

            failure.message!! shouldContain file.toString()
            failure.message!! shouldContain "'grün'"
        }

        test("ein # ohne Anführungszeichen ist ein Kommentar, und der leere Wert bricht ab") {
            // Sonst käme null an, und die Rechnung erschiene still in der
            // Farbe der Vorlage.
            listOf("akzentfarbe: #31AE34", "akzentfarbe:", "akzentfarbe: ~").forEach { entry ->
                withClue(entry) {
                    val file = createTempDirectory("loader-test").resolve("stammdaten.yaml")
                    file.writeText(resource("stammdaten.yaml") + "$entry\n")

                    val failure = shouldThrow<InputFileException> { YamlLoader.loadSellerConfig(file) }

                    failure.message!! shouldContain file.toString()
                    failure.message!! shouldContain "akzentfarbe steht ohne Wert da"
                    failure.message!! shouldContain "ohne #"
                }
            }
        }

        test("in Anführungszeichen mit # bricht es ebenfalls ab") {
            shouldThrow<SerializationException> {
                YamlLoader.parseSellerConfig(resource("stammdaten.yaml") + "akzentfarbe: \"#31AE34\"\n")
            }.message!! shouldContain "ohne #"
        }
    }

    context("logo") {
        // Stammdaten mit Logo-Eintrag in einem eigenen Verzeichnis; das Logo
        // liegt, wo der Test es hinlegt.
        fun sellerConfigWithLogo(entry: String): Path {
            val file = createTempDirectory("loader-test").resolve("stammdaten.yaml")
            file.writeText(resource("stammdaten.yaml") + "logo: $entry\n")
            return file
        }

        test("ohne Eintrag gibt es kein Logo") {
            val file = createTempDirectory("loader-test").resolve("stammdaten.yaml")
            file.writeText(resource("stammdaten.yaml"))

            YamlLoader.loadSellerConfig(file).logo shouldBe null
        }

        test("ein relativer Pfad gilt ab dem Verzeichnis der Stammdaten") {
            // Nicht ab dem Arbeitsverzeichnis des Tests – das ist ein anderes.
            val file = sellerConfigWithLogo("marke/logo.tex")
            val logoFile = file.resolveSibling("marke").createDirectories().resolve("logo.tex")
            logoFile.writeText("\\fill (0,0) circle (1);")

            val logo = YamlLoader.loadSellerConfig(file).logo!!

            logo.source shouldBe logoFile.toAbsolutePath()
            logo.content.decodeToString() shouldBe "\\fill (0,0) circle (1);"
        }

        test("ein absoluter Pfad gilt, wie er dasteht") {
            val logoFile = createTempDirectory("loader-logo").resolve("logo.tex")
            logoFile.writeText("\\fill (0,0) circle (1);")

            YamlLoader.loadSellerConfig(sellerConfigWithLogo(logoFile.toAbsolutePath().toString())).logo!!.source shouldBe
                logoFile.toAbsolutePath()
        }

        test("eine fehlende Logodatei nennt beide Dateien") {
            val file = sellerConfigWithLogo("gibtsnicht.tex")

            val failure = shouldThrow<InputFileException> { YamlLoader.loadSellerConfig(file) }

            failure.message!! shouldContain file.toString()
            failure.message!! shouldContain file.resolveSibling("gibtsnicht.tex").toAbsolutePath().toString()
            failure.message!! shouldContain "es gibt sie nicht"
        }

        test("eine andere Endung als .tex bricht ab, bevor gelesen wird") {
            // Die Datei gibt es nicht einmal: Die Endung entscheidet vorher.
            val file = sellerConfigWithLogo("logo.png")

            val failure = shouldThrow<InputFileException> { YamlLoader.loadSellerConfig(file) }

            failure.message!! shouldContain file.toString()
            failure.message!! shouldContain "zulässig ist .tex"
        }

        test("die Endung zählt ohne Groß- und Kleinschreibung, weiter geht es klein") {
            // macOS unterscheidet die Schreibweise im Dateinamen nicht, und
            // eine Datei Logo.TEX ist eine .tex-Datei. Weitergereicht wird die
            // Endung klein: Die Vorlage liest \input{logo}, und das findet
            // unter Linux nur logo.tex.
            val file = sellerConfigWithLogo("Logo.TEX")
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

    context("Steuerzeichen (#391)") {
        // Ein echtes Steuerzeichen lässt YAML im Text nicht zu, hinein kommt es
        // über ein Escape in doppelten Anführungszeichen. Die Dateien schreiben
        // deshalb "\x0B" und nicht das Zeichen selbst.
        fun invoiceWith(name: String): String =
            resource("rechnung.yaml").replace("bezeichnung: IT-Beratungsleistungen", "bezeichnung: \"$name\"")

        test("bricht beim Lesen ab und nennt Datei, Feld, Zeile und Zeichen") {
            val file = createTempDirectory("loader-test").resolve("rechnung.yaml")
            file.writeText(invoiceWith("IT-Beratung\\x0Bleistungen"))

            val failure = shouldThrow<InputFileException> { YamlLoader.loadInvoice(file) }

            failure.message!! shouldContain file.toString()
            failure.message!! shouldContain "positionen[0].bezeichnung (Zeile 17, Spalte 18): U+000B"
        }

        test("nennt alle Fundstellen einer Datei auf einmal") {
            val text =
                invoiceWith("IT-Beratung\\x0Bleistungen")
                    .replace("hinweis: Juli 2026", "hinweis: \"Juli\\x07 2026\"")

            val failure = shouldThrow<SerializationException> { YamlLoader.parseInvoice(text) }

            failure.message!! shouldContain "positionen[0].bezeichnung"
            failure.message!! shouldContain "positionen[1].hinweis"
            failure.message!! shouldContain "U+0007"
        }

        test("prüft auch die Stammdaten") {
            val text =
                resource("stammdaten.yaml").replace(
                    "firma: Muster IT-Consulting UG (haftungsbeschränkt)",
                    "firma: \"Muster\\x1B IT-Consulting UG\"",
                )

            val failure = shouldThrow<SerializationException> { YamlLoader.parseSellerConfig(text) }

            failure.message!! shouldContain "firma"
            failure.message!! shouldContain "U+001B"
        }

        test("CR, DEL, C1 und was XML 1.0 sonst verbietet, brechen ab") {
            // CR, weil YAML jedes echte Zeilenende zu LF macht und TeX nach
            // einem CR den Rest der Zeile verschluckt.
            listOf(
                "\\r" to "U+000D",
                "\\x7F" to "U+007F",
                "\\N" to "U+0085",
                "\\uFFFE" to "U+FFFE",
                "\\uFFFF" to "U+FFFF",
                "\\uD800" to "U+D800",
            ).forEach { (escape, codePoint) ->
                withClue(escape) {
                    val failure =
                        shouldThrow<SerializationException> {
                            YamlLoader.parseInvoice(invoiceWith("IT-Beratung${escape}leistungen"))
                        }

                    failure.message!! shouldContain codePoint
                }
            }
        }

        test("Tabulator und Zeilenumbruch bleiben erlaubt") {
            val invoice = YamlLoader.parseInvoice(invoiceWith("IT-Beratung\\tleistungen\\nim Juli"))

            invoice.items.first().name shouldBe "IT-Beratung\tleistungen\nim Juli"
        }
    }
})
