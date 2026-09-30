package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import de.fluchitconsulting.rechnungsgenerator.toolchain.FakeProcessRunner
import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.time.LocalDate
import kotlin.io.path.createDirectory
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText

/**
 * Die Protokolle sind echte Mitschnitte; erfundene verfehlten gerade die
 * Eigenheiten, um die es geht. Ein neuer Fall bekommt einen neuen Mitschnitt.
 */
class LatexCompilerTest : FunSpec({

    fun transcript(name: String) = File("src/test/resources/werkzeugausgaben/$name").readText()

    val finished = "Output written on rechnung.pdf (1 page, 12345 bytes)."

    fun workDir() = createTempDirectory("latexcompiler-test")

    fun successfulRun(log: String = finished) =
        FakeProcessRunner.Run(files = mapOf("rechnung.log" to log, "rechnung.pdf" to "%PDF-1.7"))

    fun typeset(
        runner: FakeProcessRunner,
        dir: java.nio.file.Path = workDir(),
        tex: String = "\\documentclass{article}",
        files: Map<String, ByteArray> = emptyMap(),
    ) = LatexCompiler(runner).compile(
        dir,
        "rechnung",
        PdfSources(tex = tex, xmpMetadata = "\\Title{Test}", creationDate = LocalDate.of(2026, 8, 14), files = files),
    )

    fun trailerIdOf(
        tex: String = "\\documentclass{article}",
        files: Map<String, ByteArray> = emptyMap(),
    ): String =
        FakeProcessRunner(successfulRun())
            .also { typeset(it, tex = tex, files = files) }
            .commands
            .single()
            .last()
            .substringAfter("<")
            .substringBefore(">")

    test("das Erstellungsdatum ist Mitternacht des Datums, in UTC geschrieben") {
        // Ohne TZ schriebe LuaTeX die Zeitzone des Rechners ins PDF.
        val runner = FakeProcessRunner(successfulRun())

        typeset(runner)

        runner.environments.single() shouldBe mapOf("SOURCE_DATE_EPOCH" to "1786665600", "TZ" to "UTC")
    }

    test("dieselben Quellen ergeben dieselbe Trailer-ID, andere eine andere") {
        fun invocation(tex: String) =
            FakeProcessRunner(successfulRun())
                .also { typeset(it, tex = tex) }
                .commands
                .single()
                .last()

        val first = invocation("\\documentclass{article}")

        first shouldContain "\\pdfvariable trailerid"
        invocation("\\documentclass{article}") shouldBe first
        invocation("\\documentclass{letter}") shouldNotBe first
    }

    context("weitere Dateien (#198)") {
        val logo = "\\fill (0,0) circle (1);".toByteArray()

        test("liegen unter ihrem Namen im Arbeitsverzeichnis, Byte für Byte") {
            val dir = workDir()

            typeset(FakeProcessRunner(successfulRun()), dir, files = mapOf("logo.tex" to logo))

            dir.resolve("logo.tex").readBytes() shouldBe logo
        }

        test("gehen in die Trailer-ID ein: ein anderes Logo, eine andere ID") {
            val withLogo = trailerIdOf(files = mapOf("logo.tex" to logo))

            trailerIdOf(files = mapOf("logo.tex" to logo.copyOf())) shouldBe withLogo
            trailerIdOf(files = mapOf("logo.tex" to "\\fill (0,0) circle (2);".toByteArray())) shouldNotBe withLogo
            trailerIdOf() shouldNotBe withLogo
        }

        test("ohne weitere Dateien bleibt die ID, wie sie vor #198 war") {
            // Sonst ergäbe jede alte Rechnung nachgebaut ein anderes PDF.
            val md5 = java.security.MessageDigest.getInstance("MD5")
            md5.update("\\documentclass{article}".toByteArray())
            md5.update(0.toByte())
            md5.update("\\Title{Test}".toByteArray())
            val before =
                java.util.HexFormat
                    .of()
                    .withUpperCase()
                    .formatHex(md5.digest())

            trailerIdOf() shouldBe before
        }

        test("ein Name mit Verzeichnis oder der Name der Quelle wird abgelehnt") {
            shouldThrow<IllegalArgumentException> {
                typeset(FakeProcessRunner(successfulRun()), files = mapOf("../logo.tex" to logo))
            }
            shouldThrow<IllegalArgumentException> {
                typeset(FakeProcessRunner(successfulRun()), files = mapOf("rechnung.tex" to logo))
            }
        }

        test("die Namen der Verzeichnisse selbst und ein leerer Name werden abgelehnt") {
            // Path.of("..").fileName ist wieder "..", die Prüfung auf einen
            // schlichten Namen allein ließe es durch.
            listOf(".", "..", "", " ").forEach { name ->
                withClue("'$name'") {
                    shouldThrow<IllegalArgumentException> {
                        typeset(FakeProcessRunner(successfulRun()), files = mapOf(name to logo))
                    }
                }
            }
        }
    }

    test("die Quelldateien liegen neben dem Ergebnis") {
        val dir = workDir()

        typeset(FakeProcessRunner(successfulRun()), dir)

        // pdfx findet die .xmpdata nur unter dem Namen der .tex.
        dir.resolve("rechnung.tex").readText() shouldContain "documentclass"
        dir.resolve("rechnung.xmpdata").readText() shouldContain "Title"
    }

    // #300
    test("lassen sich die Quellen nicht schreiben, kommt eine ToolchainException mit dem Verzeichnis") {
        val missing = workDir().resolve("gibt-es-nicht")

        val error = shouldThrow<ToolchainException> { typeset(FakeProcessRunner(successfulRun()), missing) }

        error.message!! shouldContain missing.toString()
    }

    // Ein Verzeichnis statt Dateirechten, denn die greifen unter root nicht (#336).
    test("lässt sich das Protokoll nicht lesen, kommt eine ToolchainException mit der Datei") {
        val dir = workDir()
        val logFile = dir.resolve("rechnung.log").createDirectory()

        val error = shouldThrow<ToolchainException> { typeset(FakeProcessRunner(FakeProcessRunner.Run()), dir) }

        error.message!! shouldContain logFile.toString()
    }

    test("ein Lauf genügt, wenn das Protokoll keinen zweiten verlangt") {
        val runner = FakeProcessRunner(successfulRun())

        val pdf = typeset(runner)

        pdf.exists() shouldBe true
        runner.commands shouldHaveSize 1
        runner.commands.single().first() shouldBe "lualatex"
    }

    test("verlangt das Protokoll einen zweiten Lauf, wird ein zweites Mal gesetzt") {
        val runner =
            FakeProcessRunner(
                successfulRun(transcript("lualatex-verlangt-zweiten-lauf.log")),
                successfulRun(),
            )

        typeset(runner)

        runner.commands shouldHaveSize 2
        runner.commands.distinct() shouldHaveSize 1
        runner.environments.distinct() shouldHaveSize 1
    }

    test("verlangt longtable einen zweiten Lauf, wird ein zweites Mal gesetzt") {
        // longtable sagt es ohne "Rerun to get" (#321).
        val runner =
            FakeProcessRunner(
                successfulRun(transcript("lualatex-tabellenbreite-geaendert.log")),
                successfulRun(),
            )

        typeset(runner)

        runner.commands shouldHaveSize 2
    }

    test("wer immer weiter verlangt, bekommt trotzdem nur drei Läufe") {
        val persistent = transcript("lualatex-verlangt-zweiten-lauf.log")
        val runner =
            FakeProcessRunner(
                successfulRun(persistent),
                successfulRun(persistent),
                successfulRun(persistent),
            )

        typeset(runner)

        runner.commands shouldHaveSize 3
    }

    test("ein gescheiterter Lauf nennt die Fundstelle statt tausend Zeilen Paketgeplauder") {
        val runner =
            FakeProcessRunner(
                FakeProcessRunner.Run(exitCode = 1, files = mapOf("rechnung.log" to transcript("lualatex-abbruch.log"))),
            )

        val error = shouldThrow<ToolchainException> { typeset(runner) }

        error.message!! shouldContain "Undefined control sequence"
        error.message!! shouldContain "gibtesnicht"
        error.message!! shouldContain "l.3"
        error.message!! shouldNotContain "geometry"
        // Von 98 Zeilen; es geht um die Größenordnung, nicht die Zahl.
        error.message!!.lines().size shouldBeLessThan 15
    }

    test("bei zwei dicht beieinanderliegenden Fehlern steht keine Zeile doppelt") {
        val runner =
            FakeProcessRunner(
                FakeProcessRunner.Run(
                    exitCode = 1,
                    files = mapOf("rechnung.log" to transcript("lualatex-zwei-fehler.log")),
                ),
            )

        val error = shouldThrow<ToolchainException> { typeset(runner) }

        val lines = error.message!!.lines().filter { it.isNotBlank() }
        lines.distinct() shouldHaveSize lines.size
        error.message!! shouldContain "gibtesnicht"
        error.message!! shouldContain "auchnicht"
    }

    test("ohne Fehlerzeile bleibt der Schluss des Protokolls übrig") {
        val runner =
            FakeProcessRunner(
                FakeProcessRunner.Run(exitCode = 1, files = mapOf("rechnung.log" to "Zeile eins\nZeile zwei\n")),
            )

        val error = shouldThrow<ToolchainException> { typeset(runner) }

        error.message!! shouldContain "Zeile zwei"
    }

    test("stürzt LuaLaTeX ab, bevor es ein Protokoll schreibt, zählt die Prozessausgabe") {
        val runner = FakeProcessRunner(FakeProcessRunner.Run(exitCode = 1, output = "! I can't find file 'rechnung'."))

        val error = shouldThrow<ToolchainException> { typeset(runner) }

        error.message!! shouldContain "can't find file"
    }

    context("Inhalt, der vom Blatt fällt (ADR-0020)") {
        val overflow = transcript("lualatex-seite-laeuft-ueber.log")

        test("bricht ab, obwohl LuaLaTeX Erfolg meldet, und nennt die Zeile aus dem Protokoll") {
            val runner = FakeProcessRunner(successfulRun(overflow))

            val error = shouldThrow<ToolchainException> { typeset(runner) }

            error.message!! shouldContain "mehr, als das Blatt fasst"
            error.message!! shouldContain "Overfull \\vbox (405.0pt too high)"
            error.message!! shouldNotContain "luaotfload"
        }

        test("eine zu breite Zeile bricht nicht ab: Sie ragt in den Rand, sie fehlt nicht") {
            val runner = FakeProcessRunner(successfulRun(transcript("lualatex-zeile-zu-breit.log")))

            typeset(runner).exists() shouldBe true
        }

        test("es zählt der letzte Lauf, denn aus ihm stammt das PDF") {
            // Zwei Mitschnitte hintereinander: ein erster Lauf, der überläuft und
            // einen zweiten verlangt.
            val overflowFirst = overflow + transcript("lualatex-verlangt-zweiten-lauf.log")

            typeset(FakeProcessRunner(successfulRun(overflowFirst), successfulRun())).exists() shouldBe true

            shouldThrow<ToolchainException> {
                typeset(FakeProcessRunner(successfulRun(transcript("lualatex-verlangt-zweiten-lauf.log")), successfulRun(overflow)))
            }
        }
    }

    test("meldet der Lauf Erfolg, ohne ein PDF zu hinterlassen, ist das ein Fehler") {
        val runner = FakeProcessRunner(FakeProcessRunner.Run(files = mapOf("rechnung.log" to finished)))

        val error = shouldThrow<ToolchainException> { typeset(runner) }

        error.message!! shouldContain "meldete Erfolg, hat aber kein PDF geschrieben"
    }

    // Keiner der beiden Zweige fragt die Maschine, auf der der Test läuft (#475).
    test("fehlt lualatex, nennt die Meldung das Programm und seinen Installationshinweis") {
        val compiler = LatexCompiler(FakeProcessRunner(missing = setOf("lualatex")))

        val error = shouldThrow<ToolchainException> { compiler.checkAvailable() }

        error.message!! shouldContain "lualatex"
        error.message!! shouldContain "https://tug.org/texlive/"
    }

    test("ist lualatex da, meldet checkAvailable nichts") {
        shouldNotThrowAny { LatexCompiler(FakeProcessRunner()).checkAvailable() }
    }
})
