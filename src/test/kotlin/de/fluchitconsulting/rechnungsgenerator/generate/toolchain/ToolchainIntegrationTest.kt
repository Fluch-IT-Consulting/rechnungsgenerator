package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.Tag
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.createTempDirectory
import kotlin.io.path.fileSize
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Markiert die Tests, die die echten Werkzeuge brauchen. CI wählt sie mit
 * `./gradlew build -PohneToolchain` benannt ab (#136) – eine Abwahl in der
 * Aufrufzeile, dieselbe Sorte Schalter wie EU_LISTE_ALTERSPRUEFUNG=aus.
 * Lokal ohne den Schalter läuft weiterhin alles.
 */
object Toolchain : Tag()

/**
 * Die Annahmen über die echten Werkzeuge: Reproduzierbarkeit, Text, der
 * unverändert im PDF ankommt, und wie LuaLaTeX und veraPDF Fehler melden. Er
 * ruft beide wirklich auf – fehlen sie, schlägt er fehl, statt sich
 * stillschweigend zu überspringen.
 *
 * Dass jede Rechnungsart ein gültiges PDF/A ergibt, prüft `PflichtangabenTest`
 * gleich mit (#187); die drei Tests dafür, die hier standen, gibt es nicht mehr.
 */
class ToolchainIntegrationTest : FunSpec({
    tags(Toolchain)

    val workDir = createTempDirectory("rechnungsgenerator-test")
    val run = ToolchainRun(workDir)

    test("die benötigten Werkzeuge sind vorhanden") {
        LuaLatexTypesetter.checkAvailable()
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
        val (erster, _) = run.erzeuge("rechnung", "reproduzierbar-1")
        val (zweiter, _) = run.erzeuge("rechnung", "reproduzierbar-2")

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

        val (pdf, _) = run.erzeuge("ligaturen", eingabe = eingabe)

        val gesetzt = run.textOf(pdf)
        bezeichnungen.forEach { gesetzt shouldContain it }
    }

    /**
     * Eine Zeile, die mit `[` beginnt, steht unverändert im PDF. Hinter einem
     * rohen `\\` liest LaTeX die Klammer als Abstandsangabe – `\\[Hinterhaus]`
     * – und bricht ab (#147). Das LaTeX sähe dabei richtig aus: `[` ist kein
     * Sonderzeichen, und `escapeLatex` lässt es stehen.
     */
    test("eine Anschriftszeile, die mit eckiger Klammer beginnt, steht unverändert im PDF") {
        val strasse = "[Hinterhaus] Industriestraße 1"
        val beispiel = Path.of("src/test/resources/rechnung.yaml").readText()
        val eingabe = workDir.resolve("klammer.yaml")
        eingabe.writeText(beispiel.replace("strasse: Industriestraße 1", "strasse: \"$strasse\""))

        val (pdf, _) = run.erzeuge("klammer", eingabe = eingabe)

        run.textOf(pdf) shouldContain strasse
    }

    /**
     * Ein tagesgenauer Zeitraum steht als Text im PDF. Das schmale Leerzeichen
     * um den Strich kennt TeX Gyre Heros nicht; roh durchgereicht las
     * `pdftotext` an seiner Stelle zweimal U+FFFD (#192).
     */
    test("ein tagesgenauer Leistungszeitraum steht als Text im PDF") {
        val (pdf, _) = run.erzeuge("schlussrechnung", zielname = "zeitraum")

        run.textOf(pdf) shouldContain "Leistungszeitraum: 01.06.2026 – 31.10.2026"
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
