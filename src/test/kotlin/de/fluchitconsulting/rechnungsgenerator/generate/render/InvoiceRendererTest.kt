package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.accepted.accepted
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.CheckMethod
import de.fluchitconsulting.rechnungsgenerator.core.model.CurrencyConversion
import de.fluchitconsulting.rechnungsgenerator.core.model.Design
import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.LineItem
import de.fluchitconsulting.rechnungsgenerator.core.model.Logo
import de.fluchitconsulting.rechnungsgenerator.core.model.Prepayment
import de.fluchitconsulting.rechnungsgenerator.core.model.SellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.model.ServicePeriod
import de.fluchitconsulting.rechnungsgenerator.core.model.SkontoTier
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.model.VatIdCheck
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.core.model.exampleInvoices
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testItem
import de.fluchitconsulting.rechnungsgenerator.core.model.testPrepayment
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.values.CountryCode
import de.fluchitconsulting.rechnungsgenerator.core.values.Currency
import de.fluchitconsulting.rechnungsgenerator.core.values.ExchangeRate
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent
import de.fluchitconsulting.rechnungsgenerator.core.values.RgbColor
import de.fluchitconsulting.rechnungsgenerator.core.values.Unit
import de.fluchitconsulting.rechnungsgenerator.core.values.VatId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Path
import java.time.LocalDate
import java.time.YearMonth

private fun resource(name: String): String =
    checkNotNull(InvoiceRendererTest::class.java.getResource("/$name")).readText()

// #204. Die leeren fehlen, damit eine neue, optionale Angabe keine Vergleichsdatei berührt.
private fun contentOf(values: Map<TemplateValue, Latex>): String =
    TemplateValue.entries
        .map { it to values.getValue(it).tex }
        .filter { (_, tex) -> tex.isNotEmpty() }
        .joinToString("\n") { (key, tex) -> "<<${key.name}>>\n$tex\n" }

