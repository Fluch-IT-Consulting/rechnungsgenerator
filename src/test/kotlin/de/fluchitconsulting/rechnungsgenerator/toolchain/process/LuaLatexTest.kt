package de.fluchitconsulting.rechnungsgenerator.toolchain.process

import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectory
import kotlin.io.path.createTempDirectory

class LuaLatexTest : FunSpec({

    fun workDir() = createTempDirectory("lualatex-test")

    val creationDate = Instant.parse("2026-08-14T00:00:00Z")

    fun run(
        runner: FakeProcessRunner,
        dir: Path = workDir(),
    ) = LuaLatex(runner).run(dir, "rechnung", "0A1B", creationDate)

    test("ein Lauf setzt die Quelle unter dem Jobnamen, mit der Trailer-ID davor") {
        val runner = FakeProcessRunner(FakeProcessRunner.Run())

        run(runner)

        runner.commands.single() shouldBe
            listOf(
                "lualatex",
                "-interaction=nonstopmode",
                "-halt-on-error",
                "-jobname=rechnung",
                "\\pdfvariable trailerid {[ <0A1B> <0A1B> ]}\\input{rechnung.tex}",
            )
    }

    test("das Erstellungsdatum geht als SOURCE_DATE_EPOCH mit, in UTC geschrieben") {
        // Ohne TZ schriebe LuaTeX die Zeitzone des Rechners ins PDF.
        val runner = FakeProcessRunner(FakeProcessRunner.Run())

        run(runner)

        runner.environments.single() shouldBe mapOf("SOURCE_DATE_EPOCH" to "1786665600", "TZ" to "UTC")
    }

    test("das Protokoll kommt von der Platte, nicht aus der Prozessausgabe") {
        val runner = FakeProcessRunner(FakeProcessRunner.Run(output = "Konsole", files = mapOf("rechnung.log" to "Protokoll")))

        run(runner) shouldBe LuaLatexRun(failed = false, log = "Protokoll")
    }

    test("ein Exitcode ungleich 0 heißt gescheitert, ohne Ausnahme") {
        val runner =
            FakeProcessRunner(FakeProcessRunner.Run(exitCode = 1, files = mapOf("rechnung.log" to "! Undefined control sequence.")))

        run(runner).failed shouldBe true
    }

    test("stürzt LuaLaTeX ab, bevor es ein Protokoll schreibt, zählt die Prozessausgabe") {
        val runner = FakeProcessRunner(FakeProcessRunner.Run(exitCode = 1, output = "! I can't find file 'rechnung'."))

        run(runner) shouldBe LuaLatexRun(failed = true, log = "! I can't find file 'rechnung'.")
    }

    // Ein Verzeichnis statt Dateirechten, denn die greifen unter root nicht (#336).
    test("lässt sich das Protokoll nicht lesen, kommt eine ToolchainException mit der Datei") {
        val dir = workDir()
        val logFile = dir.resolve("rechnung.log").createDirectory()

        val error = shouldThrow<ToolchainException> { run(FakeProcessRunner(FakeProcessRunner.Run()), dir) }

        error.message!! shouldContain logFile.toString()
    }

    // Keiner der beiden Zweige fragt die Maschine, auf der der Test läuft (#475).
    test("fehlt lualatex, nennt die Meldung das Programm und seinen Installationshinweis") {
        val error = shouldThrow<ToolchainException> { LuaLatex(FakeProcessRunner(missing = setOf("lualatex"))).checkAvailable() }

        error.message!! shouldContain "lualatex"
        error.message!! shouldContain "https://tug.org/texlive/"
    }

    test("ist lualatex da, meldet checkAvailable nichts") {
        shouldNotThrowAny { LuaLatex(FakeProcessRunner()).checkAvailable() }
    }
})
