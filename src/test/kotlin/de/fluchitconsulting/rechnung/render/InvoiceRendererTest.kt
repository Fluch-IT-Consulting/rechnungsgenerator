package de.fluchitconsulting.rechnung.render

import de.fluchitconsulting.rechnung.InvoiceCalculator
import de.fluchitconsulting.rechnung.YamlLoader
import de.fluchitconsulting.rechnung.model.LineItem
import de.fluchitconsulting.rechnung.model.Prepayment
import de.fluchitconsulting.rechnung.model.ServicePeriod
import de.fluchitconsulting.rechnung.testInvoice
import de.fluchitconsulting.rechnung.testItem
import de.fluchitconsulting.rechnung.testPrepayment
import de.fluchitconsulting.rechnung.testSellerConfig
import de.fluchitconsulting.rechnung.values.CountryCode
import de.fluchitconsulting.rechnung.values.InvoiceType
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
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

    fun renderMit(vararg items: de.fluchitconsulting.rechnung.model.LineItem): String {
        val invoice = testInvoice(items = items.toList())
        return InvoiceRenderer.render(invoice, config, InvoiceCalculator.calculate(invoice, config))
    }

    test("eine Position ohne Zusatzangaben bleibt einzeilig") {
        val tex = renderMit(testItem())

        tex shouldContain "Testposition"
        // Kein Umbruch, keine zweite Zeile: Die schlichte Position soll so
        // schlicht aussehen wie vorher auch.
        tex shouldNotContain "Testposition\\newline"
    }

    test("die Beschreibung wird zur zweiten Zeile, klein und gedämpft") {
        val tex = renderMit(testItem(description = "Rahmenvertrag vom 01.02.2026"))

        tex shouldContain "Testposition\\newline{\\footnotesize\\color{gedaempft} Rahmenvertrag vom 01.02.2026}"
    }

    test("Beschreibung und Hinweis stehen zusammen in der zweiten Zeile") {
        val tex = renderMit(testItem(description = "Arbeitspaket Nord", note = "Abschlag September 2026"))

        tex shouldContain "Arbeitspaket Nord · Abschlag September 2026"
    }

    test("ein abweichender Zeitraum der Position wird benannt") {
        val item =
            testItem(description = "Arbeitspaket Nord").copy(
                servicePeriod = ServicePeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
            )

        renderMit(item) shouldContain "Arbeitspaket Nord · Zeitraum: September 2026"
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

        InvoiceRenderer.render(invoice, seller, InvoiceCalculator.calculate(invoice, seller)) shouldBe
            resource("rechnung-erwartet.tex")
    }

    /**
     * Dasselbe für die beiden anderen Arten. Sie tragen das meiste Markup –
     * Absetzungstabelle, Makros, Pflichtsatz –, also gerade das, was ein Umbau
     * an der Maskierung durcheinanderbringen könnte.
     */
    test("Abschlags- und Schlussrechnung werden gesetzt wie festgehalten") {
        val seller = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))

        listOf("abschlagsrechnung", "schlussrechnung").forEach { name ->
            val invoice = YamlLoader.parseInvoice(resource("$name.yaml"))

            withClue(name) {
                InvoiceRenderer.render(invoice, seller, InvoiceCalculator.calculate(invoice, seller)) shouldBe
                    resource("$name-erwartet.tex")
            }
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
            val tex = InvoiceRenderer.render(invoice, stammdaten, InvoiceCalculator.calculate(invoice, stammdaten))
            return tex.lines().first { it.contains("Nürnberg") }.trim()
        }

        test("ein kleingeschriebenes Land ist dasselbe Land") {
            // Der Defekt, der diesen Typ ausgelöst hat: Vorher stand hier eine
            // Zeile "de" unter der Anschrift, während der Calculator dieselbe
            // Rechnung als Inlandsrechnung behandelte.
            anschrift(kundenland = "de", stellerland = "DE") shouldBe
                """Beispiel Software GmbH\\Industriestraße 1\\90402 Nürnberg"""
        }

        test("Inland ist relativ zum Rechnungssteller, nicht zu Deutschland") {
            // Sitzt der Rechnungssteller in Österreich, ist ein deutscher Kunde
            // für ihn Ausland – und bekommt sein Land genannt. Vorher stand
            // "DE" fest im Code, und diese Rechnung hätte es verschwiegen.
            anschrift(kundenland = "DE", stellerland = "AT") shouldBe
                """Beispiel Software GmbH\\Industriestraße 1\\90402 Nürnberg\\DE"""
        }

        test("das Land des Kunden wird genannt, wenn es ein anderes ist") {
            anschrift(kundenland = "at", stellerland = "DE") shouldBe
                """Beispiel Software GmbH\\Industriestraße 1\\90402 Nürnberg\\AT"""
        }
    }

    test("ein Kleinstpreis steht so da, dass man die Zeile nachrechnen kann") {
        // Der Fall aus #14: Vorher stand hier "10.000 & Std. & 0,00 € & 0,10 €"
        // – die Summe stimmte, aber der Einzelpreis war gerundet.
        val tex = renderMit(testItem(quantity = "10000", unitPrice = "0.00001"))

        tex shouldContain "10.000 & Std. & 0,00001~€ & 0,10~€"
    }

    context("Abschlagsrechnung") {
        fun abschlag(receivedOn: LocalDate? = null): String {
            val invoice = testInvoice(type = InvoiceType.PREPAYMENT, receivedOn = receivedOn)
            return InvoiceRenderer.render(invoice, config, InvoiceCalculator.calculate(invoice, config))
        }

        test("der Balken nennt die Rechnungsart") {
            abschlag() shouldContain "Abschlagsrechnung R26-001"
        }

        test("der voraussichtliche Zeitraum steht in Monaten") {
            val invoice = YamlLoader.parseInvoice(resource("abschlagsrechnung.yaml"))
            val seller = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))

            InvoiceRenderer.render(invoice, seller, InvoiceCalculator.calculate(invoice, seller)) shouldContain
                "Voraussichtlicher Leistungszeitraum: \\mbox{Juni bis Oktober 2026}"
        }

        test("der Zeitraum ist der voraussichtliche") {
            // UStAE 14.8 Abs. 1: Auf einer Abschlagsrechnung ist der Zeitraum
            // eine Prognose. Als "Leistungszeitraum" beschriftet, behauptete er,
            // die Leistung sei erbracht.
            abschlag() shouldContain "Voraussichtlicher Leistungszeitraum:"
            renderMit(testItem()) shouldContain "Leistungszeitraum: \\mbox{Juli 2026}"
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

            InvoiceRenderer.render(invoice, config, InvoiceCalculator.calculate(invoice, config)) shouldContain
                "Voraussichtlicher Zeitraum: September 2026"
        }

        test("die Vereinnahmung wird genannt, sobald sie feststeht") {
            abschlag(LocalDate.of(2026, 8, 10)) shouldContain "Abschlag vereinnahmt am 10. August 2026"
            // Auf die Zeile prüfen, nicht auf das Wort: Es steht auch in einem
            // Kommentar der Vorlage, und die wird mit gerendert.
            abschlag() shouldNotContain "Abschlag vereinnahmt am"
        }

        test("die Metadaten des PDF tragen dieselbe Bezeichnung") {
            val invoice = testInvoice(type = InvoiceType.PREPAYMENT)

            val xmp = InvoiceRenderer.renderMetadata(invoice, config)

            xmp shouldContain "\\Title{Abschlagsrechnung R26-001}"
            xmp shouldContain "\\Subject{Abschlagsrechnung R26-001 vom 14. August 2026}"
        }
    }
    context("Schlussrechnung") {
        fun schlussrechnung(items: List<LineItem>, vararg prepayments: Prepayment): String {
            val invoice = testInvoice(type = InvoiceType.FINAL, items = items, prepayments = prepayments.toList())
            return InvoiceRenderer.render(invoice, config, InvoiceCalculator.calculate(invoice, config))
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

        test("der Restbetrag steht unter dem Strich") {
            val tex = schlussrechnung(gesamtleistung, testPrepayment())

            tex shouldContain "\\textbf{Restbetrag}"
            tex shouldContain "\\textbf{19.040,00~€}"
        }

        test("ein Restbetrag von null bekommt einen Satz statt einer Zahl") {
            // Ein nacktes "0,00 €" unter dem Strich sieht aus wie ein Fehler.
            val tex = schlussrechnung(listOf(testItem(quantity = "1", unitPrice = "4000.00")), testPrepayment())

            tex shouldContain "es verbleibt kein Restbetrag"
            // Die Gegenprobe steht bei den Angaben oben rechts: Eine
            // gewöhnliche Rechnung nennt sehr wohl ein Zahlungsziel.
            renderMit(testItem()) shouldContain "Zahlbar ohne Abzug bis"
            tex shouldNotContain "\\textbf{Restbetrag}"
            // Und kein Zahlungsziel daneben: Das wäre die zweite, andere
            // Aussage desselben Blattes.
            tex shouldNotContain "Zahlbar ohne Abzug"
        }

        test("eine gewöhnliche Rechnung bekommt keine Absetzung") {
            renderMit(testItem()) shouldNotContain "Abzüglich"
        }
    }
})
