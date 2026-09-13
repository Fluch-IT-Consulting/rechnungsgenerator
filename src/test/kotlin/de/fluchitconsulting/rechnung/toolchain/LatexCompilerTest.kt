package de.fluchitconsulting.rechnung.toolchain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.time.LocalDate
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
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

    fun mitschnitt(name: String) = File("src/test/resources/werkzeugausgaben/$name").readText()

    val fertig = "Output written on rechnung.pdf (1 page, 12345 bytes)."

    fun arbeitsverzeichnis() = createTempDirectory("latexcompiler-test")

    /** Ein Lauf, der ein Protokoll und ein PDF hinterlässt – der Normalfall. */
    fun erfolgreicherLauf(log: String = fertig) =
        FakeProcessRunner.Run(files = mapOf("rechnung.log" to log, "rechnung.pdf" to "%PDF-1.7"))

    fun setzen(
        runner: FakeProcessRunner,
        dir: java.nio.file.Path = arbeitsverzeichnis(),
        tex: String = "\\documentclass{article}",
    ) = LatexCompiler(runner).compile(dir, "rechnung", tex, "\\Title{Test}", LocalDate.of(2026, 8, 14))

    test("das Erstellungsdatum ist Mitternacht des Datums, in UTC geschrieben") {
        // 1786665600 ist 2026-08-14T00:00:00Z. Ohne TZ schriebe LuaTeX die
        // Zeitzone des Rechners dahinter, und das PDF hinge am Rechner.
        val runner = FakeProcessRunner(erfolgreicherLauf())

        setzen(runner)

        runner.environments.single() shouldBe mapOf("SOURCE_DATE_EPOCH" to "1786665600", "TZ" to "UTC")
    }

    test("dieselben Quellen ergeben dieselbe Trailer-ID, andere eine andere") {
        // setzen legt jedes Mal ein neues Arbeitsverzeichnis an – der Pfad
        // darf in die ID also nicht eingehen.
        fun aufruf(tex: String) =
            FakeProcessRunner(erfolgreicherLauf())
                .also { setzen(it, tex = tex) }
                .commands
                .single()
                .last()

        val erster = aufruf("\\documentclass{article}")

        erster shouldContain "\\pdfvariable trailerid"
        aufruf("\\documentclass{article}") shouldBe erster
        aufruf("\\documentclass{letter}") shouldNotBe erster
    }

    test("die Quelldateien liegen neben dem Ergebnis") {
        val dir = arbeitsverzeichnis()

        setzen(FakeProcessRunner(erfolgreicherLauf()), dir)

        // Die .xmpdata muss denselben Namen tragen wie die .tex – daraus baut
        // pdfx die Metadaten des PDF/A.
        dir.resolve("rechnung.tex").readText() shouldContain "documentclass"
        dir.resolve("rechnung.xmpdata").readText() shouldContain "Title"
    }

    test("ein Lauf genügt, wenn das Protokoll keinen zweiten verlangt") {
        val runner = FakeProcessRunner(erfolgreicherLauf())

        val pdf = setzen(runner)

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
                erfolgreicherLauf(mitschnitt("lualatex-verlangt-zweiten-lauf.log")),
                erfolgreicherLauf(),
            )

        setzen(runner)

        runner.commands shouldHaveSize 2
        // Beide Läufe mit derselben ID und demselben Datum.
        runner.commands.distinct() shouldHaveSize 1
        runner.environments.distinct() shouldHaveSize 1
    }

    test("wer immer weiter verlangt, bekommt trotzdem nur drei Läufe") {
        // Ein Dokument, das nach drei Läufen noch einen vierten fordert,
        // konvergiert nicht – der vierte änderte daran nichts. Die Zahl steht
        // als MAX_PASSES im Code und ist ohne diesen Test unbelegt.
        val hartnaeckig = mitschnitt("lualatex-verlangt-zweiten-lauf.log")
        val runner =
            FakeProcessRunner(
                erfolgreicherLauf(hartnaeckig),
                erfolgreicherLauf(hartnaeckig),
                erfolgreicherLauf(hartnaeckig),
            )

        setzen(runner)

        runner.commands shouldHaveSize 3
    }

    test("ein gescheiterter Lauf nennt die Fundstelle statt tausend Zeilen Paketgeplauder") {
        val runner =
            FakeProcessRunner(
                FakeProcessRunner.Run(exitCode = 1, files = mapOf("rechnung.log" to mitschnitt("lualatex-abbruch.log"))),
            )

        val fehler = shouldThrow<ToolchainException> { setzen(runner) }

        fehler.message!! shouldContain "Undefined control sequence"
        fehler.message!! shouldContain "gibtesnicht"
        // Die Fundstelle kommt mit – dafür stehen die zwei Folgezeilen drin.
        fehler.message!! shouldContain "l.3"
        // Das Geplauder bleibt draußen: ein Paketname, der im Protokoll steht,
        // aber nicht zu einer Fehlerzeile gehört.
        fehler.message!! shouldNotContain "geometry"
        // Aus 98 Zeilen werden eine Handvoll. Die genaue Zahl ist nicht der
        // Punkt, die Größenordnung schon.
        fehler.message!!.lines().size shouldBeLessThan 15
    }

    test("bei zwei dicht beieinanderliegenden Fehlern steht keine Zeile doppelt") {
        // Der Fall, den distinct() abfängt: Die Dreierblöcke zweier Fehler
        // überlappen sich, wenn sie nah beieinander stehen.
        val runner =
            FakeProcessRunner(
                FakeProcessRunner.Run(
                    exitCode = 1,
                    files = mapOf("rechnung.log" to mitschnitt("lualatex-zwei-fehler.log")),
                ),
            )

        val fehler = shouldThrow<ToolchainException> { setzen(runner) }

        val zeilen = fehler.message!!.lines().filter { it.isNotBlank() }
        zeilen.distinct() shouldHaveSize zeilen.size
        fehler.message!! shouldContain "gibtesnicht"
        fehler.message!! shouldContain "auchnicht"
    }

    test("ohne Fehlerzeile bleibt der Schluss des Protokolls übrig") {
        // Besser als eine leere Meldung: Irgendetwas ist ja schiefgegangen.
        val runner =
            FakeProcessRunner(
                FakeProcessRunner.Run(exitCode = 1, files = mapOf("rechnung.log" to "Zeile eins\nZeile zwei\n")),
            )

        val fehler = shouldThrow<ToolchainException> { setzen(runner) }

        fehler.message!! shouldContain "Zeile zwei"
    }

    test("stürzt LuaLaTeX ab, bevor es ein Protokoll schreibt, zählt die Prozessausgabe") {
        // Kein rechnung.log in den Dateien: Dann bleibt nur, was der Prozess
        // auf die Ausgabe geschrieben hat.
        val runner = FakeProcessRunner(FakeProcessRunner.Run(exitCode = 1, output = "! I can't find file 'rechnung'."))

        val fehler = shouldThrow<ToolchainException> { setzen(runner) }

        fehler.message!! shouldContain "can't find file"
    }

    test("meldet der Lauf Erfolg, ohne ein PDF zu hinterlassen, ist das ein Fehler") {
        val runner = FakeProcessRunner(FakeProcessRunner.Run(files = mapOf("rechnung.log" to fertig)))

        val fehler = shouldThrow<ToolchainException> { setzen(runner) }

        fehler.message!! shouldContain "meldete Erfolg, hat aber kein PDF geschrieben"
    }
})
