package de.fluchitconsulting.rechnungsgenerator.prepayments

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Root
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.runCli
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val RESOURCES = "src/test/resources"

class PrepaymentsCommandTest : FunSpec({

    val sellerConfig = "$RESOURCES/stammdaten.yaml"
    val june = "$RESOURCES/abschlagsrechnung.yaml"
    val august = "$RESOURCES/abschlagsrechnung-2.yaml"

    test("eingetragen und angehängt ergibt der Block die Abschläge der Beispiel-Schlussrechnung") {
        val result = runCli("abschlaege", june, august, "-s", sellerConfig)

        result.exitCode shouldBe 0
        result.err shouldBe ""
        // Nie der Pfad: Der wechselt, sobald die Datei in einen Jahresordner wandert.
        result.out shouldContain "  # abschlagsrechnung.yaml"
        result.out shouldNotContain RESOURCES

        // R26-011 wurde erst nach seiner Rechnung vereinnahmt, das Feld bleibt leer.
        val block =
            result.out.replace(
                "vereinnahmt-am:                 # Datum der Vereinnahmung eintragen",
                "vereinnahmt-am: 2026-09-02",
            )
        val handwritten = Path.of("$RESOURCES/schlussrechnung.yaml").readText()
        val generated = YamlLoader.parseInvoice(handwritten.substringBefore("abschlaege:") + block)

        generated.prepayments shouldBe YamlLoader.parseInvoice(handwritten).prepayments
        CalculatedInvoice(generated, YamlLoader.parseSellerConfig(Path.of(sellerConfig).readText()))
    }

    test("in Fremdwährung erinnert ein Hinweis auf stderr an die Währung der Schlussrechnung (#409)") {
        // Ohne Umsatzsteuer, sonst bräche WAEHRUNG_MIT_UMSATZSTEUER_AUF_DIESER_ART_NICHT_MOEGLICH ab.
        val inDollar =
            createTempDirectory("abschlaege-dollar").resolve("abschlagsrechnung.yaml").also {
                it.writeText(
                    "waehrung: USD\n" + Path.of(june).readText() +
                        "    ust-satz: 0\n    steuerkategorie: O\n    steuerbefreiungsgrund: Nicht im Inland steuerbar\n",
                )
            }

        val result = runCli("abschlaege", inDollar.toString(), "-s", sellerConfig)

        result.exitCode shouldBe 0
        result.out shouldContain "R26-009"
        result.out shouldNotContain "USD"
        result.err shouldContain "waehrung: USD"
    }

    test("zweimal derselbe Aufruf ergibt zeichengleich dieselbe Ausgabe") {
        runCli("abschlaege", june, august, "-s", sellerConfig).out shouldBe
            runCli("abschlaege", june, august, "-s", sellerConfig).out
    }

    test("ist eine Datei keine Abschlagsrechnung, steht nichts auf stdout") {
        // Alles oder nichts: Den fehlenden Abschlag bemerkte sonst keine Regel.
        val result = runCli("abschlaege", june, "$RESOURCES/rechnung.yaml", "-s", sellerConfig)

        result.exitCode shouldBe 1
        result.out shouldBe ""
        result.err shouldContain "keine Abschlagsrechnung"
    }

    test("ohne Datei ist es ein Bedienfehler") {
        val result = runCli("abschlaege", "-s", sellerConfig)

        result.exitCode shouldBe 1
        result.out shouldBe ""
    }

    test("ein YAML-Fehler nennt die Datei, in der er steckt") {
        val broken =
            createTempDirectory("abschlaege-test")
                .resolve("kaputt.yaml")
                .also { it.writeText("nummer: R26-099\ngibtsnicht: 1\n") }

        val result = runCli("abschlaege", june, broken.toString(), "-s", sellerConfig)

        result.exitCode shouldBe 1
        result.out shouldBe ""
        result.err shouldContain broken.toString()
        result.err shouldContain "gibtsnicht"
    }

    context("ohne -s kommen die Stammdaten aus der Wurzel (ADR-0015)") {
        fun archive(): Path =
            createTempDirectory("wurzel-abschlaege").also {
                it
                    .resolve(Root.FOLDER)
                    .createDirectories()
                    .resolve(Root.SELLER_CONFIG)
                    .writeText(Path.of(sellerConfig).readText())
            }

        fun copyInto(archive: Path, name: String, template: String): Path =
            archive.resolve(name).also {
                it.parent.createDirectories()
                it.writeText(Path.of(template).readText())
            }

        test("gesucht wird je Datei, und aus einer Wurzel kommt derselbe Block") {
            val archive = archive()
            val first = copyInto(archive, "2026/abschlagsrechnung.yaml", june)
            val second = copyInto(archive, "abschlagsrechnung-2.yaml", august)

            val result = runCli("abschlaege", first.toString(), second.toString())

            result.exitCode shouldBe 0
            result.out shouldBe runCli("abschlaege", june, august, "-s", sellerConfig).out
        }

        test("verschiedene Wurzeln brechen ab und nennen beide") {
            val one = archive()
            val other = archive()
            val here = copyInto(one, "abschlagsrechnung.yaml", june)
            val there = copyInto(other, "abschlagsrechnung-2.yaml", august)

            val result = runCli("abschlaege", here.toString(), there.toString())

            result.exitCode shouldBe 1
            result.out shouldBe ""
            result.err shouldContain one.toString()
            result.err shouldContain other.toString()
        }
    }
})
