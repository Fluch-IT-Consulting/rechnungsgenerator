package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import de.fluchitconsulting.rechnungsgenerator.core.accepted.accepted
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.core.model.exampleInvoices
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import de.fluchitconsulting.rechnungsgenerator.generate.einvoice.CiiWriter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Das Pflichtangaben-Netz ([PflichtangabenRun]) an jedem Beispiel einer
 * Rechnungsart auf Englisch, in beiden Varianten und jedem Design (ADR-0025).
 */
class PflichtangabenEnglischTest : FunSpec({
    tags(Toolchain)

    val net = PflichtangabenRun()
    val designs = net.designs

    // Jedes Beispiel auf Englisch (#377, #406), in beiden Varianten (#407):
    // Es entsteht ein PDF, samt dem XML der E-Rechnung. Die Freitexte der
    // Dateien bleiben deutsch; geprüft ist, dass jeder Satz aus Kotlin einen
    // englischen Wortlaut hat.
    for (language in listOf(Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH)) {
        for ((_, file) in exampleInvoices) {
            test("$file auf ${language.tag}: gültiges PDF/A-1b mit allen Pflichtangaben") {
                val input = net.workDir.resolve("$file-${language.tag}.yaml")
                input.writeText("sprache: ${language.tag}\n" + Path.of("src/test/resources/$file.yaml").readText())
                val calculated = CalculatedInvoice(YamlLoader.loadInvoice(input), YamlLoader.loadSellerConfig(net.sellerConfig))

                calculated.invoice.language shouldBe language
                CiiWriter.write(calculated.accepted())
                designs.forEach { design ->
                    net.typesetAndCheck(
                        file,
                        design,
                        design.sellerConfig,
                        targetName = "${design.name}-$file-${language.tag}",
                        input = input,
                    )
                }
            }
        }
    }
})
