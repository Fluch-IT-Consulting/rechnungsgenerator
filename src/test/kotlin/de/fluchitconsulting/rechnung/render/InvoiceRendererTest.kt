package de.fluchitconsulting.rechnung.render

import de.fluchitconsulting.rechnung.InvoiceCalculator
import de.fluchitconsulting.rechnung.YamlLoader
import de.fluchitconsulting.rechnung.model.ServicePeriod
import de.fluchitconsulting.rechnung.testInvoice
import de.fluchitconsulting.rechnung.testItem
import de.fluchitconsulting.rechnung.testSellerConfig
import io.kotest.core.spec.style.FunSpec
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
})
