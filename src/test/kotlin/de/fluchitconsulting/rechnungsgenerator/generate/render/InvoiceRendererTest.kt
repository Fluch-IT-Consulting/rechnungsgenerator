package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.model.LineItem
import de.fluchitconsulting.rechnungsgenerator.model.Prepayment
import de.fluchitconsulting.rechnungsgenerator.model.SellerConfig
import de.fluchitconsulting.rechnungsgenerator.model.ServicePeriod
import de.fluchitconsulting.rechnungsgenerator.model.SkontoTier
import de.fluchitconsulting.rechnungsgenerator.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.model.testInvoice
import de.fluchitconsulting.rechnungsgenerator.model.testItem
import de.fluchitconsulting.rechnungsgenerator.model.testPrepayment
import de.fluchitconsulting.rechnungsgenerator.model.testSellerConfig
import de.fluchitconsulting.rechnungsgenerator.rules.accepted
import de.fluchitconsulting.rechnungsgenerator.values.CountryCode
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.values.Percent
import de.fluchitconsulting.rechnungsgenerator.values.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.values.VatId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.time.LocalDate

// Testressourcen liegen in src/test/resources und landen im Classpath.
private fun resource(name: String): String =
    checkNotNull(InvoiceRendererTest::class.java.getResource("/$name")).readText()

/**
 * Prüft das erzeugte LaTeX, nicht das PDF. Ob daraus ein gültiges PDF/A wird,
 * beantwortet der ToolchainIntegrationTest; hier geht es um die Frage, ob die
 * Felder an der richtigen Stelle landen – und das ist ohne LuaLaTeX zu klären.
 */
