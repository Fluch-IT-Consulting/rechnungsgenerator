package de.fluchitconsulting.rechnungsgenerator

import de.fluchitconsulting.rechnungsgenerator.core.model.Root
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * #61; der Build verlangt damit `python3` und PyYAML. Die Ausgabe geht in
 * Dateien, sonst hinge der Test mit einem hängenden Prozess und die Zeitgrenze griffe nie.
 */
class NaechsteNummerSkriptTest : FunSpec({

    val script = File(".claude/skills/rechnung/scripts/naechste-nummer.py").absolutePath

    // Mit dem Muster R\d{2}-\d{3}.
    val sellerConfig = File("src/test/resources/stammdaten.yaml").readText()

    data class ScriptRun(val exitCode: Int, val stdout: String, val stderr: String)

    fun archive(vararg fileNames: String): Path {
        val dir = createTempDirectory("nummern-test")
        val outgoing = dir.resolve("Outgoing").also { it.createDirectories() }
        fileNames.forEach { outgoing.resolve(it).writeText("") }
        return dir
    }

    fun runScript(dir: Path, vararg args: String): ScriptRun {
        val stdout = dir.resolve("stdout.txt")
        val stderr = dir.resolve("stderr.txt")
        val process =
            ProcessBuilder(listOf("python3", script, dir.resolve("Outgoing").toString()) + args)
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start()
        check(process.waitFor(30, TimeUnit.SECONDS)) { "Das Skript hat nach 30 s nicht geantwortet." }
        return ScriptRun(process.exitValue(), stdout.readText().trim(), stderr.readText().trim())
    }

    test("die nächste Nummer zählt die belegte weiter") {
        val dir = archive("Rechnung_R26-011-beispiel.pdf", "Rechnung_R26-009-beispiel.yaml")

        runScript(dir, "--rechnungsdatum", "2026-08-14").stdout shouldBe "R26-012"
    }

    test("eine anders benannte YAML-Datei zählt über ihre Nummer") {
        // #75. Eine YAML-Datei ohne Nummer, etwa vereinnahmungen.yaml, zählt nicht.
        val dir = archive("Rechnung_R26-011-beispiel.pdf")
        val outgoing = dir.resolve("Outgoing")
        outgoing.resolve("Abschlagsrechnung-R26-014.yaml").writeText("nummer: R26-014\nart: abschlagsrechnung\n")
        outgoing.resolve("vereinnahmungen.yaml").writeText("- R26-099\n")

        runScript(dir, "--rechnungsdatum", "2026-08-14").stdout shouldBe "R26-015"
    }

    test("eine unlesbare YAML-Datei wird genannt, nicht still übergangen") {
        val dir = archive("Rechnung_R26-011-beispiel.pdf")
        dir.resolve("Outgoing").resolve("kaputt.yaml").writeText("nummer: [R26-020\n")

        val result = runScript(dir, "--rechnungsdatum", "2026-08-14")

        result.exitCode shouldBe 0
        result.stdout shouldBe "R26-012"
        result.stderr shouldContain "kaputt.yaml"
    }

    test("ein Jahr ohne Rechnung fängt bei 001 an") {
        val dir = archive("Rechnung_R25-137-beispiel.pdf")

        runScript(dir, "--rechnungsdatum", "2026-01-02").stdout shouldBe "R26-001"
    }

    test("das Jahr kommt aus dem Rechnungsdatum, nicht aus dem Kalender") {
        val dir = archive("Rechnung_R25-003-beispiel.pdf", "Rechnung_R26-001-beispiel.pdf")

        runScript(dir, "--rechnungsdatum", "2025-12-31").stdout shouldBe "R25-004"
    }

    test("ohne Rechnungsdatum gilt heute") {
        val year = "%02d".format(LocalDate.now().year % 100)

        runScript(archive()).stdout shouldBe "R$year-001"
    }

    test("mit Stammdaten wird die Nummer gegen ihr Muster geprüft") {
        val dir = archive("Rechnung_R26-011-beispiel.pdf")
        dir.resolve("stammdaten.yaml").writeText(sellerConfig)

        val result =
            runScript(dir, "--rechnungsdatum", "2026-08-14", "--stammdaten", dir.resolve("stammdaten.yaml").toString())

        result.exitCode shouldBe 0
        result.stdout shouldBe "R26-012"
    }

    test("ein gewechseltes Muster hält die Vergabe an") {
        val dir = archive("Rechnung_R26-011-beispiel.pdf")
        dir
            .resolve("stammdaten.yaml")
            .writeText(sellerConfig.replace("""rechnungsnummer-muster: 'R\d{2}-\d{3}'""", """rechnungsnummer-muster: 'RE-\d{4}'"""))

        val result = runScript(dir, "--stammdaten", dir.resolve("stammdaten.yaml").toString())

        result.exitCode shouldBe 1
        result.stdout shouldBe ""
        result.stderr shouldContain "RE-\\d{4}"
        result.stderr shouldContain "muss es hier mitwechseln"
    }

    test("Stammdaten ohne Muster prüfen nicht") {
        val dir = archive("Rechnung_R26-011-beispiel.pdf")
        dir
            .resolve("stammdaten.yaml")
            .writeText(sellerConfig.lines().filterNot { it.startsWith("rechnungsnummer-muster") }.joinToString("\n"))

        val result =
            runScript(dir, "--rechnungsdatum", "2026-08-14", "--stammdaten", dir.resolve("stammdaten.yaml").toString())

        result.exitCode shouldBe 0
        result.stdout shouldBe "R26-012"
    }

    test("ein Datum, das keines ist, wird abgelehnt") {
        val result = runScript(archive(), "--rechnungsdatum", "31.12.2025")

        result.exitCode shouldBe 2
        result.stderr shouldContain "--rechnungsdatum"
    }

    test("die Stammdaten im Ordner der Wurzel sind keine vergebene Nummer") {
        // rglob steigt auch in den versteckten Ordner der Stammdaten (ADR-0015).
        val dir = archive("Rechnung_R26-009-beispiel.yaml")
        dir
            .resolve("Outgoing")
            .resolve(Root.FOLDER)
            .createDirectories()
            .resolve(Root.SELLER_CONFIG)
            .writeText(sellerConfig)

        val result = runScript(dir, "--rechnungsdatum", "2026-08-14")

        result.exitCode shouldBe 0
        result.stdout shouldBe "R26-010"
        result.stderr shouldBe ""
    }
})
