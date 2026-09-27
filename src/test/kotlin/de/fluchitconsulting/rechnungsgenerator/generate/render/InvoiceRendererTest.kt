package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.accepted.accepted
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.LineItem
import de.fluchitconsulting.rechnungsgenerator.core.model.Logo
import de.fluchitconsulting.rechnungsgenerator.core.model.Prepayment
import de.fluchitconsulting.rechnungsgenerator.core.model.SellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.model.ServicePeriod
import de.fluchitconsulting.rechnungsgenerator.core.model.SkontoTier
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.core.model.exampleInvoices
import de.fluchitconsulting.rechnungsgenerator.core.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.testItem
import de.fluchitconsulting.rechnungsgenerator.core.model.testPrepayment
import de.fluchitconsulting.rechnungsgenerator.core.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.values.CountryCode
import de.fluchitconsulting.rechnungsgenerator.core.values.Design
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

// Testressourcen liegen in src/test/resources und landen im Classpath.
private fun resource(name: String): String =
    checkNotNull(InvoiceRendererTest::class.java.getResource("/$name")).readText()

/**
 * Der Inhalt einer Rechnung, wie ihn die Vorlage bekommt (#204): je nicht
 * leerem Platzhalter eine Kopfzeile `<<NAME>>`, darunter der Wert, in der
 * Reihenfolge von [TemplateValue]. Die leeren fehlen, damit eine neue,
 * optionale Angabe keine dieser Dateien berührt. Ein Format des Netzes und
 * kein Ergebnis des Renderers – deshalb steht es hier und nicht im Hauptcode.
 */
private fun contentOf(values: Map<TemplateValue, Latex>): String =
    TemplateValue.entries
        .map { it to values.getValue(it).tex }
        .filter { (_, tex) -> tex.isNotEmpty() }
        .joinToString("\n") { (key, tex) -> "<<${key.name}>>\n$tex\n" }

/**
 * Prüft das erzeugte LaTeX, nicht das PDF. Ob daraus ein gültiges PDF/A wird,
 * beantwortet der ToolchainIntegrationTest; hier geht es um die Frage, ob die
 * Felder an der richtigen Stelle landen – und das ist ohne LuaLaTeX zu klären.
 */
