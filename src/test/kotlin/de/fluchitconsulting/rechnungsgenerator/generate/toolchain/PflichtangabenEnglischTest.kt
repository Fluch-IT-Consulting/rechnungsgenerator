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

/**
 * Das Pflichtangaben-Netz ([PflichtangabenRun]) an jedem Beispiel einer
 * Rechnungsart und jeder Steuerkategorie mit Vorgabegrund auf Englisch, in
 * beiden Varianten und jedem Design (ADR-0025).
 */
class PflichtangabenEnglischTest : FunSpec({
    tags(Toolchain)

    val net = PflichtangabenRun()
    val designs = net.designs

    // Setzt die Beispieldatei [file] in [language], mit vorangestellter
    // Sprache, in jedem Design – samt dem XML der E-Rechnung.
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

    // Jedes Beispiel auf Englisch (#377, #406), in beiden Varianten (#407):
    // Es entsteht ein PDF, samt dem XML der E-Rechnung. Die Freitexte der
    // Dateien bleiben deutsch; geprüft ist, dass jeder Satz aus Kotlin einen
    // englischen Wortlaut hat.
    for (language in listOf(Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH)) {
        for ((_, file) in exampleInvoices) {
            test("$file auf ${language.tag}: gültiges PDF/A-1b mit allen Pflichtangaben") {
                typesetIn(language, file)
            }
        }

        // Ebenso jede Kategorie, die einen Grund mitbringt (#406, #427), hier
        // mit dem Grund wörtlich wie im PflichtangabenArtenTest. Kein
        // deutsches Wort des Programms steht auf dem Blatt.
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
