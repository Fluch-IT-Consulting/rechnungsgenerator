package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Das Pflichtangaben-Netz ([PflichtangabenRun]) an den Rechnungen über mehrere
 * Seiten (ADR-0020): viele Positionen, und was an der Seitengrenze
 * beisammenbleiben muss.
 */
class PflichtangabenSeitenTest : FunSpec({
    tags(Toolchain)

    val net = PflichtangabenRun()
    val designs = net.designs

    // Mehr Positionen, als auf eine Seite passen (#318, ADR-0020): Jede steht
    // auf dem Blatt, und das Blatt hat so viele Seiten, wie das Design für
    // diese Datei nennt. Was die Folgeseiten tragen, prüft `verify` an jeder
    // mehrseitigen Rechnung; hier brechen die Positionen über zwei Seiten.
    for (design in designs) {
        test("viele-positionen im Design ${design.name}: gültig, jede Position auf dem Blatt, über mehrere Seiten") {
            val (calculated, _) =
                net.typesetAndCheck("viele-positionen", design, design.sellerConfig, targetName = "${design.name}-viele")

            calculated.invoice.items.size shouldBe 40
        }
    }

    // Der Schluss an der Seitengrenze (ADR-0020): Die Positionen füllen die
    // zweite Seite so weit, dass sie ohne Zusammenhalt in helsinki zwischen
    // Schlusssatz und Grußformel bräche und in prag zwischen Zahlungsfrist
    // und Schlusssatz. Was dann beisammenstehen muss, prüft
    // `verify` an jeder mehrseitigen Rechnung; diese Datei ist die, bei der
    // es darauf ankommt.
    for (design in designs) {
        test("Schluss an der Seitengrenze im Design ${design.name}: der Schluss bricht nicht auseinander") {
            net.typesetAndCheck("schluss-an-der-seitengrenze", design, design.sellerConfig, targetName = "${design.name}-grenze")
        }
    }

    // Ein langer Befreiungsgrund an der Seitengrenze (ADR-0020, #335): Ohne
    // Zusammenhalt endete die zweite Seite nach der ersten Zeile des Hinweises.
    // Dass er ganz auf einer Seite steht, prüft `verify` an jeder mehrseitigen
    // Rechnung; diese Datei ist die, bei der es darauf ankommt.
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
