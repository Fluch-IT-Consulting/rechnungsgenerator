package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import de.fluchitconsulting.rechnungsgenerator.RechnungsgeneratorCommand
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText

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
    fun erzeuge(
        rechnung: String,
        zielname: String = rechnung,
        eingabe: Path = Path.of("src/test/resources/$rechnung.yaml"),
    ): Pair<Path, List<String>> {
        val ziel = workDir.resolve("$zielname.pdf")
        val ausgabe = mutableListOf<String>()
        val command = RechnungsgeneratorCommand()
        command.context { echoMessage = { _, message, _, _ -> ausgabe += message.toString() } }

        val fehler =
            runCatching {
                command.parse(
                    arrayOf(
                        "erzeuge",
                        eingabe.toString(),
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
     * Dieselbe Rechnungsdatei ergibt dasselbe PDF, Byte für Byte – auch wenn
     * jeder Lauf in einem eigenen Temp-Verzeichnis setzt. Nur so taugt die
     * Prüfsumme des PDF, um nach einer Änderung zu sehen, ob sich etwas geändert
     * hat.
     *
     * Hier und nicht im schnellen Test: Ob LuaLaTeX Datum und Trailer-ID
     * wirklich aus dem übernimmt, was es bekommt, zeigt nur ein echter Lauf.
     */
    test("zwei Läufe über dieselbe Datei ergeben dasselbe PDF, datiert auf das Rechnungsdatum") {
        val (erster, _) = erzeuge("rechnung", "reproduzierbar-1")
        val (zweiter, _) = erzeuge("rechnung", "reproduzierbar-2")

        val bytes = erster.readBytes()
        withClue("${erster.fileSize()} und ${zweiter.fileSize()} Bytes") {
            bytes.contentEquals(zweiter.readBytes()) shouldBe true
        }
        // ISO-8859-1 bildet jedes Byte auf ein Zeichen ab, so lässt sich im
        // PDF nach Text suchen, ohne dass der Binärteil beim Dekodieren stört.
        // Die Rechnung ist auf den 14.08.2026 datiert.
        String(bytes, Charsets.ISO_8859_1) shouldContain "/CreationDate(D:20260814000000+00'00')"
    }

    /**
     * Text aus den Eingabedaten steht im PDF so, wie er in der YAML-Datei
     * steht. Ohne `Ligatures=TeXOff` in der Vorlage zöge die Schrift "A--C" zu
     * "A–C" zusammen und "<<A>>" zu "«A»" (#81) – das LaTeX sähe dabei richtig
     * aus, deshalb prüft das nur ein echter Lauf.
     *
     * Jede Folge steht in einer eigenen, kurzen Bezeichnung: In der schmalen
     * Spalte könnte ein Umbruch eine lange Bezeichnung zerteilen, und dann
     * fände `contains` sie nicht, ohne dass etwas falsch gesetzt wäre.
     */
    test("Zeichenfolgen, die TeX zu einem Zeichen zusammenzieht, stehen unverändert im PDF") {
        val bezeichnungen =
            listOf("Paket A--C", "Paket A---C", "Paket <<A>>", "Paket ``A''", "Paket ,,A''")
        val positionen =
            bezeichnungen.joinToString("") {
                "  - bezeichnung: \"$it\"\n    menge: 1\n    einheit: pauschal\n    einzelpreis: 10.00\n"
            }
        val beispiel = Path.of("src/test/resources/rechnung.yaml").readText()
        val eingabe = workDir.resolve("ligaturen.yaml")
        eingabe.writeText(beispiel.substringBefore("positionen:") + "positionen:\n" + positionen)

        val (pdf, _) = erzeuge("ligaturen", eingabe = eingabe)

        // Die Ausgabe in eine Datei, nicht in einen Stream – aus demselben
        // Grund wie bei den Werkzeugen des Programms (siehe ProcessRunner).
        val text = workDir.resolve("ligaturen.txt")
        val prozess =
            ProcessBuilder("pdftotext", "-enc", "UTF-8", pdf.toString(), text.toString())
                .redirectErrorStream(true)
                .redirectOutput(workDir.resolve("pdftotext.log").toFile())
                .start()
        prozess.waitFor(30, TimeUnit.SECONDS) shouldBe true
        prozess.exitValue() shouldBe 0

        val gesetzt = text.readText()
        bezeichnungen.forEach { gesetzt shouldContain it }
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
                creationDate = LocalDate.of(2026, 1, 1),
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
                    creationDate = LocalDate.of(2026, 1, 1),
                )
            }

        fehler.message!! shouldContain "gibtesnicht"
    }
})
