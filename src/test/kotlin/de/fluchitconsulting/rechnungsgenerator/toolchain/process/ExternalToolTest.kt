package de.fluchitconsulting.rechnungsgenerator.toolchain.process

import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlin.io.path.createTempDirectory
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/** Echte Prozesse mit Programmen, die auf jedem Rechner liegen: Ein gefälschter bewiese hier nichts. */
class ExternalToolTest : FunSpec({

    val workDir = createTempDirectory("externaltool-test")

    test("ein Programm, das es nicht gibt, wird beim Namen genannt") {
        val error =
            shouldThrow<ToolchainException> {
                ExternalTool.run("gibt-es-nicht-lualatex", emptyList(), workDir)
            }

        error.message!! shouldContain "Programm nicht gefunden: gibt-es-nicht-lualatex"
    }

    // #300
    test("scheitert der Lauf an einer IOException, wird sie zur ToolchainException mit dem Programm") {
        val missing = workDir.resolve("gibt-es-nicht")

        val error = shouldThrow<ToolchainException> { ExternalTool.run("true", emptyList(), missing) }

        error.message!! shouldContain "true"
        error.message!! shouldContain missing.toString()
    }

    test("ein hängender Prozess wird nach der Zeitgrenze abgeräumt") {
        lateinit var error: ToolchainException

        val needed =
            measureTime {
                error =
                    shouldThrow<ToolchainException> {
                        ExternalTool.run("sleep", listOf("10"), workDir, timeout = 1.seconds)
                    }
            }

        error.message!! shouldContain "sleep hat nach"
        error.message!! shouldContain "nicht geantwortet und wurde abgebrochen"
        // Ohne diese Zusicherung bliebe der Test grün, wenn die Zeitgrenze erst nach dem Prozessende griffe.
        withClue("Der Aufruf kehrte erst nach $needed zurück") {
            needed shouldBeLessThan 5.seconds
        }
    }

    test("die Umgebung kommt beim Programm an") {
        val result =
            ExternalTool.run(
                "printenv",
                listOf("RECHNUNGSGENERATOR_TEST"),
                workDir,
                environment = mapOf("RECHNUNGSGENERATOR_TEST" to "angekommen"),
            )

        result.output shouldContain "angekommen"
    }

    // #227
    test("stdout und stderr kommen getrennt zurück") {
        val result =
            ExternalTool.run(
                "sh",
                listOf("-c", "echo nach-stdout; echo nach-stderr >&2"),
                workDir,
            )

        result.standardOutput.trim() shouldBe "nach-stdout"
        result.errorOutput.trim() shouldBe "nach-stderr"
        result.output shouldContain "nach-stdout"
        result.output shouldContain "nach-stderr"
    }

    test("ein fehlendes Programm steht mit seinem Installationshinweis in der Meldung") {
        val error =
            shouldThrow<ToolchainException> {
                ExternalTool.requireAll("gibt-es-nicht-verapdf" to "brew install gibt-es-nicht")
            }

        error.message!! shouldContain "Diese Programme fehlen im PATH:"
        error.message!! shouldContain "gibt-es-nicht-verapdf"
        error.message!! shouldContain "brew install gibt-es-nicht"
    }

    // Mit einem vorhandenen, sonst bliebe der Test grün, wenn `requireAll` gar nicht nachsähe.
    test("mehrere fehlende Programme stehen alle in der Meldung, ein vorhandenes nicht") {
        val error =
            shouldThrow<ToolchainException> {
                ExternalTool.requireAll(
                    "gibt-es-nicht-lualatex" to "brew install lualatex-attrappe",
                    "sh" to "liegt auf jedem Rechner",
                    "gibt-es-nicht-verapdf" to "brew install verapdf-attrappe",
                )
            }

        error.message!! shouldContain "gibt-es-nicht-lualatex"
        error.message!! shouldContain "gibt-es-nicht-verapdf"
        error.message!! shouldNotContain "liegt auf jedem Rechner"
    }

    test("sind alle Programme da, geschieht nichts") {
        shouldNotThrowAny { ExternalTool.requireAll("sh" to "liegt auf jedem Rechner") }
    }
})
