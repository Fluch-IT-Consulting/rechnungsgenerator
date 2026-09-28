package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import de.fluchitconsulting.rechnungsgenerator.core.model.exampleInvoices
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Das Pflichtangaben-Netz ([PflichtangabenRun]) an den Beispielen je
 * Rechnungsart, auf Deutsch und in jedem Design.
 */
class PflichtangabenArtenTest : FunSpec({
    tags(Toolchain)

    val net = PflichtangabenRun()
    val designs = net.designs

    test("jedes Design steht im Netz, in jeder Sprache") {
        designs.map { it.name } shouldBe
            de.fluchitconsulting.rechnungsgenerator.core.values.Design.entries
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
})
