package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import de.fluchitconsulting.rechnungsgenerator.core.model.exampleInvoices
import de.fluchitconsulting.rechnungsgenerator.core.model.taxCategoryExamples
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Das Pflichtangaben-Netz ([PflichtangabenRun]) an den Beispielen je
 * Rechnungsart und je Steuerkategorie mit Vorgabegrund, auf Deutsch und in
 * jedem Design.
 */
class PflichtangabenArtenTest : FunSpec({
    tags(Toolchain)

    val net = PflichtangabenRun()
    val designs = net.designs

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

    // Die Kategorien, die einen Grund mitbringen (#427), auf Deutsch; auf
    // Englisch setzt sie der PflichtangabenEnglischTest. Dass der Grund
    // dasteht und die USt-IdNr. des Kunden, wo die Datei sie nennt, prüft
    // verify; hier steht er wörtlich (defaultReasonOnSheet).
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
