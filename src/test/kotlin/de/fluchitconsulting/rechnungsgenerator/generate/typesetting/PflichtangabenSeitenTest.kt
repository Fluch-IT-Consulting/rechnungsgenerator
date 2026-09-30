package de.fluchitconsulting.rechnungsgenerator.generate.typesetting

import de.fluchitconsulting.rechnungsgenerator.core.values.Currency
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** ADR-0020. Was beisammenbleiben muss, prüft `verify`; diese Dateien sind die, bei denen es darauf ankommt. */
class PflichtangabenSeitenTest : FunSpec({
    tags(Toolchain)

    val net = PflichtangabenRun()
    val designs = net.designs

    // #318
    for (design in designs) {
        test("viele-positionen im Design ${design.name}: gültig, jede Position auf dem Blatt, über mehrere Seiten") {
            val (calculated, _) =
                net.typesetAndCheck("viele-positionen", design, design.sellerConfig, targetName = "${design.name}-viele")

            calculated.invoice.items.size shouldBe 40
        }
    }

    // Ohne Zusammenhalt bräche helsinki vor der Grußformel, prag vor dem Schlusssatz.
    for (design in designs) {
        test("Schluss an der Seitengrenze im Design ${design.name}: der Schluss bricht nicht auseinander") {
            net.typesetAndCheck("schluss-an-der-seitengrenze", design, design.sellerConfig, targetName = "${design.name}-grenze")
        }
    }

    // #335
    for (design in designs) {
        test("Befreiungsgrund an der Seitengrenze im Design ${design.name}: der Hinweis bricht nicht auseinander") {
            val (calculated, _) =
                net.typesetAndCheck(
                    "befreiungsgrund-an-der-seitengrenze",
                    design,
                    design.sellerConfig,
                    targetName = "${design.name}-befreiung",
                )

            calculated.vatGroups.count { it.exemptionReason != null } shouldBe 1
        }
    }

    // #479. Hier und nicht bei den Sonderfällen: Die teilen sich einen Fork mit den
    // Arten, dem längsten (Messung an maxParallelForks). Złoty wörtlich, den Rest
    // vergleicht Spelling.
    val zlotySpellings =
        mapOf(
            Language.GERMAN to "1.000,00 PLN −200,00 PLN",
            Language.BRITISH_ENGLISH to "PLN 1,000.00 −PLN 200.00",
            Language.AMERICAN_ENGLISH to "PLN 1,000.00 −PLN 200.00",
        )
    for ((language, zloty) in zlotySpellings) {
        for (design in designs) {
            test("Zeichenprobe auf ${language.tag} im Design ${design.name}: jede Währung mit Zeichen an ihrer Stelle") {
                val lines = net.typesetCurrencySample(design, language)

                lines[Currency.entries.indexOf(Currency.PLN)] shouldBe zloty
            }
        }
    }

    // #480, aus demselben Grund hier. Vorher lief in prag jede der drei Betragsspalten über.
    for (language in listOf(Language.GERMAN, Language.BRITISH_ENGLISH)) {
        for (design in designs) {
            test("Beträge mit Code auf ${language.tag} im Design ${design.name}: jede Spalte fasst sie") {
                val input = net.workDir.resolve("betraege-mit-code-${language.tag}.yaml")
                input.writeText("sprache: ${language.tag}\n" + Path.of("src/test/resources/betraege-mit-code.yaml").readText())

                val (calculated, _) =
                    net.typesetAndCheck(
                        "betraege-mit-code",
                        design,
                        design.sellerConfig,
                        targetName = "${design.name}-betraege-mit-code-${language.tag}",
                        input = input,
                    )

                calculated.invoice.currency shouldBe Currency.PLN
            }
        }
    }
})