class InvoiceRendererTest : FunSpec({

    val config = testSellerConfig()

    // Das Beispiellogo aus den Testressourcen. Der Pfad ist nur ein Name –
    // gelesen hat der Test die Datei selbst, wie sonst YamlLoader.
    val exampleLogo = Logo(Path.of("logo.tex"), resource("logo.tex").toByteArray())

    fun renderWith(vararg items: de.fluchitconsulting.rechnungsgenerator.core.model.LineItem): String {
        val invoice = testInvoice(items = items.toList())
        return InvoiceRenderer.render(CalculatedInvoice(invoice, config).accepted()).sources.tex
    }

    /**
     * Der Wert eines Platzhalters, wie der Renderer ihn liefert. Seit #188
     * stehen die Beschriftungen ("Tel.", "Zahlbar ohne Abzug bis") in der
     * Vorlage, also in jedem gesetzten Text. Ob eine Angabe dasteht, zeigt
     * deshalb ihr Wert: leer oder nicht.
     */
    fun valueOf(
        key: TemplateValue,
        invoice: Invoice = testInvoice(),
        sellerConfig: SellerConfig = config,
    ): String = InvoiceRenderer.values(CalculatedInvoice(invoice, sellerConfig).accepted()).getValue(key).tex

    test("eine Position ohne Zusatzangaben bleibt einzeilig") {
        val tex = renderWith(testItem())

        tex shouldContain "Testposition"
        // Kein Umbruch, keine zweite Zeile: Die schlichte Position soll
        // schlicht bleiben.
        // Nicht bloß "positionsdetail": Die Vorlage definiert das Makro, das
        // Wort steht also ohnehin im Text. Gemeint ist der Aufruf dahinter.
        tex shouldNotContain "Testposition\\positionsdetail"
    }

    test("jede Position trägt ihre laufende Nummer, beginnend bei 1") {
        // Das erste Argument von \positionszeile. Ob ein Design sie setzt,
        // entscheidet die Vorlage; helsinki setzt sie nicht (#188).
        val tex = renderWith(testItem().copy(name = "Erste"), testItem().copy(name = "Zweite"))

        tex shouldContain "\\positionszeile{1}{Erste}"
        tex shouldContain "\\positionszeile{2}{Zweite}"
    }

    test("die Beschreibung wird zur zweiten Zeile, klein und gedämpft") {
        val tex = renderWith(testItem(description = "Rahmenvertrag vom 01.02.2026"))

        // Wie die zweite Zeile aussieht, steht in der Vorlage; hier wird
        // geprüft, was hineingeht (#51).
        tex shouldContain "Testposition\\positionsdetail{Rahmenvertrag vom 01.02.2026}"
    }

    test("Beschreibung und Hinweis stehen zusammen in der zweiten Zeile") {
        val tex = renderWith(testItem(description = "Arbeitspaket Nord", note = "Abschlag September 2026"))

        tex shouldContain "Arbeitspaket Nord · Abschlag September 2026"
    }

    test("ein abweichender Zeitraum der Position wird benannt") {
        // Vor dem Rechnungsdatum (14.08.2026): Eine Rechnung rechnet eine
        // erbrachte Leistung ab, auch je Position (#47).
        val item =
            testItem(description = "Arbeitspaket Nord").copy(
                servicePeriod = ServicePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)),
            )

        renderWith(item) shouldContain "Arbeitspaket Nord · Zeitraum: Juni 2026"
    }

    test("Sonderzeichen werden auch in den Zusatzfeldern maskiert") {
        // Ein nacktes & beendet in LaTeX die Tabellenspalte, ein % den Rest der
        // Zeile. Beides würde den Satz zerlegen, nicht bloß hässlich aussehen.
        val tex = renderWith(testItem(description = "Meier & Söhne", note = "50 % Anzahlung"))

        tex shouldContain "Meier \\& Söhne · 50 \\% Anzahlung"
    }

    /**
     * Eine Rechnungsdatei ohne den Schlüssel `art` ist eine gewöhnliche
     * Rechnung, und ihr Satz steht zeichengleich in `rechnung-erwartet.tex`.
     *
     * Verglichen wird das LaTeX, nicht das PDF – zwei PDF-Läufe unterscheiden
     * sich schon im Zeitstempel, und alles, was am PDF anders werden könnte,
     * steht vorher hier drin. Wird die Vorlage absichtlich geändert, muss
     * `rechnung-erwartet.tex` bewusst neu erzeugt werden; genau das ist der
     * Zweck: Der Satz einer gewöhnlichen Rechnung soll sich nicht nebenbei
     * ändern, wenn an einer anderen Rechnungsart gebaut wird.
     */
    test("eine Rechnungsdatei ohne art wird gesetzt wie erwartet") {
        val seller = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))
        val invoice = YamlLoader.parseInvoice(resource("rechnung.yaml"))

        InvoiceRenderer.render(CalculatedInvoice(invoice, seller).accepted()).sources.tex shouldBe
            resource("rechnung-erwartet.tex")
    }

    /**
     * Je Art der Inhalt, je Design das Satzbild (#204). Die Werte hängen nicht
     * am Design, eine Datei gilt also für jedes; den Volltext sichert je Design
     * allein die gewöhnliche Rechnung. Die übrigen Arten tragen ihr Markup –
     * Absetzungstabelle, Pflichtsatz, Bezug – als Makroaufrufe in den Werten,
     * und wie ein Design diese Makros definiert, steht in dessen Volltext.
     * Eine Änderung am Satzbild berührt so eine Datei je Design statt eine je
     * Art, und wird der Volltext rot, zeigt die Inhalts-Datei, ob sich der
     * Inhalt geändert hat oder nur das Satzbild.
     *
     * Ohne Logo: Es ist ein Schalter und keine Angabe der Rechnungsart; das
     * Logo sichert die Volltext-Datei von prag.
     */
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

    /**
     * Das Satzbild von prag, gesichert an der Rechnung (ADR-0011). Die
     * übrigen Arten sichern hier wie in helsinki ihre Inhalts-Dateien und das
     * Pflichtangaben-Netz; eine Vergleichsdatei je Design und Art änderte sich
     * mit jeder Vorlage.
     */
    test("eine Rechnung im Design prag wird gesetzt wie festgehalten") {
        // Mit Logo (#198): Das zeigt prag, und ohne Logo stünde in der
        // Vergleichsdatei nur ein leeres \ifblank.
        val seller =
            YamlLoader.parseSellerConfig(resource("stammdaten.yaml")).copy(design = Design.PRAG, logo = exampleLogo)
        val invoice = YamlLoader.parseInvoice(resource("rechnung.yaml"))

        InvoiceRenderer.render(CalculatedInvoice(invoice, seller).accepted()).sources.tex shouldBe
            resource("prag-rechnung-erwartet.tex")
    }

    /**
     * Je Design der Volltext einer englischen Rechnung (#377), wie der
     * deutschen oben eine Frage und keine Aufgabe. Was aus Kotlin kommt –
     * Daten, Einheiten, Sätze, das Land – steht hier wörtlich; die Wörter der
     * Designs stehen als `\wort…` da und in ihren Sprachdateien.
     */
    test("eine englische Rechnung wird in jedem Design gesetzt wie festgehalten") {
        val seller = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))
        val invoice = YamlLoader.parseInvoice(resource("englische-rechnung.yaml"))

        mapOf(
            seller to "englische-rechnung-erwartet.tex",
            seller.copy(design = Design.PRAG, logo = exampleLogo) to "prag-englische-rechnung-erwartet.tex",
        ).forEach { (sellerConfig, expected) ->
            withClue(expected) {
                InvoiceRenderer.render(CalculatedInvoice(invoice, sellerConfig).accepted()).sources.tex shouldBe
                    resource(expected)
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

        // Ob das Blatt das Logo zeigt, sagt der Renderer ausdrücklich – das
        // Kommando nennt es danach in einer Fehlermeldung (#203).
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
        // Wie gestrichen wird, prüft der TemplateTest; hier nur, dass im
        // gesetzten LaTeX keine ankommt (#149).

        test("im gesetzten LaTeX steht keine Kommentarzeile der Vorlage") {
            renderWith(testItem()).lines().filter { it.trimStart().startsWith("%") }.shouldBeEmpty()
        }
    }

    context("Das Land in der Anschrift") {

        /** Der Block, in dem die Anschrift des Kunden steht. */
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
            // Der Defekt, gegen den dieser Typ steht: Ohne ihn stünde hier eine
            // Zeile "de" unter der Anschrift, während der Calculator dieselbe
            // Rechnung als Inlandsrechnung behandelt.
            address(customerCountry = "de", sellerCountry = "DE") shouldBe
                """Beispiel Software GmbH\zeilenwechsel{}Industriestraße 1\zeilenwechsel{}90402 Nürnberg"""
        }

        test("Inland ist relativ zum Rechnungssteller, nicht zu Deutschland") {
            // Sitzt der Rechnungssteller in Österreich, ist ein deutscher Kunde
            // für ihn Ausland – und bekommt sein Land genannt. Stünde "DE"
            // fest im Code, verschwiege diese Rechnung es.
            address(customerCountry = "DE", sellerCountry = "AT") shouldBe
                """Beispiel Software GmbH\zeilenwechsel{}Industriestraße 1\zeilenwechsel{}90402 Nürnberg\zeilenwechsel{}\landzeile{Deutschland}"""
        }

        test("das Land des Kunden wird genannt, wenn es ein anderes ist, mit seinem Namen und nicht dem Code") {
            address(customerCountry = "at", sellerCountry = "DE") shouldBe
                """Beispiel Software GmbH\zeilenwechsel{}Industriestraße 1\zeilenwechsel{}90402 Nürnberg\zeilenwechsel{}\landzeile{Österreich}"""
        }

        // Ein Code, den das JDK nicht als Staat nach ISO 3166 führt, bricht
        // nicht ab: Die Form hat CountryCode geprüft, und auf dem Blatt steht
        // er, wie er in der Datei steht.
        // EL: Griechenland in der Umsatzsteuer der EU, in ISO 3166 GR.
        // ZZ: Das JDK nennt es "Unbekannte Region" – das darf nicht aufs Blatt.
        for (code in listOf("EL", "ZZ", "XX")) {
            test("der Code $code ohne Namen steht als Code da") {
                address(customerCountry = code, sellerCountry = "DE") shouldEndWith """\zeilenwechsel{}\landzeile{$code}"""
            }
        }
    }

    test("ein Kleinstpreis steht so da, dass man die Zeile nachrechnen kann") {
        // Der Fall aus #14: Mit gerundetem Einzelpreis stünde hier
        // "10.000 & Std. & 0,00 € & 0,10 €" – die Summe stimmt, aber
        // nachrechnen ließe sie sich nicht.
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
            // UStAE 14.8 Abs. 1: Auf einer Abschlagsrechnung ist der Zeitraum
            // eine Prognose. Als "Leistungszeitraum" beschriftet, behauptete er,
            // die Leistung sei erbracht.
            //
            // Deshalb kommt diese Beschriftung vom Renderer und nicht aus der
            // Vorlage: Sie trägt eine fachliche Aussage (#188).
            valueOf(TemplateValue.ZEITRAUM_BESCHRIFTUNG, testInvoice(type = InvoiceType.PREPAYMENT)) shouldBe
                "Voraussichtlicher Leistungszeitraum"
            valueOf(TemplateValue.ZEITRAUM_BESCHRIFTUNG) shouldBe "Leistungszeitraum"
            valueOf(TemplateValue.LEISTUNGSZEITRAUM) shouldBe "Juli 2026"
        }

        test("der Pflichtsatz steht unter den Summen") {
            val tex = abschlag()

            tex shouldContain "Die Leistung ist noch nicht erbracht."
            // Nicht nur, DASS er dasteht: Über der Positionstabelle wäre der
            // Satz eine Ankündigung, unter dem Gesamtbetrag ist er der Vorbehalt
            // zu genau dieser Summe.
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
            // ADR-0002: Ein Abschlag ist keine negative Position. Stünde er in
            // der Tabelle, senkte er die Bemessungsgrundlage und damit die
            // ausgewiesene Umsatzsteuer.
            val tex = finalInvoice(totalService, testPrepayment())

            tex.indexOf("R26-009") shouldBeGreaterThan tex.indexOf("\\gesamtzeile{")
        }

        test("jeder Abschlag steht einzeln, mit Netto und Steuer") {
            // UStAE 14.8 Abs. 7 ließe die Summe genügen; einzeln, damit der
            // Kunde sieht, welche seiner Zahlungen abgesetzt wurde.
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
            // Abgesetzt wird, also negativ. Welches Minuszeichen auf dem Blatt
            // steht, entscheidet die Vorlage (ADR-0021).
            tex shouldContain "\\betrag{-4760.00}"
        }

        test("der Restbetrag steht unter dem Strich, und darunter die Bankverbindung") {
            val tex = finalInvoice(totalService, testPrepayment())

            tex shouldContain "\\abschlusszeile{Restbetrag}{\\betrag{19040.00}}"
            // Der Aufruf und nicht bloß der Name: Die Vorlage definiert
            // \bankblock, das Wort steht also in jedem erzeugten .tex.
            tex shouldContain "\\bankblock{}"
        }

        test("ein Restbetrag von null bekommt einen Satz statt einer Zahl") {
            // Ein nacktes "0,00 €" unter dem Strich sieht aus wie ein Fehler.
            val tex = finalInvoice(listOf(testItem(quantity = "1", unitPrice = "4000.00")), testPrepayment())

            tex shouldContain "es verbleibt kein Restbetrag"
            // Keine Abschlusszeile mit Betrag (#94).
            tex shouldNotContain "\\abschlusszeile{Restbetrag}"
            // Und kein Zahlungsziel daneben: Das wäre die zweite, andere
            // Aussage desselben Blattes. Die Gegenprobe: Eine gewöhnliche
            // Rechnung nennt sehr wohl eines.
            val settled =
                testInvoice(
                    type = InvoiceType.FINAL,
                    items = listOf(testItem(quantity = "1", unitPrice = "4000.00")),
                    prepayments = listOf(testPrepayment()),
                )
            valueOf(TemplateValue.ZAHLUNGSZIEL, settled) shouldBe ""
            valueOf(TemplateValue.ZAHLUNGSZIEL) shouldBe "28.~August 2026"
            // Ebenso wenig ein Konto: Wer nichts zu zahlen hat, bekommt keines
            // genannt (#94). Die IBAN in der Fußzeile bleibt.
            valueOf(TemplateValue.BANKVERBINDUNG, settled) shouldBe ""
        }

        test("eine gewöhnliche Rechnung bekommt keine Absetzung") {
            renderWith(testItem()) shouldNotContain "Abzüglich"
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
            // Kein Datum steht doppelt: Jede Skontofrist endet vor dem
            // Zahlungsziel, das SKONTOFRIST_NICHT_VOR_ZAHLUNGSZIEL hält.
            valueOf(TemplateValue.ZAHLUNGSZIEL, testInvoice(skonto = listOf(SkontoTier(rate = Percent("2"), days = 7)))) shouldBe
                "28.~August 2026"
        }

        test("ohne Skonto kein Satz") {
            renderWith(testItem()) shouldNotContain "Skonto"
        }
    }

    context("Zahlungsfrist") {
        // Der Satz, den prag statt der Kopfzeile "Zahlbar bis" setzt (#211).
        // Die Stammdaten des Tests haben 14 Tage.
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
            // Ohne Skonto fehlen die Worte: Dort fragte man sich, welcher
            // Abzug gemeint ist.
            paymentTerm(testInvoice(skonto = listOf(SkontoTier(rate = Percent("2"), days = 7)))) shouldBe
                "\\hinweisabsatz{Die Zahlung ist ohne Abzug 14 Tage nach Rechnungsdatum fällig.}"
        }

        test("ohne Zahlungsziel kein Satz") {
            // Eine voll abgesetzte Schlussrechnung fordert kein Geld (#94).
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
        // Was aus Kotlin kommt. Beschriftungen der Vorlage und die
        // Schreibweise der Zahlen stehen in den Sprachdateien der Designs.
        fun english(
            items: List<LineItem> = listOf(testItem()),
            paymentTermDaysOverride: Int? = null,
        ) = testInvoice(items = items, paymentTermDaysOverride = paymentTermDaysOverride, language = Language.BRITISH_ENGLISH)

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
    }

    context("Pflichtangaben und was fehlen darf") {
        // Keine dieser Angaben steht in einer der Vergleichsdateien (#95):
        // Deren Stammdaten haben alles, und keine ihrer Rechnungen ist
        // steuerfrei. Geprüft wird am Inhalt, nicht am Makro.

        fun render(invoice: Invoice, sellerConfig: SellerConfig = config): String =
            InvoiceRenderer.render(CalculatedInvoice(invoice, sellerConfig).accepted()).sources.tex

        // Eine Position zum Regelsatz und eine mit Steuerschuldnerschaft des
        // Leistungsempfängers – die verlangt die USt-IdNr. des Kunden.
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
            // BT-120. Bei Reverse Charge verlangt §14a Abs. 5 UStG den Hinweis
            // ausdrücklich auf der Rechnung.
            render(withReverseCharge()) shouldContain
                "Auf \\betrag{200.00} entfällt keine Umsatzsteuer: Steuerschuldnerschaft des Leistungsempfängers (Reverse Charge)."
        }

        test("eine Steuergruppe ohne Steuerbetrag steht nicht in den Summen") {
            // "zzgl. 0 % USt. 0,00 €" sähe aus wie ein Rechenfehler; warum keine
            // Steuer anfällt, sagt der Hinweis darunter.
            val tex = render(withReverseCharge())

            tex shouldContain "\\steuerzeile{\\prozent{19}}"
            tex shouldNotContain "\\steuerzeile{\\prozent{0}"
        }

        test("jeder Hinweis auf eine Steuerbefreiung ist ein eigener Satz") {
            // Zwischen zwei Hinweisen darf die Seite brechen, in einem nicht
            // (ADR-0020) – das entscheidet die Vorlage je Makroaufruf.
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
            // §14a UStG. Wo sie steht, entscheidet die Vorlage; hier nur, ob.
            val customerVatId = VatId("ATU12345678")

            valueOf(TemplateValue.KUNDE_USTID, testInvoice(customerVatId = customerVatId)) shouldBe customerVatId.formatted()
            valueOf(TemplateValue.KUNDE_USTID) shouldBe ""
        }

        test("der Ansprechpartner steht unter dem Namen und vor der Straße, wenn es einen gibt") {
            // BT-56 (#196). Die Reihenfolge ist Postkonvention, deshalb im
            // Block EMPFAENGER und nicht als eigener Platzhalter.
            valueOf(TemplateValue.EMPFAENGER, testInvoice(contactPoint = "Herrn Max Beispiel")) shouldBe
                """Beispiel Software GmbH\zeilenwechsel{}Herrn Max Beispiel\zeilenwechsel{}Industriestraße 1\zeilenwechsel{}90402 Nürnberg"""
            valueOf(TemplateValue.EMPFAENGER) shouldBe
                """Beispiel Software GmbH\zeilenwechsel{}Industriestraße 1\zeilenwechsel{}90402 Nürnberg"""
        }

        test("die Kundennummer kommt maskiert, und ohne sie ist der Wert leer") {
            // BT-46 (#190). Leer heißt: helsinki lässt die Zeile samt "Kundennr."
            // weg. Das & zeigt, dass sie wie jeder Freitext maskiert wird.
            valueOf(TemplateValue.KUNDENNUMMER, testInvoice(buyerIdentifier = "K-17 & Co")) shouldBe "K-17 \\& Co"
            valueOf(TemplateValue.KUNDENNUMMER) shouldBe ""
        }

        test("die Auftragsnummer kommt maskiert, und ohne sie ist der Wert leer") {
            // BT-14 (#191). Leer heißt: helsinki lässt die Zeile samt
            // "Unsere Auftragsnr." weg.
            valueOf(TemplateValue.AUFTRAGSNUMMER, testInvoice(salesOrderReference = "A_2026_014")) shouldBe "A\\_2026\\_014"
            valueOf(TemplateValue.AUFTRAGSNUMMER) shouldBe ""
        }

        test("die Einleitung kommt maskiert, und ohne sie ist der Wert leer") {
            // #209. Leer heißt: Die Vorlage setzt über der Tabelle nichts, und
            // der Abstand ändert sich nicht.
            valueOf(TemplateValue.EINLEITUNG, testInvoice(introduction = "Für Projekt A & B:")) shouldBe
                "Für Projekt A \\& B:"
            valueOf(TemplateValue.EINLEITUNG) shouldBe ""
        }

        test("Geschäftsführung, Telefon, Registergericht und Referenz sind leer, wenn sie fehlen") {
            // Die Beschriftungen stehen seit #188 in der Vorlage, ebenso die
            // Regel "die Person im Kopf nur ohne Geschäftsführung". Hier bleibt
            // die Frage, ob der Renderer eine fehlende Angabe leer liefert und
            // eine vorhandene so, wie sie in der Eingabe steht.
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
            // Einzeln statt als fertige Zeile, aber genauso maskiert: Ein & in
            // der Firmierung zerlegte sonst den Fuß.
            valueOf(TemplateValue.FIRMA, sellerConfig = config.copy(companyName = "Meier & Söhne")) shouldBe "Meier \\& Söhne"
        }
    }
})
