package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import de.fluchitconsulting.rechnungsgenerator.core.accepted.accepted
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.core.model.exampleInvoices
import de.fluchitconsulting.rechnungsgenerator.core.model.taxCategoryExamples
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import de.fluchitconsulting.rechnungsgenerator.generate.einvoice.CiiWriter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** ADR-0025 */
class PflichtangabenEnglischTest : FunSpec({
    tags(Toolchain)

    val net = PflichtangabenRun()
    val designs = net.designs

    fun typesetIn(
        language: Language,
        file: String,
    ): List<Typeset> {
        val input = net.workDir.resolve("$file-${language.tag}.yaml")
        input.writeText("sprache: ${language.tag}\n" + Path.of("src/test/resources/$file.yaml").readText())
        val calculated = CalculatedInvoice(YamlLoader.loadInvoice(input), YamlLoader.loadSellerConfig(net.sellerConfig))

        calculated.invoice.language shouldBe language
        CiiWriter.write(calculated.accepted())
        return designs.map { design ->
            net.typesetAndCheck(
                file,
                design,
                design.sellerConfig,
                targetName = "${design.name}-$file-${language.tag}",
                input = input,
            )
        }
    }

    // #377, #406, #407. Die Freitexte der Dateien bleiben deutsch.
    for (language in listOf(Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH)) {
        for ((_, file) in exampleInvoices) {
            test("$file auf ${language.tag}: gültiges PDF/A-1b mit allen Pflichtangaben") {
                typesetIn(language, file)
            }
        }

        // #406, #427
        for ((category, file) in taxCategoryExamples) {
            test("$file auf ${language.tag}: gültiges PDF/A-1b mit dem Grund der Kategorie ${category.code}") {
                typesetIn(language, file).forEach { (calculated, sheet) ->
                    calculated.vatGroups.map { it.category } shouldBe listOf(category)
                    sheet.shows("Hinweis auf die Steuerbefreiung", ": ${defaultReasonOnSheet(category, language)}.")
                    sheet.lacks("Deutsch auf dem Blatt", "Steuerschuldnerschaft", "Steuerfrei", "Rechnung", "Umsatzsteuer")
                }
            }
        }
    }
})
