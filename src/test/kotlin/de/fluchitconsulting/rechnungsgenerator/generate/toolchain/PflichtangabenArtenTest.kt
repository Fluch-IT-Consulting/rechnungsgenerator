package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import de.fluchitconsulting.rechnungsgenerator.core.model.exampleInvoices
import de.fluchitconsulting.rechnungsgenerator.core.model.taxCategoryExamples
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PflichtangabenArtenTest : FunSpec({
    tags(Toolchain)

    val net = PflichtangabenRun()
    val designs = net.designs

    // Gilt für alle Klassen des Netzes, denn sie teilen die `designs`.
    test("jedes Design steht im Netz, in jeder Sprache") {
        designs.map { it.name } shouldBe
            de.fluchitconsulting.rechnungsgenerator.core.model.Design.entries
                .map { it.label }
        designs.forEach { design -> withClue(design.name) { design.words.keys shouldBe Language.entries.toSet() } }
    }

    for (design in designs) {
        for ((invoiceType, file) in exampleInvoices) {
            test("$file im Design ${design.name}: gültiges PDF/A-1b mit allen Pflichtangaben") {
                val (calculated, _) = net.typesetAndCheck(file, design, design.sellerConfig, targetName = "${design.name}-$file")
                calculated.invoice.type shouldBe invoiceType
            }
        }
    }

    // #427
    for (design in designs) {
        for ((category, file) in taxCategoryExamples) {
            test("$file im Design ${design.name}: gültiges PDF/A-1b mit dem Grund der Kategorie ${category.code}") {
                val (calculated, sheet) = net.typesetAndCheck(file, design, design.sellerConfig, targetName = "${design.name}-$file")

                calculated.vatGroups.map { it.category } shouldBe listOf(category)
                sheet.shows("Hinweis auf die Steuerbefreiung", ": ${defaultReasonOnSheet(category, Language.GERMAN)}.")
            }
        }
    }
})
