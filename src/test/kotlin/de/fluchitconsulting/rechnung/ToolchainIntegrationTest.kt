package de.fluchitconsulting.rechnung

import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import de.fluchitconsulting.rechnung.toolchain.ExternalTool
import de.fluchitconsulting.rechnung.toolchain.LatexCompiler
import de.fluchitconsulting.rechnung.toolchain.LuaLatexTypesetter
import de.fluchitconsulting.rechnung.toolchain.PdfAValidator
import de.fluchitconsulting.rechnung.toolchain.ToolchainException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.fileSize

/**
 * Der Integrationstest, der die Zusage des Projekts einlöst: Ohne gültiges
 * PDF/A gibt es keinen grünen Build. Er ruft LuaLaTeX und veraPDF wirklich auf –
 * fehlen sie, schlägt er fehl, statt sich stillschweigend zu überspringen.
 */
class ToolchainIntegrationTest : FunSpec({

    val workDir = createTempDirectory("rechnungsgenerator-test")

    /**
     * Erzeugt eine Rechnung mit den echten Werkzeugen – über dasselbe Kommando,
     * das auch ein Mensch aufruft.
     *
     * Nicht über die Einzelteile: Rechnen, Setzen und Prüfen stehen genau
     * einmal zusammen, nämlich im Kommando. Baute dieser Test sie noch einmal
     * nach, gäbe es die Abfolge an zwei Stellen, und die zweite wäre die, die
     * niemand pflegt.
     *
     * Die Ausgaben werden eingesammelt, damit die Meldung von veraPDF im
     * Testbericht landet – sonst stünde dort nur "expected true but was false".
     */
    fun erzeuge(rechnung: String): Pair<Path, List<String>> {
        val ziel = workDir.resolve("$rechnung.pdf")
        val ausgabe = mutableListOf<String>()
        val command = RechnungsgeneratorCommand()
        command.context { echoMessage = { _, message, _, _ -> ausgabe += message.toString() } }

        val fehler =
            runCatching {
                command.parse(
                    arrayOf(
                        "erzeuge",
                        "src/test/resources/$rechnung.yaml",
                        "-s",
                        "src/test/resources/stammdaten.yaml",
                        "-o",
                        ziel.toString(),
                    ),
                )
            }.exceptionOrNull()

        withClue(ausgabe.joinToString("\n")) { fehler shouldBe null }
        return ziel to ausgabe
    }

    test("die benötigten Werkzeuge sind vorhanden") {
        LuaLatexTypesetter.checkAvailable()
    }

    test("erzeugt aus den Beispieldaten ein gültiges PDF/A-1b") {
        val (pdf, ausgabe) = erzeuge("rechnung")

        pdf.exists() shouldBe true
        pdf.fileSize() shouldBe pdf.fileSize().coerceAtLeast(1000)
        withClue(ausgabe.joinToString("\n")) { ausgabe shouldContain "PDF/A-1b: gültig" }
    }

    test("erzeugt aus einer Abschlagsrechnung ein gültiges PDF/A-1b") {
        val (_, ausgabe) = erzeuge("abschlagsrechnung")

        withClue(ausgabe.joinToString("\n")) { ausgabe shouldContain "PDF/A-1b: gültig" }
    }

    test("erzeugt aus einer Schlussrechnung ein gültiges PDF/A-1b") {
        val (_, ausgabe) = erzeuge("schlussrechnung")

        withClue(ausgabe.joinToString("\n")) { ausgabe shouldContain "PDF/A-1b: gültig" }
    }

    /**
     * Sieht aus wie ein Doppelgänger von `PdfAValidatorTest`, ist aber das
     * Gegenstück dazu: Der schnelle Test beweist, dass wir einen
     * aufgezeichneten Bericht richtig lesen. Dieser hier beweist, dass die
     * Aufzeichnung noch stimmt – dass echtes veraPDF bei einem nicht konformen
     * PDF weiterhin Exitcode 1 liefert und weiterhin dieses XML.
     *
     * Ein Mitschnitt ist die Momentaufnahme eines fremden Programms. Er wird
     * still falsch, wenn das Programm sein Format ändert, und dann sind die
     * schnellen Tests weiterhin grün und prüfen das Falsche. Wer hier aufräumen
     * will, nimmt genau das Netz weg, das die anderen trägt.
     */
    test("ein nicht konformes PDF wird als solches gemeldet, nicht als Fehler") {
        val plain =
            LatexCompiler(ExternalTool).compile(
                workingDir = workDir,
                jobName = "ohne-pdfa",
                texSource = "\\documentclass{article}\\begin{document}Kein PDF/A.\\end{document}",
                xmpMetadata = "",
            )

        val validation = PdfAValidator(ExternalTool).validate(plain)

        validation.valid shouldBe false
        validation.violations.shouldNotBeEmpty()
    }

    /**
     * Das Gegenstück zu `LatexCompilerTest` aus demselben Grund: Dieser Test
     * belegt, dass echtes LuaLaTeX seine Fehler weiterhin mit "!" beginnt – die
     * Annahme, auf der das Auswerten des Protokolls beruht.
     */
    test("ein LaTeX-Fehler kommt als ToolchainException an") {
        val fehler =
            shouldThrow<ToolchainException> {
                LatexCompiler(ExternalTool).compile(
                    workingDir = workDir,
                    jobName = "kaputt",
                    texSource = "\\documentclass{article}\\begin{document}\\gibtesnicht\\end{document}",
                    xmpMetadata = "",
                )
            }

        fehler.message!! shouldContain "gibtesnicht"
    }
})
