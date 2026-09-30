package de.fluchitconsulting.rechnungsgenerator.generate.typesetting

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

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
})
