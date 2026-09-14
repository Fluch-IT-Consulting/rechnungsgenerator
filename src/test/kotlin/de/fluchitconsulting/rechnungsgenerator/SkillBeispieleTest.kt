package de.fluchitconsulting.rechnungsgenerator

import de.fluchitconsulting.rechnungsgenerator.calculation.InvoiceCalculator
import de.fluchitconsulting.rechnungsgenerator.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.rules.aborts
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.values.Money
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Prüft die YAML-Beispiele im `rechnung`-Skill gegen den echten Parser.
 *
 * Doku, die nicht mehr stimmt, ist schlimmer als keine: Der Skill schreibt die
 * Rechnungsdateien, und ein Beispiel mit einem Feld, das es nicht mehr gibt,
 * führt ihn beim nächsten Mal in einen Abbruch. Der Test kostet Millisekunden
 * und wird rot, sobald sich das Format ändert, ohne dass die Beispiele
 * mitziehen.
 *
 * Der Kundenblock steht in den Beispielen als `kunde: …` – er ist dort verkürzt,
 * weil ihn der Abschnitt darüber schon zeigt, und wird hier ergänzt.
 */
class SkillBeispieleTest : FunSpec({

    val kunde =
        """
        kunde:
          name: Beispiel Software GmbH
          anschrift:
            strasse: Industriestraße 1
            plz: "90402"
            ort: Nürnberg
        """.trimIndent()

    test("die YAML-Beispiele im Skill sind gültige Rechnungsdateien") {
        val config = YamlLoader.parseSellerConfig(File("src/test/resources/stammdaten.yaml").readText())
        // Die Schlussrechnung steht in einer eigenen Datei des Skills. In der
        // Reihenfolge SKILL.md, dann schlussrechnung.md ergeben die Beispiele
        // Rechnung, Abschlagsrechnung, Stornorechnung, Schlussrechnung.
        val skill =
            listOf("SKILL.md", "schlussrechnung.md")
                .joinToString("\n") { File(".claude/skills/rechnung/$it").readText() }

        val invoices =
            Regex("```yaml\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
                .findAll(skill)
                .map { it.groupValues[1] }
                .filter { it.startsWith("nummer:") }
                .map { YamlLoader.parseInvoice(it.replace("kunde: …", kunde)) }
                .toList()

        // Die Anzahl mitprüfen: Sonst wäre der Test auch dann grün, wenn das
        // Suchmuster nichts mehr findet.
        invoices.map { it.type } shouldBe
            listOf(InvoiceType.INVOICE, InvoiceType.PREPAYMENT, InvoiceType.CANCELLATION, InvoiceType.FINAL)

        val schlussrechnung = invoices.last()
        val totals = InvoiceCalculator.calculate(schlussrechnung, config)
        totals.aborts().shouldBeEmpty()
        totals.grossTotal shouldBe Money("23800.00")
        totals.remainingAmount shouldBe Money("19040.00")

        // Und die beiden anderen wenigstens durchrechnen lassen: Auch eine
        // gültige Datei kann eine widersprüchliche Rechnung beschreiben.
        invoices.dropLast(1).forEach { InvoiceCalculator.calculate(it, config).aborts().shouldBeEmpty() }
    }
})
