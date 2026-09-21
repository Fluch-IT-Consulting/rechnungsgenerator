package de.fluchitconsulting.rechnungsgenerator.prepayments

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.model.Root
import de.fluchitconsulting.rechnungsgenerator.model.YamlLoader
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

/**
 * Prüft `rechnungsgenerator abschlaege` von den Argumenten bis zur Ausgabe.
 *
 * Wie aus einer Abschlagsrechnung ein Abschlag wird und wie er hingeschrieben
 * wird, prüfen PrepaymentsTest und PrepaymentBlockTest. Hier geht es darum,
 * dass das Kommando beides mit echten Dateien zusammenbringt – und dass bei
 * einem Fehler nichts auf stdout landet.
 */
class PrepaymentsCommandTest : FunSpec({

    val stammdaten = "$RESOURCES/stammdaten.yaml"
    val june = "$RESOURCES/abschlagsrechnung.yaml"
    val august = "$RESOURCES/abschlagsrechnung-2.yaml"

    test("eingetragen und angehängt ergibt der Block die Abschläge der Beispiel-Schlussrechnung") {
        val result = runCli("abschlaege", june, august, "-s", stammdaten)

        result.exitCode shouldBe 0
        result.err shouldBe ""
        // Je Abschlag der Dateiname, aber nie der Pfad: Der wechselt, sobald die
        // Datei in einen Jahresordner wandert.
        result.out shouldContain "  # abschlagsrechnung.yaml"
        result.out shouldNotContain RESOURCES

        // R26-011 wurde erst nach seiner Rechnung vereinnahmt, also bleibt das
        // Feld leer – ein Mensch trägt es ein, so wie es in der
        // Beispiel-Schlussrechnung steht.
        val block =
            result.out.replace(
                "vereinnahmt-am:                 # Datum der Vereinnahmung eintragen",
                "vereinnahmt-am: 2026-09-02",
            )
        val handwritten = Path.of("$RESOURCES/schlussrechnung.yaml").readText()
        val generated = YamlLoader.parseInvoice(handwritten.substringBefore("abschlaege:") + block)

        generated.prepayments shouldBe YamlLoader.parseInvoice(handwritten).prepayments
        // Und die Schlussrechnung rechnet damit durch, alle neun Regeln inklusive.
        CalculatedInvoice(generated, YamlLoader.parseSellerConfig(Path.of(stammdaten).readText()))
    }

    test("zweimal derselbe Aufruf ergibt zeichengleich dieselbe Ausgabe") {
        runCli("abschlaege", june, august, "-s", stammdaten).out shouldBe
            runCli("abschlaege", june, august, "-s", stammdaten).out
    }

    test("ist eine Datei keine Abschlagsrechnung, steht nichts auf stdout") {
        // Alles oder nichts: Der gültige Abschlag aus june käme sonst allein in
        // die Ausgabe, und der fehlende fiele keiner Regel der Schlussrechnung
        // auf – nur dem Kunden, der ihn doppelt zahlt.
        val result = runCli("abschlaege", june, "$RESOURCES/rechnung.yaml", "-s", stammdaten)

        result.exitCode shouldBe 1
        result.out shouldBe ""
        result.err shouldContain "keine Abschlagsrechnung"
    }

    test("ohne Datei ist es ein Bedienfehler") {
        val result = runCli("abschlaege", "-s", stammdaten)

        result.exitCode shouldBe 1
        result.out shouldBe ""
    }

    test("ein YAML-Fehler nennt die Datei, in der er steckt") {
        // kaml nennt nur den Schlüssel. Bei mehreren Dateien bliebe offen, in
        // welcher er steht.
        val broken =
            createTempDirectory("abschlaege-test")
                .resolve("kaputt.yaml")
                .also { it.writeText("nummer: R26-099\ngibtsnicht: 1\n") }

        val result = runCli("abschlaege", june, broken.toString(), "-s", stammdaten)

        result.exitCode shouldBe 1
        result.out shouldBe ""
        result.err shouldContain broken.toString()
        result.err shouldContain "gibtsnicht"
    }

    // Dass eine unlesbare Datei nicht im Stacktrace endet, stand hier bis #119
    // noch einmal eigens für dieses Kommando. Das hält jetzt execute() für alle,
    // geprüft in MainTest.

    context("ohne -s kommen die Stammdaten aus der Wurzel (ADR-0015)") {
        /** Eine Ablage mit den Beispiel-Stammdaten in ihrem Ordner. */
        fun ablage(): Path =
            createTempDirectory("wurzel-abschlaege").also {
                it
                    .resolve(Root.FOLDER)
                    .createDirectories()
                    .resolve(Root.SELLER_CONFIG)
                    .writeText(Path.of(stammdaten).readText())
            }

        /** Legt [vorlage] unter [name] in die Ablage; die Zwischenordner entstehen mit. */
        fun kopie(ablage: Path, name: String, vorlage: String): Path =
            ablage.resolve(name).also {
                it.parent.createDirectories()
                it.writeText(Path.of(vorlage).readText())
            }

        test("gesucht wird je Datei, und aus einer Wurzel kommt derselbe Block") {
            val ablage = ablage()
            val erste = kopie(ablage, "2026/abschlagsrechnung.yaml", june)
            val zweite = kopie(ablage, "abschlagsrechnung-2.yaml", august)

            val result = runCli("abschlaege", erste.toString(), zweite.toString())

            result.exitCode shouldBe 0
            result.out shouldBe runCli("abschlaege", june, august, "-s", stammdaten).out
        }

        test("verschiedene Wurzeln brechen ab und nennen beide") {
            // Abschläge zweier Rechnungssteller in einem Block sind fachlich
            // sinnlos; "die erste gewinnt" verschleierte das nur.
            val eine = ablage()
            val andere = ablage()
            val hier = kopie(eine, "abschlagsrechnung.yaml", june)
            val dort = kopie(andere, "abschlagsrechnung-2.yaml", august)

            val result = runCli("abschlaege", hier.toString(), dort.toString())

            result.exitCode shouldBe 1
            result.out shouldBe ""
            result.err shouldContain eine.toString()
            result.err shouldContain andere.toString()
        }
    }
})
