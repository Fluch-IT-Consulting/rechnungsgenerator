package de.fluchitconsulting.rechnungsgenerator

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.core.rules.aborts
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.io.File

/** Der Kundenblock steht in den Beispielen verkürzt als `kunde: …` und wird hier ergänzt. */
class SkillExamplesTest : FunSpec({

    val customer =
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
        val skill =
            listOf("SKILL.md", "schlussrechnung.md")
                .joinToString("\n") { File(".claude/skills/rechnung/$it").readText() }

        val invoices =
            Regex("```yaml\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
                .findAll(skill)
                .map { it.groupValues[1] }
                .filter { it.startsWith("nummer:") }
                .map { YamlLoader.parseInvoice(it.replace("kunde: …", customer)) }
                .toList()

        // Mit der Anzahl, sonst bliebe der Test grün, wenn das Suchmuster nichts findet.
        invoices.map { it.type } shouldBe
            listOf(InvoiceType.INVOICE, InvoiceType.PREPAYMENT, InvoiceType.CANCELLATION, InvoiceType.FINAL)

        val finalInvoice = invoices.last()
        val totals = CalculatedInvoice(finalInvoice, config)
        totals.aborts().shouldBeEmpty()
        totals.grossTotal shouldBe Money("23800.00")
        totals.remainingAmount shouldBe Money("19040.00")

        invoices.dropLast(1).forEach { CalculatedInvoice(it, config).aborts().shouldBeEmpty() }
    }
})
