package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.model.LineItem
import de.fluchitconsulting.rechnungsgenerator.model.Prepayment
import de.fluchitconsulting.rechnungsgenerator.model.SellerConfig
import de.fluchitconsulting.rechnungsgenerator.model.ServicePeriod
import de.fluchitconsulting.rechnungsgenerator.model.SkontoTier
import de.fluchitconsulting.rechnungsgenerator.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.model.exampleInvoices
import de.fluchitconsulting.rechnungsgenerator.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.model.testItem
import de.fluchitconsulting.rechnungsgenerator.model.testPrepayment
import de.fluchitconsulting.rechnungsgenerator.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.rules.accepted
import de.fluchitconsulting.rechnungsgenerator.values.CountryCode
import de.fluchitconsulting.rechnungsgenerator.values.Design
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.values.Logo
import de.fluchitconsulting.rechnungsgenerator.values.Percent
import de.fluchitconsulting.rechnungsgenerator.values.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.values.VatId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
    val beispielLogo = Logo(Path.of("logo.tex"), resource("logo.tex").toByteArray())

    fun renderMit(vararg items: de.fluchitconsulting.rechnungsgenerator.model.LineItem): String {
        val invoice = testInvoice(items = items.toList())
        return InvoiceRenderer.render(CalculatedInvoice(invoice, config).accepted()).sources.tex
    }

    /**
     * Der Wert eines Platzhalters, wie der Renderer ihn liefert. Seit #188
     * stehen die Beschriftungen ("Tel.", "Zahlbar ohne Abzug bis") in der
     * Vorlage, also in jedem gesetzten Text. Ob eine Angabe dasteht, zeigt
     * deshalb ihr Wert: leer oder nicht.
     */
    fun wert(
        key: TemplateValue,
        invoice: Invoice = testInvoice(),
        stammdaten: SellerConfig = config,
    ): String = InvoiceRenderer.values(CalculatedInvoice(invoice, stammdaten).accepted()).getValue(key).tex

    test("eine Position ohne Zusatzangaben bleibt einzeilig") {
        val tex = renderMit(testItem())

        tex shouldContain "Testposition"
        // Kein Umbruch, keine zweite Zeile: Die schlichte Position soll
        // schlicht bleiben.
        // Nicht bloß "positionsdetail": Die Vorlage definiert das Makro, das
        // Wort steht also ohnehin im Text. Gemeint ist der Aufruf dahinter.
        tex shouldNotContain "Testposition\\positionsdetail"
    }

    test("jede Position trägt ihre laufende Nummer, beginnend bei 1") {
        // Das erste Argument von \positionszeile. Ob ein Design sie setzt,
        // entscheidet die Vorlage; balken setzt sie nicht (#188).
        val tex = renderMit(testItem().copy(name = "Erste"), testItem().copy(name = "Zweite"))

        tex shouldContain "\\positionszeile{1}{Erste}"
        tex shouldContain "\\positionszeile{2}{Zweite}"
    }

    test("die Beschreibung wird zur zweiten Zeile, klein und gedämpft") {
        val tex = renderMit(testItem(description = "Rahmenvertrag vom 01.02.2026"))

        // Wie die zweite Zeile aussieht, steht in der Vorlage; hier wird
        // geprüft, was hineingeht (#51).
        tex shouldContain "Testposition\\positionsdetail{Rahmenvertrag vom 01.02.2026}"
    }

    test("Beschreibung und Hinweis stehen zusammen in der zweiten Zeile") {
        val tex = renderMit(testItem(description = "Arbeitspaket Nord", note = "Abschlag September 2026"))

        tex shouldContain "Arbeitspaket Nord · Abschlag September 2026"
    }

    test("ein abweichender Zeitraum der Position wird benannt") {
        // Vor dem Rechnungsdatum (14.08.2026): Eine Rechnung rechnet eine
        // erbrachte Leistung ab, auch je Position (#47).
        val item =
            testItem(description = "Arbeitspaket Nord").copy(
                servicePeriod = ServicePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)),
            )

        renderMit(item) shouldContain "Arbeitspaket Nord · Zeitraum: Juni 2026"
    }

    test("Sonderzeichen werden auch in den Zusatzfeldern maskiert") {
        // Ein nacktes & beendet in LaTeX die Tabellenspalte, ein % den Rest der
        // Zeile. Beides würde den Satz zerlegen, nicht bloß hässlich aussehen.
        val tex = renderMit(testItem(description = "Meier & Söhne", note = "50 % Anzahlung"))

        tex shouldContain "Meier \\& Söhne · 50 \\% Anzahlung"
    }

    /**
     * Der negative Test der Rechnungsart: Eine bestehende Rechnungsdatei kennt
     * den Schlüssel `art` nicht und muss unverändert dasselbe Ergebnis liefern.
     *
     * Verglichen wird das LaTeX, nicht das PDF – zwei PDF-Läufe unterscheiden
     * sich schon im Zeitstempel, und alles, was am PDF anders werden könnte,
     * steht vorher hier drin. Wird die Vorlage absichtlich geändert, muss
     * `rechnung-erwartet.tex` bewusst neu erzeugt werden; genau das ist der
     * Zweck: Der Satz einer gewöhnlichen Rechnung soll sich nicht nebenbei
     * ändern, während die anderen Rechnungsarten dazukommen.
     */
    test("eine Rechnungsdatei ohne art wird gesetzt wie bisher") {
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
     * Logo sichert die Volltext-Datei von raster.
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
     * Das Satzbild von raster, gesichert an der Rechnung (ADR-0011). Die
     * übrigen Arten sichern hier wie in balken ihre Inhalts-Dateien und das
     * Pflichtangaben-Netz; eine Vergleichsdatei je Design und Art änderte sich
     * mit jeder Vorlage.
     */
    test("eine Rechnung im Design raster wird gesetzt wie festgehalten") {
        // Mit Logo (#198): Das zeigt raster, und ohne Logo stünde in der
        // Vergleichsdatei nur ein leeres \ifblank.
        val seller =
            YamlLoader.parseSellerConfig(resource("stammdaten.yaml")).copy(design = Design.RASTER, logo = beispielLogo)
        val invoice = YamlLoader.parseInvoice(resource("rechnung.yaml"))

        InvoiceRenderer.render(CalculatedInvoice(invoice, seller).accepted()).sources.tex shouldBe
            resource("raster-rechnung-erwartet.tex")
    }

    context("logo (#198)") {
        fun gesetzt(design: Design, logo: Logo?) =
            InvoiceRenderer.render(CalculatedInvoice(testInvoice(), config.copy(design = design, logo = logo)).accepted())

        test("der Platzhalter ist ein Schalter: der Name der Datei oder leer") {
            wert(TemplateValue.LOGO, stammdaten = config.copy(logo = beispielLogo)) shouldBe "logo"
            wert(TemplateValue.LOGO) shouldBe ""
        }

        test("raster bindet das Logo ein und bekommt es als logo.tex mit") {
            val rendered = gesetzt(Design.RASTER, beispielLogo)

            rendered.sources.tex shouldContain "\\input{logo}"
            rendered.sources.files.keys shouldBe setOf("logo.tex")
            rendered.sources.files.getValue("logo.tex") shouldBe beispielLogo.content
        }

        test("balken übergeht das Logo und bekommt keine Datei") {
            // Sonst ginge die Datei in die Trailer-ID ein, und dieselbe
            // Rechnung ergäbe mit und ohne Eintrag verschiedene PDFs.
            val mitLogo = gesetzt(Design.BALKEN, beispielLogo)

            mitLogo.sources.files shouldBe emptyMap()
            mitLogo.sources.tex shouldBe gesetzt(Design.BALKEN, null).sources.tex
        }

        test("ohne Logo gibt es in keinem Design eine Datei") {
            Design.entries.forEach { design ->
                withClue(design) { gesetzt(design, null).sources.files shouldBe emptyMap() }
            }
        }

        // Ob das Blatt das Logo zeigt, sagt der Renderer ausdrücklich – das
        // Kommando nennt es danach in einer Fehlermeldung (#203).
        test("raster mit Logo zeigt es") {
            gesetzt(Design.RASTER, beispielLogo).includesLogo shouldBe true
        }

        test("balken mit Logo zeigt es nicht") {
            gesetzt(Design.BALKEN, beispielLogo).includesLogo shouldBe false
        }

        test("ohne Logo in den Stammdaten zeigt es kein Design, auch wenn die Vorlage den Platzhalter trägt") {
            Design.entries.forEach { design ->
                withClue(design) { gesetzt(design, null).includesLogo shouldBe false }
            }
        }
    }

    context("Die Kommentarzeilen der Vorlage") {
        // Wie gestrichen wird, prüft der TemplateTest; hier nur, dass im
        // gesetzten LaTeX keine ankommt (#149).

        test("im gesetzten LaTeX steht keine Kommentarzeile der Vorlage") {
            renderMit(testItem()).lines().filter { it.trimStart().startsWith("%") }.shouldBeEmpty()
        }
    }

    context("Das Land in der Anschrift") {

        /** Der Block, in dem die Anschrift des Kunden steht. */
        fun anschrift(kundenland: String, stellerland: String): String {
            val basis = testInvoice()
            val invoice =
                basis.copy(
                    customer =
                        basis.customer.copy(
                            address = basis.customer.address.copy(countryCode = CountryCode(kundenland)),
                        ),
                )
            val stammdaten = config.copy(address = config.address.copy(countryCode = CountryCode(stellerland)))
            return wert(TemplateValue.EMPFAENGER, invoice, stammdaten)
        }

        test("ein kleingeschriebenes Land ist dasselbe Land") {
            // Der Defekt, gegen den dieser Typ steht: Ohne ihn stünde hier eine
            // Zeile "de" unter der Anschrift, während der Calculator dieselbe
            // Rechnung als Inlandsrechnung behandelt.
            anschrift(kundenland = "de", stellerland = "DE") shouldBe
                """Beispiel Software GmbH\zeilenwechsel{}Industriestraße 1\zeilenwechsel{}90402 Nürnberg"""
        }

        test("Inland ist relativ zum Rechnungssteller, nicht zu Deutschland") {
            // Sitzt der Rechnungssteller in Österreich, ist ein deutscher Kunde
            // für ihn Ausland – und bekommt sein Land genannt. Stünde "DE"
            // fest im Code, verschwiege diese Rechnung es.
            anschrift(kundenland = "DE", stellerland = "AT") shouldBe
                """Beispiel Software GmbH\zeilenwechsel{}Industriestraße 1\zeilenwechsel{}90402 Nürnberg\zeilenwechsel{}DE"""
        }

        test("das Land des Kunden wird genannt, wenn es ein anderes ist") {
            anschrift(kundenland = "at", stellerland = "DE") shouldBe
                """Beispiel Software GmbH\zeilenwechsel{}Industriestraße 1\zeilenwechsel{}90402 Nürnberg\zeilenwechsel{}AT"""
        }
    }

    test("ein Kleinstpreis steht so da, dass man die Zeile nachrechnen kann") {
        // Der Fall aus #14: Mit gerundetem Einzelpreis stünde hier
        // "10.000 & Std. & 0,00 € & 0,10 €" – die Summe stimmt, aber
        // nachrechnen ließe sie sich nicht.
        val tex = renderMit(testItem(quantity = "10000", unitPrice = "0.00001"))

        tex shouldContain "{10.000}{Std.}{0,00001~€}{}{0,10~€}"
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

            wert(TemplateValue.LEISTUNGSZEITRAUM, invoice, seller) shouldBe "Juni bis Oktober 2026"
        }

        test("der Zeitraum ist der voraussichtliche") {
            // UStAE 14.8 Abs. 1: Auf einer Abschlagsrechnung ist der Zeitraum
            // eine Prognose. Als "Leistungszeitraum" beschriftet, behauptete er,
            // die Leistung sei erbracht.
            //
            // Deshalb kommt diese Beschriftung vom Renderer und nicht aus der
            // Vorlage: Sie trägt eine fachliche Aussage (#188).
            wert(TemplateValue.ZEITRAUM_BESCHRIFTUNG, testInvoice(type = InvoiceType.PREPAYMENT)) shouldBe
                "Voraussichtlicher Leistungszeitraum"
            wert(TemplateValue.ZEITRAUM_BESCHRIFTUNG) shouldBe "Leistungszeitraum"
            wert(TemplateValue.LEISTUNGSZEITRAUM) shouldBe "Juli 2026"
        }

        test("der Pflichtsatz steht unter den Summen") {
            val tex = abschlag()

            tex shouldContain "Die Leistung ist noch nicht erbracht."
            // Nicht nur, DASS er dasteht: Über der Positionstabelle wäre der
            // Satz eine Ankündigung, unter dem Gesamtbetrag ist er der Vorbehalt
            // zu genau dieser Summe.
            tex.indexOf("Die Leistung ist noch nicht erbracht.") shouldBeGreaterThan tex.indexOf("\\gesamtzeile{")
            renderMit(testItem()) shouldNotContain "noch nicht erbracht"
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

            wert(TemplateValue.VEREINNAHMT_AM, vereinnahmt) shouldBe "10.~August 2026"
            wert(TemplateValue.VEREINNAHMT_AM, testInvoice(type = InvoiceType.PREPAYMENT)) shouldBe ""
        }

        test("die Metadaten des PDF tragen dieselbe Bezeichnung") {
            val invoice = testInvoice(type = InvoiceType.PREPAYMENT)

            val xmp = InvoiceRenderer.render(CalculatedInvoice(invoice, config).accepted()).sources.xmpMetadata

            xmp shouldContain "\\Title{Abschlagsrechnung R26-001}"
            xmp shouldContain "\\Subject{Abschlagsrechnung R26-001 vom 14. August 2026}"
        }
    }
    context("Schlussrechnung") {
        fun schlussrechnung(items: List<LineItem>, vararg prepayments: Prepayment): String {
            val invoice = testInvoice(type = InvoiceType.FINAL, items = items, prepayments = prepayments.toList())
            return InvoiceRenderer.render(CalculatedInvoice(invoice, config).accepted()).sources.tex
        }

        val gesamtleistung = listOf(testItem(quantity = "1", unitPrice = "20000.00"))

        test("die Abschläge stehen unter dem Gesamtbetrag, nicht in der Positionstabelle") {
            // ADR-0002: Ein Abschlag ist keine negative Position. Stünde er in
            // der Tabelle, senkte er die Bemessungsgrundlage und damit die
            // ausgewiesene Umsatzsteuer.
            val tex = schlussrechnung(gesamtleistung, testPrepayment())

            tex.indexOf("R26-009") shouldBeGreaterThan tex.indexOf("\\gesamtzeile{")
        }

        test("jeder Abschlag steht einzeln, mit Netto und Steuer") {
            // UStAE 14.8 Abs. 7 ließe die Summe genügen; einzeln, damit der
            // Kunde sieht, welche seiner Zahlungen abgesetzt wurde.
            val tex =
                schlussrechnung(
                    gesamtleistung,
                    testPrepayment(invoiceNumber = "R26-009"),
                    testPrepayment(
                        invoiceNumber = "R26-011",
                        issueDate = LocalDate.of(2026, 7, 20),
                        receivedOn = LocalDate.of(2026, 7, 25),
                    ),
                )

            tex shouldContain "Abschlagsrechnung R26-009 vom 15.06.2026, vereinnahmt am 10.06.2026"
            tex shouldContain "Abschlagsrechnung R26-011 vom 20.07.2026, vereinnahmt am 25.07.2026"
            // Die Tilde vor Einheit und Währung ist das geschützte Leerzeichen
            // aus GermanFormat, beim Maskieren übersetzt – LaTeX liest sie als
            // \nobreakspace.
            tex shouldContain "netto 4.000,00~€ zzgl. 19~\\% USt. 760,00~€"
            // Abgesetzt wird, also mit Minuszeichen – und zwar dem typografischen.
            tex shouldContain "\u22124.760,00~€"
        }

        test("der Restbetrag steht unter dem Strich, und darunter die Bankverbindung") {
            val tex = schlussrechnung(gesamtleistung, testPrepayment())

            tex shouldContain "\\abschlusszeile{Restbetrag}{19.040,00~€}"
            // Der Aufruf und nicht bloß der Name: Die Vorlage definiert
            // \bankblock, das Wort steht also in jedem erzeugten .tex.
            tex shouldContain "\\bankblock{}"
        }

        test("ein Restbetrag von null bekommt einen Satz statt einer Zahl") {
            // Ein nacktes "0,00 €" unter dem Strich sieht aus wie ein Fehler.
            val tex = schlussrechnung(listOf(testItem(quantity = "1", unitPrice = "4000.00")), testPrepayment())

            tex shouldContain "es verbleibt kein Restbetrag"
            // Keine Abschlusszeile mit Betrag (#94).
            tex shouldNotContain "\\abschlusszeile{Restbetrag}"
            // Und kein Zahlungsziel daneben: Das wäre die zweite, andere
            // Aussage desselben Blattes. Die Gegenprobe: Eine gewöhnliche
            // Rechnung nennt sehr wohl eines.
            val gedeckt =
                testInvoice(
                    type = InvoiceType.FINAL,
                    items = listOf(testItem(quantity = "1", unitPrice = "4000.00")),
                    prepayments = listOf(testPrepayment()),
                )
            wert(TemplateValue.ZAHLUNGSZIEL, gedeckt) shouldBe ""
            wert(TemplateValue.ZAHLUNGSZIEL) shouldBe "28.~August 2026"
            // Ebenso wenig ein Konto: Wer nichts zu zahlen hat, bekommt keines
            // genannt (#94). Die IBAN in der Fußzeile bleibt.
            wert(TemplateValue.BANKVERBINDUNG, gedeckt) shouldBe ""
        }

        test("eine gewöhnliche Rechnung bekommt keine Absetzung") {
            renderMit(testItem()) shouldNotContain "Abzüglich"
        }
    }

    context("Skonto") {
        // Rechnungsdatum 14.08.2026, Zahlungsziel 14 Tage.
        fun mitSkonto(vararg stufen: SkontoTier): String =
            InvoiceRenderer.render(CalculatedInvoice(testInvoice(skonto = stufen.toList()), config).accepted()).sources.tex

        test("eine Stufe wird ein Satz unter den Summen") {
            val tex = mitSkonto(SkontoTier(rate = Percent("2"), days = 7))

            tex shouldContain
                "\\hinweisabsatz{Bei Zahlung bis~\\zusammen{21.~August 2026} können 2~\\% " +
                "Skonto abgezogen werden.}"
            tex.indexOf("Skonto abgezogen") shouldBeGreaterThan tex.indexOf("\\gesamtzeile{")
        }

        test("weitere Stufen hängen sich nach der Frist sortiert an") {
            val tex = mitSkonto(SkontoTier(rate = Percent("1.5"), days = 10), SkontoTier(rate = Percent("2"), days = 7))

            tex shouldContain
                "\\hinweisabsatz{Bei Zahlung bis~\\zusammen{21.~August 2026} können 2~\\% " +
                "Skonto abgezogen werden, bei Zahlung bis~\\zusammen{24.~August 2026} 1,5~\\%.}"
        }

        test("das Zahlungsziel im Kopf bleibt, wie es ist") {
            // Kein Datum steht doppelt: Jede Skontofrist endet vor dem
            // Zahlungsziel, das SKONTOFRIST_NICHT_VOR_ZAHLUNGSZIEL hält.
            wert(TemplateValue.ZAHLUNGSZIEL, testInvoice(skonto = listOf(SkontoTier(rate = Percent("2"), days = 7)))) shouldBe
                "28.~August 2026"
        }

        test("ohne Skonto kein Satz") {
            renderMit(testItem()) shouldNotContain "Skonto"
        }
    }

    context("Zahlungsfrist") {
        // Der Satz, den raster statt der Kopfzeile "Zahlbar bis" setzt (#211).
        // Die Stammdaten des Tests haben 14 Tage.
        fun frist(invoice: Invoice): String = wert(TemplateValue.ZAHLUNGSFRIST, invoice)

        test("mehrere Tage ab Rechnungsdatum") {
            frist(testInvoice()) shouldBe "\\hinweisabsatz{Die Zahlung ist 14 Tage nach Rechnungsdatum fällig.}"
        }

        test("ein Tag in der Einzahl") {
            frist(testInvoice(paymentTermDaysOverride = 1)) shouldBe
                "\\hinweisabsatz{Die Zahlung ist 1 Tag nach Rechnungsdatum fällig.}"
        }

        test("null Tage heißt sofort") {
            frist(testInvoice(paymentTermDaysOverride = 0)) shouldBe "\\hinweisabsatz{Die Zahlung ist sofort fällig.}"
        }

        test("mit Skonto ohne Abzug") {
            // Ohne Skonto fehlen die Worte: Dort fragte man sich, welcher
            // Abzug gemeint ist.
            frist(testInvoice(skonto = listOf(SkontoTier(rate = Percent("2"), days = 7)))) shouldBe
                "\\hinweisabsatz{Die Zahlung ist ohne Abzug 14 Tage nach Rechnungsdatum fällig.}"
        }

        test("ohne Zahlungsziel kein Satz") {
            // Eine voll abgesetzte Schlussrechnung fordert kein Geld (#94).
            val gedeckt =
                testInvoice(
                    type = InvoiceType.FINAL,
                    items = listOf(testItem(quantity = "1", unitPrice = "4000.00")),
                    prepayments = listOf(testPrepayment()),
                )
            frist(gedeckt) shouldBe ""
        }
    }

    context("Pflichtangaben und was fehlen darf") {
        // Keine dieser Angaben steht in einer der Vergleichsdateien (#95):
        // Deren Stammdaten haben alles, und keine ihrer Rechnungen ist
        // steuerfrei. Geprüft wird am Inhalt, nicht am Makro.

        fun render(invoice: Invoice, stammdaten: SellerConfig = config): String =
            InvoiceRenderer.render(CalculatedInvoice(invoice, stammdaten).accepted()).sources.tex

        // Eine Position zum Regelsatz und eine mit Steuerschuldnerschaft des
        // Leistungsempfängers – die verlangt die USt-IdNr. des Kunden.
        fun mitReverseCharge() =
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
            render(mitReverseCharge()) shouldContain
                "Auf 200,00~€ entfällt keine Umsatzsteuer: Steuerschuldnerschaft des Leistungsempfängers (Reverse Charge)."
        }

        test("eine Steuergruppe ohne Steuerbetrag steht nicht in den Summen") {
            // "zzgl. 0 % USt. 0,00 €" sähe aus wie ein Rechenfehler; warum keine
            // Steuer anfällt, sagt der Hinweis darunter.
            val tex = render(mitReverseCharge())

            tex shouldContain "\\steuerzeile{19~\\%}"
            tex shouldNotContain "\\steuerzeile{0"
        }

        test("die USt-IdNr. des Kunden steht unter seiner Anschrift, wenn er eine hat") {
            // §14a UStG. Wo sie steht, entscheidet die Vorlage; hier nur, ob.
            val kundenId = VatId("ATU12345678")

            wert(TemplateValue.KUNDE_USTID, testInvoice(customerVatId = kundenId)) shouldBe kundenId.formatted()
            wert(TemplateValue.KUNDE_USTID) shouldBe ""
        }

        test("der Ansprechpartner steht unter dem Namen und vor der Straße, wenn es einen gibt") {
            // BT-56 (#196). Die Reihenfolge ist Postkonvention, deshalb im
            // Block EMPFAENGER und nicht als eigener Platzhalter.
            wert(TemplateValue.EMPFAENGER, testInvoice(contactPoint = "Herrn Max Beispiel")) shouldBe
                """Beispiel Software GmbH\zeilenwechsel{}Herrn Max Beispiel\zeilenwechsel{}Industriestraße 1\zeilenwechsel{}90402 Nürnberg"""
            wert(TemplateValue.EMPFAENGER) shouldBe
                """Beispiel Software GmbH\zeilenwechsel{}Industriestraße 1\zeilenwechsel{}90402 Nürnberg"""
        }

        test("die Kundennummer kommt maskiert, und ohne sie ist der Wert leer") {
            // BT-46 (#190). Leer heißt: balken lässt die Zeile samt "Kundennr."
            // weg. Das & zeigt, dass sie wie jeder Freitext maskiert wird.
            wert(TemplateValue.KUNDENNUMMER, testInvoice(buyerIdentifier = "K-17 & Co")) shouldBe "K-17 \\& Co"
            wert(TemplateValue.KUNDENNUMMER) shouldBe ""
        }

        test("die Auftragsnummer kommt maskiert, und ohne sie ist der Wert leer") {
            // BT-14 (#191). Leer heißt: balken lässt die Zeile samt
            // "Unsere Auftragsnr." weg.
            wert(TemplateValue.AUFTRAGSNUMMER, testInvoice(salesOrderReference = "A_2026_014")) shouldBe "A\\_2026\\_014"
            wert(TemplateValue.AUFTRAGSNUMMER) shouldBe ""
        }

        test("die Einleitung kommt maskiert, und ohne sie ist der Wert leer") {
            // #209. Leer heißt: Die Vorlage setzt über der Tabelle nichts, und
            // der Abstand ändert sich nicht.
            wert(TemplateValue.EINLEITUNG, testInvoice(introduction = "Für Projekt A & B:")) shouldBe
                "Für Projekt A \\& B:"
            wert(TemplateValue.EINLEITUNG) shouldBe ""
        }

        test("Geschäftsführung, Telefon, Registergericht und Referenz sind leer, wenn sie fehlen") {
            // Die Beschriftungen stehen seit #188 in der Vorlage, ebenso die
            // Regel "die Person im Kopf nur ohne Geschäftsführung". Hier bleibt
            // die Frage, ob der Renderer eine fehlende Angabe leer liefert und
            // eine vorhandene so, wie sie in der Eingabe steht.
            wert(TemplateValue.GESCHAEFTSFUEHRUNG) shouldBe ""
            wert(TemplateValue.TELEFON) shouldBe ""
            wert(TemplateValue.REGISTERGERICHT) shouldBe ""
            wert(TemplateValue.REGISTERNUMMER) shouldBe ""
            wert(TemplateValue.KUNDENREFERENZ) shouldBe ""

            val basis = testInvoice()
            val mitReferenz = basis.copy(customer = basis.customer.copy(buyerReference = "Bestellung 4711"))
            val stammdaten =
                config.copy(
                    managingDirector = "Gero Geschäftsführer",
                    phone = "+49 931 000000",
                    registerCourt = "Würzburg",
                    registerNumber = "HRB 1",
                )
            wert(TemplateValue.GESCHAEFTSFUEHRUNG, mitReferenz, stammdaten) shouldBe "Gero Geschäftsführer"
            wert(TemplateValue.TELEFON, mitReferenz, stammdaten) shouldBe "+49 931 000000"
            wert(TemplateValue.REGISTERGERICHT, mitReferenz, stammdaten) shouldBe "Würzburg"
            wert(TemplateValue.REGISTERNUMMER, mitReferenz, stammdaten) shouldBe "HRB 1"
            wert(TemplateValue.KUNDENREFERENZ, mitReferenz, stammdaten) shouldBe "Bestellung 4711"
        }

        test("die Angaben der Stammdaten kommen maskiert") {
            // Einzeln statt als fertige Zeile, aber genauso maskiert: Ein & in
            // der Firmierung zerlegte sonst den Fuß.
            wert(TemplateValue.FIRMA, stammdaten = config.copy(companyName = "Meier & Söhne")) shouldBe "Meier \\& Söhne"
        }
    }
})
