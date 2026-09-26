package de.fluchitconsulting.rechnungsgenerator.generate.einvoice

import de.fluchitconsulting.rechnungsgenerator.core.accepted.accepted
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mustangproject.validator.ZUGFeRDValidator
import java.nio.file.Path
import kotlin.io.path.readText

private fun resource(name: String): String = Path.of("src/test/resources/$name").readText()

private fun exampleXml(): String {
    val seller = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))
    val invoice = YamlLoader.parseInvoice(resource("rechnung.yaml"))
    return CiiWriter.write(CalculatedInvoice(invoice, seller).accepted())
}

/**
 * Das XML der Beispielrechnung, zeichengleich und geprüft.
 *
 * Die Vergleichsdatei ist wie `rechnung-erwartet.tex` eine Frage und keine
 * Aufgabe: Wird der Test rot, erst nachsehen, ob die Änderung gewollt ist.
 * Der Vergleich allein sagt aber nicht, ob das XML gilt – deshalb prüft
 * Mustang es daneben, so wie ihn auch `erzeuge --zugferd` prüft (ADR-0023).
 */
class CiiWriterTest : FunSpec({

    test("die Beispielrechnung ergibt das erwartete XML") {
        exampleXml() shouldBe resource("rechnung-erwartet.xml")
    }

    /**
     * Gültig heißt für Mustang auch "gültig mit Warnungen". Erlaubt ist genau
     * eine, die zum leeren `ApplicableHeaderTradeDelivery` (siehe `CiiWriter`);
     * jede weitere wäre ein Befund, der sonst unbemerkt durchginge.
     */
    test("das XML ist gültig, mit keiner Warnung außer PEPPOL-EN16931-R008") {
        val validator = ZUGFeRDValidator()
        val report = validator.validate(exampleXml().toByteArray(), "factur-x.xml")

        validator.wasCompletelyValid() shouldBe true
        warningIds(report) shouldBe listOf("PEPPOL-EN16931-R008")
    }
})

/** Die Kennungen der Warnungen im Bericht, etwa `[ID PEPPOL-EN16931-R008]`. */
private fun warningIds(report: String): List<String> =
    Regex("""<warning[^>]*>[^<]*\[ID ([^\]]+)]""").findAll(report).map { it.groupValues[1] }.toList()
