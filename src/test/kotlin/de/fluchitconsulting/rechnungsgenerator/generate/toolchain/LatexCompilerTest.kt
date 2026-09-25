package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

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
 * Was zwischen Prozessaufruf und fertigem PDF passiert – ohne LuaLaTeX.
 *
 * Die Protokolle sind echte Mitschnitte aus `src/test/resources/werkzeugausgaben/`;
 * erfundene würden gerade die Eigenheiten verfehlen, um die es geht (das
 * Paketgeplauder vor den Fehlerzeilen, die Wendung, mit der LaTeX einen
 * weiteren Lauf verlangt).
 *
 * Der [FakeProcessRunner] legt sie ab, wo LuaLaTeX sie ablegen würde. Deshalb
 * geht dieser Test durch denselben Code wie ein echter Lauf: Das Protokoll wird
 * von der Platte gelesen, das PDF auf der Platte gesucht.
 */
class LatexCompilerTest : FunSpec({

    fun transcript(name: String) = File("src/test/resources/werkzeugausgaben/$name").readText()

    val finished = "Output written on rechnung.pdf (1 page, 12345 bytes)."

    fun workDir() = createTempDirectory("latexcompiler-test")

    /** Ein Lauf, der ein Protokoll und ein PDF hinterlässt – der Normalfall. */
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

    /** Die Trailer-ID aus dem Aufruf, mit dem LuaLaTeX gestartet wurde. */
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
        // 1786665600 ist 2026-08-14T00:00:00Z. Ohne TZ schriebe LuaTeX die
        // Zeitzone des Rechners dahinter, und das PDF hinge am Rechner.
        val runner = FakeProcessRunner(successfulRun())

        typeset(runner)

        runner.environments.single() shouldBe mapOf("SOURCE_DATE_EPOCH" to "1786665600", "TZ" to "UTC")
    }

    test("dieselben Quellen ergeben dieselbe Trailer-ID, andere eine andere") {
        // setzen legt jedes Mal ein neues Arbeitsverzeichnis an – der Pfad
        // darf in die ID also nicht eingehen.
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
            // Die .tex nennt das Logo nur per \\input. Ginge es nicht in die ID
            // ein, änderte ein neues Logo das PDF, aber nicht seine ID.
            val withLogo = trailerIdOf(files = mapOf("logo.tex" to logo))

            trailerIdOf(files = mapOf("logo.tex" to logo.copyOf())) shouldBe withLogo
            trailerIdOf(files = mapOf("logo.tex" to "\\fill (0,0) circle (2);".toByteArray())) shouldNotBe withLogo
            trailerIdOf() shouldNotBe withLogo
        }

        test("ohne weitere Dateien bleibt die ID, wie sie vor #198 war") {
            // Sonst ergäbe jede alte Rechnung beim Nachbauen ein anderes PDF.
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
            // Path.of("..").fileName ist wieder "..": Die Prüfung auf einen
            // schlichten Namen allein ließe sie durch, und geschrieben würde
            // ins Verzeichnis darüber oder auf das Arbeitsverzeichnis selbst.
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

        // Die .xmpdata muss denselben Namen tragen wie die .tex – daraus baut
        // pdfx die Metadaten des PDF/A.
        dir.resolve("rechnung.tex").readText() shouldContain "documentclass"
        dir.resolve("rechnung.xmpdata").readText() shouldContain "Title"
    }

    // Steht für ein Temp-Verzeichnis, das voll oder schreibgeschützt ist: Der
    // Satz kommt nicht zustande, und die Meldung sagt, wo (#300).
    test("lassen sich die Quellen nicht schreiben, kommt eine ToolchainException mit dem Verzeichnis") {
        val missing = workDir().resolve("gibt-es-nicht")

        val error = shouldThrow<ToolchainException> { typeset(FakeProcessRunner(successfulRun()), missing) }

        error.message!! shouldContain missing.toString()
    }

    // Ein Verzeichnis unter dem Namen des Protokolls besteht exists() und
    // scheitert am Lesen – der Fall eines unlesbaren Protokolls, ohne dass
    // Rechte gesetzt werden müssen, die unter root nicht greifen (#336).
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
        // "LaTeX Warning: Label(s) may have changed. Rerun to get
        // cross-references right." – aus einem Lauf über ein Dokument mit
        // Querverweisen. Der zweite Lauf schweigt dazu, also ist Schluss.
        val runner =
            FakeProcessRunner(
                successfulRun(transcript("lualatex-verlangt-zweiten-lauf.log")),
                successfulRun(),
            )

        typeset(runner)

        runner.commands shouldHaveSize 2
        // Beide Läufe mit derselben ID und demselben Datum.
        runner.commands.distinct() shouldHaveSize 1
        runner.environments.distinct() shouldHaveSize 1
    }

    test("verlangt longtable einen zweiten Lauf, wird ein zweites Mal gesetzt") {
        // "Table widths have changed. Rerun LaTeX." – longtable sagt es anders
        // als der Kern, ohne "Rerun to get". Aus einem ersten Lauf über eine
        // xltabular mit Spaltenkopf, wie die Positionen sie haben (#321).
        val runner =
            FakeProcessRunner(
                successfulRun(transcript("lualatex-tabellenbreite-geaendert.log")),
                successfulRun(),
            )

        typeset(runner)

        runner.commands shouldHaveSize 2
    }

    test("wer immer weiter verlangt, bekommt trotzdem nur drei Läufe") {
        // Ein Dokument, das nach drei Läufen noch einen vierten fordert,
        // konvergiert nicht – der vierte änderte daran nichts. Die Zahl steht
        // als MAX_PASSES im Code und ist ohne diesen Test unbelegt.
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
        // Die Fundstelle kommt mit – dafür stehen die zwei Folgezeilen drin.
        error.message!! shouldContain "l.3"
        // Das Geplauder bleibt draußen: ein Paketname, der im Protokoll steht,
        // aber nicht zu einer Fehlerzeile gehört.
        error.message!! shouldNotContain "geometry"
        // Aus 98 Zeilen werden eine Handvoll. Die genaue Zahl ist nicht der
        // Punkt, die Größenordnung schon.
        error.message!!.lines().size shouldBeLessThan 15
    }

    test("bei zwei dicht beieinanderliegenden Fehlern steht keine Zeile doppelt") {
        // Der Fall, den distinct() abfängt: Die Dreierblöcke zweier Fehler
        // überlappen sich, wenn sie nah beieinander stehen.
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
        // Besser als eine leere Meldung: Irgendetwas ist ja schiefgegangen.
        val runner =
            FakeProcessRunner(
                FakeProcessRunner.Run(exitCode = 1, files = mapOf("rechnung.log" to "Zeile eins\nZeile zwei\n")),
            )

        val error = shouldThrow<ToolchainException> { typeset(runner) }

        error.message!! shouldContain "Zeile zwei"
    }

    test("stürzt LuaLaTeX ab, bevor es ein Protokoll schreibt, zählt die Prozessausgabe") {
        // Kein rechnung.log in den Dateien: Dann bleibt nur, was der Prozess
        // auf die Ausgabe geschrieben hat.
        val runner = FakeProcessRunner(FakeProcessRunner.Run(exitCode = 1, output = "! I can't find file 'rechnung'."))

        val error = shouldThrow<ToolchainException> { typeset(runner) }

        error.message!! shouldContain "can't find file"
    }

    context("Inhalt, der vom Blatt fällt (ADR-0020)") {
        // Eine Tabelle mit 80 Zeilen auf einer Seite: LuaLaTeX meldet Erfolg,
        // Exitcode 0, und schreibt nur ins Protokoll, dass die Seite überläuft.
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
            // Zwei Mitschnitte hintereinander stehen für einen ersten Lauf, der
            // überläuft und einen zweiten verlangt. Der zweite läuft nicht über.
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
})