class InvoiceRendererTest : FunSpec({

    val config = testSellerConfig()

    fun renderMit(vararg items: de.fluchitconsulting.rechnungsgenerator.model.LineItem): String {
        val invoice = testInvoice(items = items.toList())
        return InvoiceRenderer.render(CalculatedInvoice(invoice, config).accepted())
    }

    test("eine Position ohne Zusatzangaben bleibt einzeilig") {
        val tex = renderMit(testItem())

        tex shouldContain "Testposition"
        // Kein Umbruch, keine zweite Zeile: Die schlichte Position soll so
        // schlicht aussehen wie vorher auch.
        // Nicht bloß "positionsdetail": Die Vorlage definiert das Makro, das
        // Wort steht also ohnehin im Text. Gemeint ist der Aufruf dahinter.
        tex shouldNotContain "Testposition\\positionsdetail"
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

        InvoiceRenderer.render(CalculatedInvoice(invoice, seller).accepted()) shouldBe
            resource("rechnung-erwartet.tex")
    }

    /**
     * Dasselbe für die anderen Arten. Sie tragen das meiste Markup –
     * Absetzungstabelle, Makros, Pflichtsatz, der Bezug auf die
     * Ursprungsrechnung –, also gerade das, was ein Umbau an der Maskierung
     * durcheinanderbringen könnte.
     */
    test("die übrigen Rechnungsarten werden gesetzt wie festgehalten") {
        val seller = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))

        listOf("abschlagsrechnung", "schlussrechnung", "stornorechnung").forEach { name ->
            val invoice = YamlLoader.parseInvoice(resource("$name.yaml"))

            withClue(name) {
                InvoiceRenderer.render(CalculatedInvoice(invoice, seller).accepted()) shouldBe
                    resource("$name-erwartet.tex")
            }
        }
    }

    context("Vorlage und Werte müssen zusammenpassen") {
        // Diese Prüfung kann keine Rechnungsdatei auslösen – sie schlägt zu,
        // wenn Vorlage und Renderer auseinanderlaufen. Deshalb wird fillIn hier
        // direkt gerufen: Sonst wäre die Zusicherung eingebaut, aber unbelegt.

        test("ein Platzhalter ohne Wert bleibt nicht stehen, sondern bricht ab") {
            // Der teure Fall: Ohne die Prüfung stünde "<<SUMMEN>>" gesetzt im
            // PDF. LuaLaTeX übersetzt das klaglos, und veraPDF bestätigt ein
            // einwandfreies PDF/A – es ist ja eines.
            val fehler =
                shouldThrow<IllegalStateException> {
                    InvoiceRenderer.fillIn("\\textbf{<<FIRMA>>} <<SUMMEN>>", mapOf("FIRMA" to "Muster".escapeLatex()))
                }

            fehler.message!! shouldContain "<<SUMMEN>>"
            fehler.message!! shouldContain "keinen Wert"
        }

        test("ein Wert ohne Platzhalter bricht ebenfalls ab") {
            // Der häufigere Fall, und der mit der besseren Meldung: Wer sich
            // beim Schlüssel verschreibt, erfährt hier, welcher Schlüssel
            // gemeint war – nicht bloß, welcher Platzhalter stehen blieb.
            val fehler =
                shouldThrow<IllegalStateException> {
                    InvoiceRenderer.fillIn("<<FIRMA>>", mapOf("FIRMA" to "Muster".escapeLatex(), "FRIMA" to Latex.EMPTY))
                }

            fehler.message!! shouldContain "<<FRIMA>>"
        }

        test("passen beide zusammen, kommt der gefüllte Text heraus") {
            InvoiceRenderer.fillIn("<<A>> und <<B>>", mapOf("A" to "eins".escapeLatex(), "B" to "zwei".escapeLatex())) shouldBe
                "eins und zwei"
        }

        // Die Werte stammen aus den Eingabedaten, und ein Kundenname darf
        // spitze Klammern enthalten. Eingesetzter Text ist Inhalt, nicht Vorlage.
        test("ein Wert, der wie ein späterer Platzhalter aussieht, bleibt stehen") {
            InvoiceRenderer.fillIn(
                "<<A>> und <<B>>",
                mapOf("A" to "Kunde <<B>>".escapeLatex(), "B" to "zwei".escapeLatex()),
            ) shouldBe "Kunde <<B>> und zwei"
        }

        test("ein Wert, der wie ein früherer Platzhalter aussieht, gilt nicht als Lücke der Vorlage") {
            InvoiceRenderer.fillIn(
                "<<A>> und <<B>>",
                mapOf("A" to "eins".escapeLatex(), "B" to "Kunde <<A>>".escapeLatex()),
            ) shouldBe "eins und Kunde <<A>>"
        }

        test("Backslash und Dollar im Wert werden wörtlich eingesetzt") {
            // Beim Ersetzen per regulärem Ausdruck deuten manche Schnittstellen
            // "\" und "$1" im Ersatztext als Rückverweis – LaTeX besteht aus beidem.
            InvoiceRenderer.fillIn("<<A>>", mapOf("A" to Latex.raw("\\textbf{\$1}"))) shouldBe "\\textbf{\$1}"
        }

        test("ein rohes Zeilenende in einem Wert bricht ab und nennt den Platzhalter") {
            // Der Fall aus #147: Hinter einem rohen \\ liest LaTeX eine Zeile,
            // die mit "[" beginnt, als Abstandsangabe.
            val fehler =
                shouldThrow<IllegalStateException> {
                    InvoiceRenderer.checkLineBreaks(mapOf("EMPFAENGER" to Latex.raw("Muster GmbH\\\\Musterweg 1")))
                }

            fehler.message!! shouldContain "<<EMPFAENGER>>"
        }

        test("ein Zeilenwechsel über das Makro der Vorlage besteht die Prüfung") {
            val anschrift = "Muster GmbH".escapeLatex() + Latex.macro(TemplateMacro.ZEILENWECHSEL) + "Musterweg 1".escapeLatex()

            InvoiceRenderer.checkLineBreaks(mapOf("EMPFAENGER" to anschrift))
        }

        test("zwei Backslashes aus den Eingabedaten sind kein Zeilenende") {
            // escapeLatex macht aus jedem einzelnen \textbackslash{} – deshalb
            // kann keine Rechnungsdatei die Prüfung auslösen.
            InvoiceRenderer.checkLineBreaks(mapOf("SCHLUSSSATZ" to "Ablage unter \\\\server\\rechnungen".escapeLatex()))
        }
    }

    context("Die Kommentarzeilen der Vorlage") {
        // #149: Die Kommentare erklären invoice.tex, im erzeugten .tex liest
        // sie niemand – und eine geänderte Kommentarzeile soll keinen Test rot
        // machen.

        test("eine ganze Kommentarzeile verschwindet samt Zeilenende, auch eingerückt") {
            InvoiceRenderer.withoutCommentLines("a\n%% Kommentar\n  % eingerückt\nb\n") shouldBe "a\nb\n"
        }

        test("ein % hinter Code bleibt stehen") {
            // Am Zeilenende einer Makrodefinition unterdrückt es den
            // Zwischenraum; gestrichen, änderte sich das Satzbild.
            val vorlage = "\\newcommand{\\x}[1]{%\n  #1}%\n\\usepackage{booktabs}  %% Hinweis\n"

            InvoiceRenderer.withoutCommentLines(vorlage) shouldBe vorlage
        }

        test("ein maskiertes Prozentzeichen am Zeilenanfang ist kein Kommentar") {
            InvoiceRenderer.withoutCommentLines("\\% Rabatt\n") shouldBe "\\% Rabatt\n"
        }

        test("im gesetzten LaTeX steht keine Kommentarzeile der Vorlage") {
            renderMit(testItem()).lines().filter { it.trimStart().startsWith("%") }.shouldBeEmpty()
        }
    }

    context("Das Land in der Anschrift") {

        /** Die eine Zeile, in der die Anschrift des Kunden steht. */
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
            val tex = InvoiceRenderer.render(CalculatedInvoice(invoice, stammdaten).accepted())
            return tex.lines().first { it.contains("Nürnberg") }.trim()
        }

        test("ein kleingeschriebenes Land ist dasselbe Land") {
            // Der Defekt, der diesen Typ ausgelöst hat: Vorher stand hier eine
            // Zeile "de" unter der Anschrift, während der Calculator dieselbe
            // Rechnung als Inlandsrechnung behandelte.
            anschrift(kundenland = "de", stellerland = "DE") shouldBe
                """Beispiel Software GmbH\zeilenwechsel{}Industriestraße 1\zeilenwechsel{}90402 Nürnberg"""
        }

        test("Inland ist relativ zum Rechnungssteller, nicht zu Deutschland") {
            // Sitzt der Rechnungssteller in Österreich, ist ein deutscher Kunde
            // für ihn Ausland – und bekommt sein Land genannt. Vorher stand
            // "DE" fest im Code, und diese Rechnung hätte es verschwiegen.
            anschrift(kundenland = "DE", stellerland = "AT") shouldBe
                """Beispiel Software GmbH\zeilenwechsel{}Industriestraße 1\zeilenwechsel{}90402 Nürnberg\zeilenwechsel{}DE"""
        }

        test("das Land des Kunden wird genannt, wenn es ein anderes ist") {
            anschrift(kundenland = "at", stellerland = "DE") shouldBe
                """Beispiel Software GmbH\zeilenwechsel{}Industriestraße 1\zeilenwechsel{}90402 Nürnberg\zeilenwechsel{}AT"""
        }
    }

    test("ein Kleinstpreis steht so da, dass man die Zeile nachrechnen kann") {
        // Der Fall aus #14: Vorher stand hier "10.000 & Std. & 0,00 € & 0,10 €"
        // – die Summe stimmte, aber der Einzelpreis war gerundet.
        val tex = renderMit(testItem(quantity = "10000", unitPrice = "0.00001"))

        tex shouldContain "{10.000}{Std.}{0,00001~€}{0,10~€}"
    }

    context("Abschlagsrechnung") {
        fun abschlag(receivedOn: LocalDate? = null): String {
            val invoice = testInvoice(type = InvoiceType.PREPAYMENT, receivedOn = receivedOn)
            return InvoiceRenderer.render(CalculatedInvoice(invoice, config).accepted())
        }

        test("der Balken nennt die Rechnungsart") {
            abschlag() shouldContain "Abschlagsrechnung R26-001"
        }

        test("der voraussichtliche Zeitraum steht in Monaten") {
            val invoice = YamlLoader.parseInvoice(resource("abschlagsrechnung.yaml"))
            val seller = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))

            InvoiceRenderer.render(CalculatedInvoice(invoice, seller).accepted()) shouldContain
                "Voraussichtlicher Leistungszeitraum: \\zusammen{Juni bis Oktober 2026}"
        }

        test("der Zeitraum ist der voraussichtliche") {
            // UStAE 14.8 Abs. 1: Auf einer Abschlagsrechnung ist der Zeitraum
            // eine Prognose. Als "Leistungszeitraum" beschriftet, behauptete er,
            // die Leistung sei erbracht.
            abschlag() shouldContain "Voraussichtlicher Leistungszeitraum:"
            renderMit(testItem()) shouldContain "Leistungszeitraum: \\zusammen{Juli 2026}"
            renderMit(testItem()) shouldNotContain "Voraussichtlich"
        }

        test("der Pflichtsatz steht unter den Summen") {
            val tex = abschlag()

            tex shouldContain "Die Leistung ist noch nicht erbracht."
            // Nicht nur, DASS er dasteht: Über der Positionstabelle wäre der
            // Satz eine Ankündigung, unter dem Gesamtbetrag ist er der Vorbehalt
            // zu genau dieser Summe.
            tex.indexOf("Die Leistung ist noch nicht erbracht.") shouldBeGreaterThan tex.indexOf("Gesamtbetrag")
            renderMit(testItem()) shouldNotContain "noch nicht erbracht"
        }

        test("auch der Zeitraum einer Position ist der voraussichtliche") {
            val item =
                testItem(description = "Arbeitspaket Nord").copy(
                    servicePeriod = ServicePeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
                )
            val invoice = testInvoice(type = InvoiceType.PREPAYMENT, items = listOf(item))

            InvoiceRenderer.render(CalculatedInvoice(invoice, config).accepted()) shouldContain
                "Voraussichtlicher Zeitraum: September 2026"
        }

        test("die Vereinnahmung wird genannt, sobald sie feststeht") {
            abschlag(LocalDate.of(2026, 8, 10)) shouldContain "Abschlag vereinnahmt am 10.~August 2026"
            abschlag() shouldNotContain "vereinnahmt"
        }

        test("die Metadaten des PDF tragen dieselbe Bezeichnung") {
            val invoice = testInvoice(type = InvoiceType.PREPAYMENT)

            val xmp = InvoiceRenderer.renderMetadata(CalculatedInvoice(invoice, config).accepted())

            xmp shouldContain "\\Title{Abschlagsrechnung R26-001}"
            xmp shouldContain "\\Subject{Abschlagsrechnung R26-001 vom 14. August 2026}"
        }
    }
    context("Schlussrechnung") {
        fun schlussrechnung(items: List<LineItem>, vararg prepayments: Prepayment): String {
            val invoice = testInvoice(type = InvoiceType.FINAL, items = items, prepayments = prepayments.toList())
            return InvoiceRenderer.render(CalculatedInvoice(invoice, config).accepted())
        }

        val gesamtleistung = listOf(testItem(quantity = "1", unitPrice = "20000.00"))

        test("die Abschläge stehen unter dem Gesamtbetrag, nicht in der Positionstabelle") {
            // ADR-0002: Ein Abschlag ist keine negative Position. Stünde er in
            // der Tabelle, senkte er die Bemessungsgrundlage und damit die
            // ausgewiesene Umsatzsteuer.
            val tex = schlussrechnung(gesamtleistung, testPrepayment())

            tex.indexOf("R26-009") shouldBeGreaterThan tex.indexOf("Gesamtbetrag")
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
            tex shouldContain "\\bankblock{Kontoinhaber"
        }

        test("ein Restbetrag von null bekommt einen Satz statt einer Zahl") {
            // Ein nacktes "0,00 €" unter dem Strich sieht aus wie ein Fehler.
            val tex = schlussrechnung(listOf(testItem(quantity = "1", unitPrice = "4000.00")), testPrepayment())

            tex shouldContain "es verbleibt kein Restbetrag"
            // Keine Abschlusszeile mit Betrag. Bis #94 stand hier
            // "\textbf{Restbetrag}" – das schreibt der Renderer seit #76 nirgends,
            // die Zeile bestand also immer.
            tex shouldNotContain "\\abschlusszeile{Restbetrag}"
            // Die Gegenprobe steht bei den Angaben oben rechts: Eine
            // gewöhnliche Rechnung nennt sehr wohl ein Zahlungsziel.
            renderMit(testItem()) shouldContain "Zahlbar ohne Abzug bis"
            // Und kein Zahlungsziel daneben: Das wäre die zweite, andere
            // Aussage desselben Blattes.
            tex shouldNotContain "Zahlbar ohne Abzug"
            // Ebenso wenig ein Konto: Wer nichts zu zahlen hat, bekommt keines
            // genannt (#94). Die IBAN in der Fußzeile bleibt.
            tex shouldNotContain "\\bankblock{"
        }

        test("eine gewöhnliche Rechnung bekommt keine Absetzung") {
            renderMit(testItem()) shouldNotContain "Abzüglich"
        }
    }

    context("Skonto") {
        // Rechnungsdatum 14.08.2026, Zahlungsziel 14 Tage.
        fun mitSkonto(vararg stufen: SkontoTier): String =
            InvoiceRenderer.render(CalculatedInvoice(testInvoice(skonto = stufen.toList()), config).accepted())

        test("eine Stufe wird ein Satz unter den Summen") {
            val tex = mitSkonto(SkontoTier(rate = Percent("2"), days = 7))

            tex shouldContain
                "\\hinweisabsatz{Bei Zahlung bis~\\zusammen{21.~August 2026} können 2~\\% " +
                "Skonto abgezogen werden.}"
            tex.indexOf("Skonto abgezogen") shouldBeGreaterThan tex.indexOf("Gesamtbetrag")
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
            mitSkonto(SkontoTier(rate = Percent("2"), days = 7)) shouldContain
                "Zahlbar ohne Abzug bis 28.~August 2026"
        }

        test("ohne Skonto kein Satz") {
            renderMit(testItem()) shouldNotContain "Skonto"
        }
    }

    context("Pflichtangaben und was fehlen darf") {
        // Keine dieser Angaben stand bis #95 in einem Test oder einer der
        // Vergleichsdateien: Deren Stammdaten haben alles, und keine ihrer
        // Rechnungen ist steuerfrei. Geprüft wird am Inhalt, nicht am Makro.

        fun render(invoice: Invoice, stammdaten: SellerConfig = config): String =
            InvoiceRenderer.render(CalculatedInvoice(invoice, stammdaten).accepted())

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

            tex shouldContain "zzgl. 19~\\% USt."
            tex shouldNotContain "zzgl. 0"
        }

        test("die USt-IdNr. des Kunden steht unter seiner Anschrift, wenn er eine hat") {
            // §14a UStG. Geprüft wird der Aufruf samt Nummer: Die Fußzeile trägt
            // ohnehin die USt-IdNr. des Rechnungsstellers, das Wort allein stünde
            // also immer da.
            val kundenId = VatId("ATU12345678")

            render(testInvoice(customerVatId = kundenId)) shouldContain "\\ustidtrenner{}USt-IdNr. ${kundenId.formatted()}"
            render(testInvoice()) shouldNotContain "\\ustidtrenner{}"
        }

        test("der Inhaber steht im Briefkopf nur, wenn es keine Geschäftsführung gibt") {
            // Unter der Grußformel steht er immer (<<INHABER>>), deshalb wird die
            // Zeile des Briefkopfs selbst verglichen.
            val stammdaten = config.copy(ownerName = "Ina Inhaberin")

            fun briefkopf(tex: String) = tex.lines().first { it.contains("Muster IT-Consulting}") }

            val ohne = render(testInvoice(), stammdaten)
            briefkopf(ohne) shouldContain "}\\kopfperson{Ina Inhaberin}"
            ohne shouldNotContain "Geschäftsführung"

            val mit = render(testInvoice(), stammdaten.copy(managingDirector = "Gero Geschäftsführer"))
            briefkopf(mit) shouldNotContain "Ina Inhaberin"
            mit shouldContain "Geschäftsführung\\zeilenwechsel{}Gero Geschäftsführer"
        }

        test("Telefon, Registergericht und Referenz stehen nur da, wenn sie angegeben sind") {
            val ohne = render(testInvoice())
            ohne shouldNotContain "Tel."
            ohne shouldNotContain "Registergericht"
            ohne shouldNotContain "Ihre Referenz"

            val basis = testInvoice()
            val mitReferenz = basis.copy(customer = basis.customer.copy(buyerReference = "Bestellung 4711"))
            val stammdaten = config.copy(phone = "+49 931 000000", registerCourt = "Würzburg", registerNumber = "HRB 1")
            val mit = render(mitReferenz, stammdaten)
            mit shouldContain "Tel. +49 931 000000"
            mit shouldContain "Registergericht Würzburg, HRB 1"
            mit shouldContain "Ihre Referenz: Bestellung 4711"
        }
    }

    context("Vorlage und Renderer müssen dieselben Makros kennen") {
        // Dieselbe Sorte Prüfung wie bei den Platzhaltern (#30), eine Ebene
        // tiefer: Ein vertippter Makroname übersetzt klaglos und fiele sonst
        // erst LuaLaTeX auf.

        /** Eine Vorlage, die alle Makros kennt – bis auf die genannten Abweichung. */
        fun vorlageOhne(fehlend: TemplateMacro? = null, falscheStelligkeit: TemplateMacro? = null) =
            TemplateMacro.entries
                .filterNot { it == fehlend }
                .joinToString("\n") { macro ->
                    val stelligkeit = if (macro == falscheStelligkeit) macro.arity + 1 else macro.arity
                    "\\newcommand{\\${macro.macroName}}[$stelligkeit]{}"
                }

        test("ein Makro, das die Vorlage nicht kennt, wird benannt") {
            val fehler =
                shouldThrow<IllegalStateException> {
                    InvoiceRenderer.checkMacros(vorlageOhne(fehlend = TemplateMacro.POSITIONSZEILE))
                }

            fehler.message!! shouldContain "positionszeile"
        }

        test("eine falsche Stelligkeit fällt ebenfalls auf") {
            val fehler =
                shouldThrow<IllegalStateException> {
                    InvoiceRenderer.checkMacros(vorlageOhne(falscheStelligkeit = TemplateMacro.POSITIONSZEILE))
                }

            fehler.message!! shouldContain "positionszeile"
            fehler.message!! shouldContain "6"
        }

        test("ein auskommentiertes Makro gilt nicht als definiert") {
            // Die Vorlage wird gestrichen geprüft, so wie render sie prüft:
            // Ein \newcommand hinter % definiert für TeX nichts.
            val vorlage =
                vorlageOhne(fehlend = TemplateMacro.POSITIONSZEILE) +
                    "\n% \\newcommand{\\positionszeile}[${TemplateMacro.POSITIONSZEILE.arity}]{}"

            val fehler =
                shouldThrow<IllegalStateException> {
                    InvoiceRenderer.checkMacros(InvoiceRenderer.withoutCommentLines(vorlage))
                }

            fehler.message!! shouldContain "positionszeile"
        }

        test("die echte Vorlage passt zum Renderer") {
            // Der Positivfall, damit die Prüfung nicht nur beim Kaputten greift.
            InvoiceRenderer.checkMacros(
                checkNotNull(InvoiceRenderer::class.java.getResource("/templates/invoice.tex")).readText(),
            )
        }
    }
})