class InvoiceRendererTest : FunSpec({

    val config = testSellerConfig()

    // Der Pfad ist nur ein Name, die Bytes liest der Test selbst.
    val exampleLogo = Logo(Path.of("logo.tex"), resource("logo.tex").toByteArray())

    fun renderWith(vararg items: de.fluchitconsulting.rechnungsgenerator.core.model.LineItem): String {
        val invoice = testInvoice(items = items.toList())
        return InvoiceRenderer.render(CalculatedInvoice(invoice, config).accepted()).sources.tex
    }

    // Ob eine Angabe dasteht, zeigt ihr Wert, denn die Beschriftung steht in der Vorlage (#188).
    fun valueOf(
        key: TemplateValue,
        invoice: Invoice = testInvoice(),
        sellerConfig: SellerConfig = config,
    ): String = InvoiceRenderer.values(CalculatedInvoice(invoice, sellerConfig).accepted()).getValue(key).tex

    test("eine Position ohne Zusatzangaben bleibt einzeilig") {
        val tex = renderWith(testItem())

        tex shouldContain "Testposition"
        // Der Aufruf, nicht das Wort: Die Vorlage definiert das Makro.
        tex shouldNotContain "Testposition\\positionsdetail"
    }

    test("jede Position trägt ihre laufende Nummer, beginnend bei 1") {
        // Ob ein Design sie setzt, entscheidet die Vorlage; helsinki nicht (#188).
        val tex = renderWith(testItem().copy(name = "Erste"), testItem().copy(name = "Zweite"))

        tex shouldContain "\\positionszeile{1}{Erste}"
        tex shouldContain "\\positionszeile{2}{Zweite}"
    }

    test("die Beschreibung wird zur zweiten Zeile, klein und gedämpft") {
        val tex = renderWith(testItem(description = "Rahmenvertrag vom 01.02.2026"))

        // #51
        tex shouldContain "Testposition\\positionsdetail{Rahmenvertrag vom 01.02.2026}"
    }

    test("Beschreibung und Hinweis stehen zusammen in der zweiten Zeile") {
        val tex = renderWith(testItem(description = "Arbeitspaket Nord", note = "Abschlag September 2026"))

        tex shouldContain "Arbeitspaket Nord · Abschlag September 2026"
    }

    test("ein abweichender Zeitraum der Position wird benannt") {
        // Vor dem Rechnungsdatum, denn auch eine Position rechnet Erbrachtes ab (#47).
        val item =
            testItem(description = "Arbeitspaket Nord").copy(
                servicePeriod = ServicePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)),
            )

        renderWith(item) shouldContain "Arbeitspaket Nord · Zeitraum: Juni 2026"
    }

    test("Sonderzeichen werden auch in den Zusatzfeldern maskiert") {
        val tex = renderWith(testItem(description = "Meier & Söhne", note = "50 % Anzahlung"))

        tex shouldContain "Meier \\& Söhne · 50 \\% Anzahlung"
    }

    // Wird er rot, ist das eine Frage und keine Aufgabe: Der Satz einer
    // gewöhnlichen Rechnung soll sich nicht nebenbei ändern.
    test("eine Rechnungsdatei ohne art wird gesetzt wie erwartet") {
        val seller = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))
        val invoice = YamlLoader.parseInvoice(resource("rechnung.yaml"))

        InvoiceRenderer.render(CalculatedInvoice(invoice, seller).accepted()).sources.tex shouldBe
            resource("rechnung-erwartet.tex")
    }

    // Je Art der Inhalt, je Design der Volltext nur der gewöhnlichen Rechnung
    // (#204). Ohne Logo, das sichert der Volltext von prag.
    test("jede Rechnungsart hat eine Beispieldatei") {
        exampleInvoices.keys shouldBe InvoiceType.entries.toSet()
    }

    test("jede Rechnungsart bekommt die Werte wie festgehalten") {
        val seller = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))

        exampleInvoices.values.forEach { name ->
            val invoice = YamlLoader.parseInvoice(resource("$name.yaml"))

            withClue(name) {
                contentOf(InvoiceRenderer.values(CalculatedInvoice(invoice, seller).accepted())) shouldBe
                    resource("$name-inhalt.tex")
            }
        }
    }

    // #405, #407. Die Freitexte der Dateien bleiben deutsch.
    test("jede Rechnungsart bekommt auf Englisch die Werte wie festgehalten") {
        val seller = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))

        listOf(Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH).forEach { language ->
            exampleInvoices.values.forEach { name ->
                val invoice = YamlLoader.parseInvoice("sprache: ${language.tag}\n" + resource("$name.yaml"))
                val expected = "$name-${language.tag.lowercase()}-inhalt.tex"

                withClue(expected) {
                    contentOf(InvoiceRenderer.values(CalculatedInvoice(invoice, seller).accepted())) shouldBe resource(expected)
                }
            }
        }
    }

    test("eine Rechnung im Design prag wird gesetzt wie festgehalten") {
        // Mit Logo (#198), sonst stünde in der Vergleichsdatei nur ein leeres \ifblank.
        val seller =
            YamlLoader.parseSellerConfig(resource("stammdaten.yaml")).copy(design = Design.PRAG, logo = exampleLogo)
        val invoice = YamlLoader.parseInvoice(resource("rechnung.yaml"))

        InvoiceRenderer.render(CalculatedInvoice(invoice, seller).accepted()).sources.tex shouldBe
            resource("prag-rechnung-erwartet.tex")
    }

    // #377, #407; wie oben eine Frage und keine Aufgabe.
    test("eine englische und eine amerikanische Rechnung werden in jedem Design gesetzt wie festgehalten") {
        val seller = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))

        listOf("englische-rechnung", "amerikanische-rechnung").forEach { name ->
            val invoice = YamlLoader.parseInvoice(resource("$name.yaml"))

            mapOf(
                seller to "$name-erwartet.tex",
                seller.copy(design = Design.PRAG, logo = exampleLogo) to "prag-$name-erwartet.tex",
            ).forEach { (sellerConfig, expected) ->
                withClue(expected) {
                    InvoiceRenderer.render(CalculatedInvoice(invoice, sellerConfig).accepted()).sources.tex shouldBe
                        resource(expected)
                }
            }
        }
    }

    context("logo (#198)") {
        fun typeset(design: Design, logo: Logo?) =
            InvoiceRenderer.render(CalculatedInvoice(testInvoice(), config.copy(design = design, logo = logo)).accepted())

        test("der Platzhalter ist ein Schalter: der Name der Datei oder leer") {
            valueOf(TemplateValue.LOGO, sellerConfig = config.copy(logo = exampleLogo)) shouldBe "logo"
            valueOf(TemplateValue.LOGO) shouldBe ""
        }

        test("jedes Design bindet das Logo ein und bekommt es als logo.tex mit") {
            Design.entries.forEach { design ->
                withClue(design) {
                    val rendered = typeset(design, exampleLogo)

                    rendered.sources.tex shouldContain "\\input{logo}"
                    rendered.sources.files.keys shouldBe setOf(Foundation.FILE_NAME, Template.LANGUAGE_FILE_NAME, "logo.tex")
                    rendered.sources.files.getValue("logo.tex") shouldBe exampleLogo.content
                }
            }
        }

        test("ohne Logo gibt es in keinem Design eine Datei dafür, nur die Grundlage") {
            Design.entries.forEach { design ->
                withClue(design) {
                    typeset(design, null).sources.files.keys shouldBe
                        setOf(Foundation.FILE_NAME, Template.LANGUAGE_FILE_NAME)
                }
            }
        }

        // Ausdrücklich, weil das Kommando es in einer Fehlermeldung nennt (#203).
        test("mit Logo zeigt es jedes Design") {
            Design.entries.forEach { design ->
                withClue(design) { typeset(design, exampleLogo).includesLogo shouldBe true }
            }
        }

        test("ohne Logo in den Stammdaten zeigt es kein Design, auch wenn die Vorlage den Platzhalter trägt") {
            Design.entries.forEach { design ->
                withClue(design) { typeset(design, null).includesLogo shouldBe false }
            }
        }
    }

    context("akzentfarbe (#371)") {
        fun typeset(design: Design, accentColor: RgbColor?) =
            InvoiceRenderer
                .render(CalculatedInvoice(testInvoice(), config.copy(design = design, accentColor = accentColor)).accepted())
                .sources.tex

        test("der Platzhalter ist die Farbe oder leer") {
            valueOf(TemplateValue.AKZENTFARBE, sellerConfig = config.copy(accentColor = RgbColor("31ae34"))) shouldBe "31AE34"
            valueOf(TemplateValue.AKZENTFARBE) shouldBe ""
        }

        test("helsinki setzt sie als Farbe des Balkens, ohne Eintrag die eigene") {
            typeset(Design.HELSINKI, RgbColor("31AE34")) shouldContain
                "\\ifblank{31AE34}{\\definecolor{akzent}{HTML}{8D313A}}{\\definecolor{akzent}{HTML}{31AE34}}"
            typeset(Design.HELSINKI, null) shouldContain
                "\\ifblank{}{\\definecolor{akzent}{HTML}{8D313A}}"
        }

        test("prag hat keine Akzentfarbe und übergeht sie") {
            typeset(Design.PRAG, RgbColor("31AE34")) shouldBe typeset(Design.PRAG, null)
        }
    }

    context("Die Kommentarzeilen der Vorlage") {
        // #149

        test("im gesetzten LaTeX steht keine Kommentarzeile der Vorlage") {
            renderWith(testItem()).lines().filter { it.trimStart().startsWith("%") }.shouldBeEmpty()
        }
    }

    context("Das Land in der Anschrift") {

        fun address(customerCountry: String, sellerCountry: String): String {
            val basis = testInvoice()
            val invoice =
                basis.copy(
                    customer =
                        basis.customer.copy(
                            address = basis.customer.address.copy(countryCode = CountryCode(customerCountry)),
                        ),
                )
            val sellerConfig = config.copy(address = config.address.copy(countryCode = CountryCode(sellerCountry)))
            return valueOf(TemplateValue.EMPFAENGER, invoice, sellerConfig)
        }

        test("ein kleingeschriebenes Land ist dasselbe Land") {
            address(customerCountry = "de", sellerCountry = "DE") shouldBe
                """Beispiel Software GmbH\zeilenwechsel{}Industriestraße 1\zeilenwechsel{}90402 Nürnberg"""
        }

        test("Inland ist relativ zum Rechnungssteller, nicht zu Deutschland") {
            address(customerCountry = "DE", sellerCountry = "AT") shouldBe
                """Beispiel Software GmbH\zeilenwechsel{}Industriestraße 1\zeilenwechsel{}90402 Nürnberg\zeilenwechsel{}\landzeile{Deutschland}"""
        }

        test("das Land des Kunden wird genannt, wenn es ein anderes ist, mit seinem Namen und nicht dem Code") {
            address(customerCountry = "at", sellerCountry = "DE") shouldBe
                """Beispiel Software GmbH\zeilenwechsel{}Industriestraße 1\zeilenwechsel{}90402 Nürnberg\zeilenwechsel{}\landzeile{Österreich}"""
        }

        test("ohne Postleitzahl steht der Ort allein (#512)") {
            val basis = testInvoice()
            val address = basis.customer.address.copy(postalCode = null, city = "Dubai", countryCode = CountryCode("AE"))
            val invoice = basis.copy(customer = basis.customer.copy(address = address))

            valueOf(TemplateValue.EMPFAENGER, invoice) shouldContain """Industriestraße 1\zeilenwechsel{}Dubai\zeilenwechsel{}\landzeile{"""
        }

        // EL ist Griechenland nur in der Umsatzsteuer der EU; ZZ nennt das JDK
        // "Unbekannte Region".
        for (code in listOf("EL", "ZZ", "XX")) {
            test("der Code $code ohne Namen steht als Code da") {
                address(customerCountry = code, sellerCountry = "DE") shouldEndWith """\zeilenwechsel{}\landzeile{$code}"""
            }
        }
    }

    test("ein Kleinstpreis steht so da, dass man die Zeile nachrechnen kann") {
        // #14
        val tex = renderWith(testItem(quantity = "10000", unitPrice = "0.00001"))

        tex shouldContain "{\\menge{10000}}{Std.}{\\betrag{0.00001}}{}{\\betrag{0.10}}"
    }

    context("Abschlagsrechnung") {
        fun abschlag(receivedOn: LocalDate? = null): String {
            val invoice = testInvoice(type = InvoiceType.PREPAYMENT, receivedOn = receivedOn)
            return InvoiceRenderer.render(CalculatedInvoice(invoice, config).accepted()).sources.tex
        }

        test("der Balken nennt die Rechnungsart") {
            abschlag() shouldContain "Abschlagsrechnung R26-001"
        }

        test("der voraussichtliche Zeitraum steht in Monaten") {
            val invoice = YamlLoader.parseInvoice(resource("abschlagsrechnung.yaml"))
            val seller = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))

            valueOf(TemplateValue.LEISTUNGSZEITRAUM, invoice, seller) shouldBe "Juni bis Oktober 2026"
        }

        test("der Zeitraum ist der voraussichtliche") {
            // UStAE 14.8 Abs. 1. Vom Renderer und nicht aus der Vorlage, weil die
            // Beschriftung eine fachliche Aussage trägt (#188).
            valueOf(TemplateValue.ZEITRAUM_BESCHRIFTUNG, testInvoice(type = InvoiceType.PREPAYMENT)) shouldBe
                "Voraussichtlicher Leistungszeitraum"
            valueOf(TemplateValue.ZEITRAUM_BESCHRIFTUNG) shouldBe "Leistungszeitraum"
            valueOf(TemplateValue.LEISTUNGSZEITRAUM) shouldBe "Juli 2026"
        }

        test("der Pflichtsatz steht unter den Summen") {
            val tex = abschlag()

            tex shouldContain "Die Leistung ist noch nicht erbracht."
            // Unter dem Gesamtbetrag, als Vorbehalt zu genau dieser Summe.
            tex.indexOf("Die Leistung ist noch nicht erbracht.") shouldBeGreaterThan tex.indexOf("\\gesamtzeile{")
            renderWith(testItem()) shouldNotContain "noch nicht erbracht"
        }

        test("auch der Zeitraum einer Position ist der voraussichtliche") {
            val item =
                testItem(description = "Arbeitspaket Nord").copy(
                    servicePeriod = ServicePeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
                )
            val invoice = testInvoice(type = InvoiceType.PREPAYMENT, items = listOf(item))

            InvoiceRenderer.render(CalculatedInvoice(invoice, config).accepted()).sources.tex shouldContain
                "Voraussichtlicher Zeitraum: September 2026"
        }

        // Steht in keiner Beispieldatei (#405).
        test("auf Englisch heißt der Zeitraum einer Position Expected period") {
            val item =
                testItem().copy(servicePeriod = ServicePeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
            val invoice = testInvoice(type = InvoiceType.PREPAYMENT, items = listOf(item), language = Language.BRITISH_ENGLISH)

            InvoiceRenderer.render(CalculatedInvoice(invoice, config).accepted()).sources.tex shouldContain
                "Expected period: September 2026"
        }

        test("die Vereinnahmung wird genannt, sobald sie feststeht") {
            val vereinnahmt = testInvoice(type = InvoiceType.PREPAYMENT, receivedOn = LocalDate.of(2026, 8, 10))

            valueOf(TemplateValue.VEREINNAHMT_AM, vereinnahmt) shouldBe "10.~August 2026"
            valueOf(TemplateValue.VEREINNAHMT_AM, testInvoice(type = InvoiceType.PREPAYMENT)) shouldBe ""
        }

        test("die Metadaten des PDF tragen dieselbe Bezeichnung") {
            val invoice = testInvoice(type = InvoiceType.PREPAYMENT)

            val xmp = InvoiceRenderer.render(CalculatedInvoice(invoice, config).accepted()).sources.xmpMetadata

            xmp shouldContain "\\Title{Abschlagsrechnung R26-001}"
            xmp shouldContain "\\Subject{Abschlagsrechnung R26-001 vom 14. August 2026}"
        }
    }
    context("Schlussrechnung") {
        fun finalInvoice(items: List<LineItem>, vararg prepayments: Prepayment): String {
            val invoice = testInvoice(type = InvoiceType.FINAL, items = items, prepayments = prepayments.toList())
            return InvoiceRenderer.render(CalculatedInvoice(invoice, config).accepted()).sources.tex
        }

        val totalService = listOf(testItem(quantity = "1", unitPrice = "20000.00"))

        test("die Abschläge stehen unter dem Gesamtbetrag, nicht in der Positionstabelle") {
            // ADR-0002
            val tex = finalInvoice(totalService, testPrepayment())

            tex.indexOf("R26-009") shouldBeGreaterThan tex.indexOf("\\gesamtzeile{")
        }

        test("jeder Abschlag steht einzeln, mit Netto und Steuer") {
            // UStAE 14.8 Abs. 7 ließe die Summe genügen.
            val tex =
                finalInvoice(
                    totalService,
                    testPrepayment(invoiceNumber = "R26-009"),
                    testPrepayment(
                        invoiceNumber = "R26-011",
                        issueDate = LocalDate.of(2026, 7, 20),
                        receivedOn = LocalDate.of(2026, 7, 25),
                    ),
                )

            tex shouldContain "Abschlagsrechnung R26-009 vom 15.06.2026, vereinnahmt am 10.06.2026"
            tex shouldContain "Abschlagsrechnung R26-011 vom 20.07.2026, vereinnahmt am 25.07.2026"
            tex shouldContain "netto \\betrag{4000.00} zzgl. \\prozent{19} USt. \\betrag{760.00}"
            // Das Minuszeichen wählt die Vorlage (ADR-0021).
            tex shouldContain "\\betrag{-4760.00}"
        }

        test("der Restbetrag steht unter dem Strich, und darunter die Bankverbindung") {
            val tex = finalInvoice(totalService, testPrepayment())

            tex shouldContain "\\abschlusszeile{Restbetrag}{\\betrag{19040.00}}"
            // Der Aufruf, nicht das Wort: Die Vorlage definiert \bankblock.
            tex shouldContain "\\bankblock{}"
        }

        test("ein Restbetrag von null bekommt einen Satz statt einer Zahl") {
            val tex = finalInvoice(listOf(testItem(quantity = "1", unitPrice = "4000.00")), testPrepayment())

            tex shouldContain "es verbleibt kein Restbetrag"
            // #94
            tex shouldNotContain "\\abschlusszeile{Restbetrag}"
            val settled =
                testInvoice(
                    type = InvoiceType.FINAL,
                    items = listOf(testItem(quantity = "1", unitPrice = "4000.00")),
                    prepayments = listOf(testPrepayment()),
                )
            valueOf(TemplateValue.ZAHLUNGSZIEL, settled) shouldBe ""
            valueOf(TemplateValue.ZAHLUNGSZIEL) shouldBe "28.~August 2026"
            // Die IBAN im Fuß bleibt.
            valueOf(TemplateValue.BANKVERBINDUNG, settled) shouldBe ""
        }

        test("eine gewöhnliche Rechnung bekommt keine Absetzung") {
            renderWith(testItem()) shouldNotContain "Abzüglich"
        }

        // Der Satz statt der Null steht in keiner Inhalts-Datei (#405).
        test("auf Englisch bekommt ein Restbetrag von null den englischen Satz") {
            val invoice =
                testInvoice(
                    type = InvoiceType.FINAL,
                    items = listOf(testItem(quantity = "1", unitPrice = "4000.00")),
                    prepayments = listOf(testPrepayment()),
                    language = Language.BRITISH_ENGLISH,
                )

            InvoiceRenderer.render(CalculatedInvoice(invoice, config).accepted()).sources.tex shouldContain
                "\\hinweisabsatz{The invoice amount is settled by the advance payments received; no balance remains.}"
        }
    }

    context("Skonto") {
        // Rechnungsdatum 14.08.2026, Zahlungsziel 14 Tage.
        fun withSkonto(vararg stufen: SkontoTier): String =
            InvoiceRenderer.render(CalculatedInvoice(testInvoice(skonto = stufen.toList()), config).accepted()).sources.tex

        test("eine Stufe wird ein Satz unter den Summen") {
            val tex = withSkonto(SkontoTier(rate = Percent("2"), days = 7))

            tex shouldContain
                "\\hinweisabsatz{Bei Zahlung bis~\\zusammen{21.~August 2026} können \\prozent{2} " +
                "Skonto abgezogen werden.}"
            tex.indexOf("Skonto abgezogen") shouldBeGreaterThan tex.indexOf("\\gesamtzeile{")
        }

        test("weitere Stufen hängen sich nach der Frist sortiert an") {
            val tex = withSkonto(SkontoTier(rate = Percent("1.5"), days = 10), SkontoTier(rate = Percent("2"), days = 7))

            tex shouldContain
                "\\hinweisabsatz{Bei Zahlung bis~\\zusammen{21.~August 2026} können \\prozent{2} " +
                "Skonto abgezogen werden, bei Zahlung bis~\\zusammen{24.~August 2026} \\prozent{1.5}.}"
        }

        test("das Zahlungsziel im Kopf bleibt, wie es ist") {
            // Kein Datum doppelt, das hält SKONTOFRIST_NICHT_VOR_ZAHLUNGSZIEL.
            valueOf(TemplateValue.ZAHLUNGSZIEL, testInvoice(skonto = listOf(SkontoTier(rate = Percent("2"), days = 7)))) shouldBe
                "28.~August 2026"
        }

        test("ohne Skonto kein Satz") {
            renderWith(testItem()) shouldNotContain "Skonto"
        }
    }

    context("Umrechnung (#418)") {
        // 19.00 $ / 1.1685 = 16.2601… -> 16.26 €
        fun inDollar(
            language: Language = Language.GERMAN,
            skonto: List<SkontoTier> = emptyList(),
        ) = testInvoice(
            currency = Currency.USD,
            conversion = CurrencyConversion(ExchangeRate("1.1685"), YearMonth.of(2026, 7)),
            language = language,
            skonto = skonto,
        )

        test("die Umsatzsteuer in Euro wird ein Satz mit Kurs und Monat") {
            valueOf(TemplateValue.UMRECHNUNG, inDollar()) shouldBe
                "\\hinweisabsatz{Umgerechnet in Euro beträgt die Umsatzsteuer \\eurobetrag{16.26} " +
                "(\\zusammen{\\eurobetrag{1} = \\betrag{1.1685}}, Monatsdurchschnittskurs des BMF für Juli 2026).}"
        }

        test("auf Englisch nennt der Satz das Ministerium ausgeschrieben") {
            listOf(Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH).forEach { language ->
                withClue(language) {
                    valueOf(TemplateValue.UMRECHNUNG, inDollar(language)) shouldBe
                        "\\hinweisabsatz{Converted to euros, the VAT amounts to \\eurobetrag{16.26} " +
                        "(\\zusammen{\\eurobetrag{1} = \\betrag{1.1685}}, monthly average rate of the " +
                        "German Federal Ministry of Finance for July 2026).}"
                }
            }
        }

        test("ohne Umrechnung kein Satz") {
            valueOf(TemplateValue.UMRECHNUNG, testInvoice()) shouldBe ""
        }

        test("der Satz steht unter den Summen und vor dem Skonto") {
            Design.entries.forEach { design ->
                withClue(design) {
                    val invoice = inDollar(skonto = listOf(SkontoTier(rate = Percent("2"), days = 7)))
                    val tex =
                        InvoiceRenderer
                            .render(CalculatedInvoice(invoice, config.copy(design = design)).accepted())
                            .sources.tex

                    tex.indexOf("Umgerechnet in Euro") shouldBeGreaterThan tex.indexOf("\\gesamtzeile{")
                    tex.indexOf("Skonto abgezogen") shouldBeGreaterThan tex.indexOf("Umgerechnet in Euro")
                }
            }
        }
    }

    context("Zahlungsfrist") {
        // #211; die Stammdaten des Tests haben 14 Tage.
        fun paymentTerm(invoice: Invoice): String = valueOf(TemplateValue.ZAHLUNGSFRIST, invoice)

        test("mehrere Tage ab Rechnungsdatum") {
            paymentTerm(testInvoice()) shouldBe "\\hinweisabsatz{Die Zahlung ist 14 Tage nach Rechnungsdatum fällig.}"
        }

        test("ein Tag in der Einzahl") {
            paymentTerm(testInvoice(paymentTermDaysOverride = 1)) shouldBe
                "\\hinweisabsatz{Die Zahlung ist 1 Tag nach Rechnungsdatum fällig.}"
        }

        test("null Tage heißt sofort") {
            paymentTerm(testInvoice(paymentTermDaysOverride = 0)) shouldBe "\\hinweisabsatz{Die Zahlung ist sofort fällig.}"
        }

        test("mit Skonto ohne Abzug") {
            paymentTerm(testInvoice(skonto = listOf(SkontoTier(rate = Percent("2"), days = 7)))) shouldBe
                "\\hinweisabsatz{Die Zahlung ist ohne Abzug 14 Tage nach Rechnungsdatum fällig.}"
        }

        test("ohne Zahlungsziel kein Satz") {
            // #94
            val settled =
                testInvoice(
                    type = InvoiceType.FINAL,
                    items = listOf(testItem(quantity = "1", unitPrice = "4000.00")),
                    prepayments = listOf(testPrepayment()),
                )
            paymentTerm(settled) shouldBe ""
        }
    }

    context("Englisch (#377)") {
        fun english(
            items: List<LineItem> = listOf(testItem()),
            paymentTermDaysOverride: Int? = null,
            skonto: List<SkontoTier> = emptyList(),
        ) = testInvoice(
            items = items,
            paymentTermDaysOverride = paymentTermDaysOverride,
            skonto = skonto,
            language = Language.BRITISH_ENGLISH,
        )

        test("Name des Dokuments und Beschriftung des Zeitraums") {
            valueOf(TemplateValue.DOKUMENTART, english()) shouldBe "Invoice"
            valueOf(TemplateValue.ZEITRAUM_BESCHRIFTUNG, english()) shouldBe "Service period"
        }

        test("die Zahlungsfrist in allen drei Formen") {
            valueOf(TemplateValue.ZAHLUNGSFRIST, english()) shouldBe
                "\\hinweisabsatz{Payment is due 14 days after the invoice date.}"
            valueOf(TemplateValue.ZAHLUNGSFRIST, english(paymentTermDaysOverride = 1)) shouldBe
                "\\hinweisabsatz{Payment is due 1 day after the invoice date.}"
            valueOf(TemplateValue.ZAHLUNGSFRIST, english(paymentTermDaysOverride = 0)) shouldBe
                "\\hinweisabsatz{Payment is due immediately.}"
        }

        // "in full" wie das "Due in full by" im Kopf von helsinki (#406).
        test("die Zahlungsfrist mit Skonto") {
            valueOf(TemplateValue.ZAHLUNGSFRIST, english(skonto = listOf(SkontoTier(rate = Percent("2"), days = 7)))) shouldBe
                "\\hinweisabsatz{Payment is due in full 14 days after the invoice date.}"
        }

        // "of 2%" statt "a 2%": Der Artikel hinge an der Aussprache der Zahl (#406).
        test("Skonto mit zwei Stufen, nach der Frist sortiert") {
            val skonto = listOf(SkontoTier(rate = Percent("1.5"), days = 10), SkontoTier(rate = Percent("2"), days = 7))

            valueOf(TemplateValue.SKONTO, english(skonto = skonto)) shouldBe
                "\\hinweisabsatz{For payment by~\\zusammen{21~August 2026}, an early payment discount of \\prozent{2} " +
                "may be deducted; for payment by~\\zusammen{24~August 2026}, \\prozent{1.5}.}"
        }

        test("die Einheit und der Zeitraum einer Position") {
            val item =
                testItem().copy(
                    unit = Unit.DAY,
                    servicePeriod = ServicePeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15)),
                )
            val positions = valueOf(TemplateValue.POSITIONEN, english(items = listOf(item)))

            positions shouldContain "{day}"
            positions shouldContain "Period: "
        }

        test("der Steuerhinweis sagt nicht steuerbefreit") {
            val item = testItem(vatRate = "0", taxCategory = TaxCategory.OUT_OF_SCOPE, taxExemptionReason = "Not subject to German VAT")

            valueOf(TemplateValue.STEUERHINWEISE, english(items = listOf(item))) shouldBe
                "\\steuerhinweis{No VAT is charged on \\betrag{100.00}: Not subject to German VAT.}"
        }

        test("Rechnungsdatum, Zahlungsziel und Zeitraum britisch") {
            valueOf(TemplateValue.RECHNUNGSDATUM, english()) shouldBe "14~August 2026"
            valueOf(TemplateValue.ZAHLUNGSZIEL, english()) shouldBe "28~August 2026"
            valueOf(TemplateValue.LEISTUNGSZEITRAUM, english()) shouldBe "July 2026"
        }

        test("das Land in der Sprache der Rechnung") {
            val invoice = english()
            val inBritain =
                invoice.copy(customer = invoice.customer.copy(address = invoice.customer.address.copy(countryCode = CountryCode("GB"))))

            valueOf(TemplateValue.EMPFAENGER, inBritain) shouldContain "\\landzeile{United Kingdom}"
        }

        test("die Metadaten nennen Sprache, Titel und Betreff auf Englisch") {
            val metadata = InvoiceRenderer.render(CalculatedInvoice(english(), config).accepted()).sources.xmpMetadata

            metadata shouldContain "\\Title{Invoice R26-001}"
            metadata shouldContain "\\Subject{Invoice R26-001 dated 14 August 2026}"
            metadata shouldContain "\\Language{en-GB}"
        }

        test("ohne Pflichtsatz, mit dem englischen Schlusssatz") {
            valueOf(TemplateValue.ARTHINWEIS, english()) shouldBe ""
            valueOf(TemplateValue.SCHLUSSSATZ, english()) shouldBe "Thank you for your trust and the pleasant collaboration."
        }

        test("auf en-US das Datum amerikanisch, im Kopf und in den Metadaten (#407)") {
            val american = english().copy(language = Language.AMERICAN_ENGLISH)
            val metadata = InvoiceRenderer.render(CalculatedInvoice(american, config).accepted()).sources.xmpMetadata

            valueOf(TemplateValue.RECHNUNGSDATUM, american) shouldBe "August~14, 2026"
            metadata shouldContain "\\Subject{Invoice R26-001 dated August 14, 2026}"
            metadata shouldContain "\\Language{en-US}"
        }
    }

    context("Pflichtangaben und was fehlen darf") {
        // Keine dieser Angaben steht in einer Vergleichsdatei (#95).

        fun render(invoice: Invoice, sellerConfig: SellerConfig = config): String =
            InvoiceRenderer.render(CalculatedInvoice(invoice, sellerConfig).accepted()).sources.tex

        // Reverse Charge verlangt die USt-IdNr. des Kunden.
        fun withReverseCharge() =
            testInvoice(
                items =
                    listOf(
                        testItem(),
                        testItem(unitPrice = "200.00", vatRate = "0", taxCategory = TaxCategory.REVERSE_CHARGE),
                    ),
                customerVatId = VatId("DE123456788"),
            )

        test("eine Position ohne Umsatzsteuer bekommt ihren Grund genannt") {
            // BT-120, §14a Abs. 1 und 5 UStG
            render(withReverseCharge()) shouldContain
                "Auf \\betrag{200.00} entfällt keine Umsatzsteuer: Steuerschuldnerschaft des Leistungsempfängers (Reverse Charge)."
        }

        // „Reverse charge“ ist der englische Begriff aus Anlage 8 zum UStAE
        // (BMF vom 17.09.2025), zulässig statt des deutschen (#406).
        test("auf Englisch heißt der Grund bei Reverse Charge Reverse charge") {
            render(withReverseCharge().copy(language = Language.BRITISH_ENGLISH)) shouldContain
                "No VAT is charged on \\betrag{200.00}: Reverse charge."
        }

        // Kein vorgeschriebener Wortlaut, nur dieselbe fachliche Aussage wie der
        // deutsche (UStAE 14.5 Abs. 20, #406).
        listOf(
            TaxCategory.INTRA_COMMUNITY to "Exempt intra-Community supply of goods",
            TaxCategory.EXPORT to "Exempt export of goods",
        ).forEach { (category, reason) ->
            test("auf Englisch heißt der Grund bei ${category.code} $reason") {
                val invoice =
                    testInvoice(
                        items = listOf(testItem(unitPrice = "200.00", vatRate = "0", taxCategory = category)),
                        customerVatId = VatId("ATU12345678"),
                        language = Language.BRITISH_ENGLISH,
                    )
                // K verlangt die Bestätigungsabfrage immer, G nie.
                val checked =
                    invoice.copy(
                        customer =
                            invoice.customer.copy(
                                vatIdCheck = VatIdCheck(LocalDate.of(2026, 8, 1), CheckMethod.QUALIFIED, "BZSt-Protokoll 4711"),
                            ),
                    )

                render(checked) shouldContain "No VAT is charged on \\betrag{200.00}: $reason."
            }
        }

        test("eine Steuergruppe ohne Steuerbetrag steht nicht in den Summen") {
            val tex = render(withReverseCharge())

            tex shouldContain "\\steuerzeile{\\prozent{19}}"
            tex shouldNotContain "\\steuerzeile{\\prozent{0}"
        }

        test("jeder Hinweis auf eine Steuerbefreiung ist ein eigener Satz") {
            // Zwischen zwei Hinweisen darf die Seite brechen, in einem nicht (ADR-0020).
            val tex =
                render(
                    testInvoice(
                        items =
                            listOf(
                                testItem(
                                    vatRate = "0",
                                    taxCategory = TaxCategory.EXEMPT,
                                    taxExemptionReason = "Steuerfrei nach §4 Nr. 21 UStG",
                                ),
                                testItem(
                                    unitPrice = "300.00",
                                    vatRate = "0",
                                    taxCategory = TaxCategory.OUT_OF_SCOPE,
                                    taxExemptionReason = "Nicht im Inland steuerbar",
                                ),
                            ),
                    ),
                )

            tex shouldContain
                "\\steuerhinweis{Auf \\betrag{100.00} entfällt keine Umsatzsteuer: Steuerfrei nach §4 Nr. 21 UStG.}\n" +
                "\\steuerhinweis{Auf \\betrag{300.00} entfällt keine Umsatzsteuer: Nicht im Inland steuerbar.}"
        }

        test("die USt-IdNr. des Kunden steht unter seiner Anschrift, wenn er eine hat") {
            // §14a UStG
            val customerVatId = VatId("ATU12345678")

            valueOf(TemplateValue.KUNDE_USTID, testInvoice(customerVatId = customerVatId)) shouldBe customerVatId.formatted()
            valueOf(TemplateValue.KUNDE_USTID) shouldBe ""
        }

        test("der Ansprechpartner steht unter dem Namen und vor der Straße, wenn es einen gibt") {
            // BT-56 (#196). Postkonvention, deshalb im Block und kein eigener Platzhalter.
            valueOf(TemplateValue.EMPFAENGER, testInvoice(contactPoint = "Herrn Max Beispiel")) shouldBe
                """Beispiel Software GmbH\zeilenwechsel{}Herrn Max Beispiel\zeilenwechsel{}Industriestraße 1\zeilenwechsel{}90402 Nürnberg"""
            valueOf(TemplateValue.EMPFAENGER) shouldBe
                """Beispiel Software GmbH\zeilenwechsel{}Industriestraße 1\zeilenwechsel{}90402 Nürnberg"""
        }

        test("die Kundennummer kommt maskiert, und ohne sie ist der Wert leer") {
            // BT-46 (#190)
            valueOf(TemplateValue.KUNDENNUMMER, testInvoice(buyerIdentifier = "K-17 & Co")) shouldBe "K-17 \\& Co"
            valueOf(TemplateValue.KUNDENNUMMER) shouldBe ""
        }

        test("die Auftragsnummer kommt maskiert, und ohne sie ist der Wert leer") {
            // BT-14 (#191)
            valueOf(TemplateValue.AUFTRAGSNUMMER, testInvoice(salesOrderReference = "A_2026_014")) shouldBe "A\\_2026\\_014"
            valueOf(TemplateValue.AUFTRAGSNUMMER) shouldBe ""
        }

        test("die Einleitung kommt maskiert, und ohne sie ist der Wert leer") {
            // #209
            valueOf(TemplateValue.EINLEITUNG, testInvoice(introduction = "Für Projekt A & B:")) shouldBe
                "Für Projekt A \\& B:"
            valueOf(TemplateValue.EINLEITUNG) shouldBe ""
        }

        test("Geschäftsführung, Telefon, Registergericht und Referenz sind leer, wenn sie fehlen") {
            // #188
            valueOf(TemplateValue.GESCHAEFTSFUEHRUNG) shouldBe ""
            valueOf(TemplateValue.TELEFON) shouldBe ""
            valueOf(TemplateValue.REGISTERGERICHT) shouldBe ""
            valueOf(TemplateValue.REGISTERNUMMER) shouldBe ""
            valueOf(TemplateValue.KUNDENREFERENZ) shouldBe ""

            val basis = testInvoice()
            val withReference = basis.copy(customer = basis.customer.copy(buyerReference = "Bestellung 4711"))
            val sellerConfig =
                config.copy(
                    managingDirector = "Gero Geschäftsführer",
                    phone = "+49 931 000000",
                    registerCourt = "Würzburg",
                    registerNumber = "HRB 1",
                )
            valueOf(TemplateValue.GESCHAEFTSFUEHRUNG, withReference, sellerConfig) shouldBe "Gero Geschäftsführer"
            valueOf(TemplateValue.TELEFON, withReference, sellerConfig) shouldBe "+49 931 000000"
            valueOf(TemplateValue.REGISTERGERICHT, withReference, sellerConfig) shouldBe "Würzburg"
            valueOf(TemplateValue.REGISTERNUMMER, withReference, sellerConfig) shouldBe "HRB 1"
            valueOf(TemplateValue.KUNDENREFERENZ, withReference, sellerConfig) shouldBe "Bestellung 4711"
        }

        test("die Angaben der Stammdaten kommen maskiert") {
            valueOf(TemplateValue.FIRMA, sellerConfig = config.copy(companyName = "Meier & Söhne")) shouldBe "Meier \\& Söhne"
        }
    }
})
