package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import de.fluchitconsulting.rechnungsgenerator.core.accepted.accepted
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.core.values.Design
import de.fluchitconsulting.rechnungsgenerator.generate.render.InvoiceRenderer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.Tag
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.Enabled
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDate
import java.util.HexFormat
import kotlin.io.path.copyTo
import kotlin.io.path.createTempDirectory
import kotlin.io.path.fileSize
import kotlin.io.path.readBytes
import kotlin.io.path.readLines
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
 * Ob das Programm `verapdf` installiert ist.
 *
 * Seit #226 prüft der Generator eingebaut, und veraPDF ist keine
 * Voraussetzung mehr – weder für ihn noch für seinen Build. Die zwei Tests,
 * die das Programm wirklich brauchen, überspringen sich deshalb, statt rot zu
 * werden. Übersprungen und nicht bestanden, wie beim Altersnachweis der
 * EU-Liste: Ein Netz, das fehlt, soll nicht aussehen wie eines, das hält.
 */
private val withVerapdf =
    if (ExternalTool.canRun("verapdf")) {
        Enabled.enabled
    } else {
        Enabled.disabled(
            "Das Programm verapdf ist nicht installiert (brew install verapdf). " +
                "Der eingebaute Prüfer wird trotzdem auf Herz und Nieren geprüft; " +
                "allein der Vergleich der beiden Prüfer entfällt.",
        )
    }

/**
 * Der Integrationstest, der die Zusage des Projekts einlöst: Ohne gültiges
 * PDF/A gibt es keinen grünen Build. Er ruft LuaLaTeX und veraPDF wirklich auf –
 * fehlen sie, schlägt er fehl, statt sich stillschweigend zu überspringen.
 */
class ToolchainIntegrationTest : FunSpec({
    tags(Toolchain)

    val workDir = createTempDirectory("rechnungsgenerator-test")
    val run = ToolchainRun(workDir)

    // Die Stammdaten des Beispiels mit dem Design, dazu auf Wunsch eine
    // andere Firma.
    fun sellerConfigFor(
        design: Design,
        firm: String? = null,
    ): Path {
        val example = Path.of("src/test/resources/stammdaten.yaml").readText()
        val content = if (firm == null) example else example.replace(Regex("(?m)^firma: .*$"), "firma: $firm")
        return workDir.resolve("stammdaten-$design${if (firm == null) "" else "-firma"}.yaml").also {
            it.writeText(content + "design: $design\n")
        }
    }

    // Der eingebaute Prüfer braucht nichts; verlangt wird hier also LuaLaTeX.
    // Dass der Generator ohne installiertes veraPDF läuft, ist seit #226 eine
    // Zusage – und sie gilt auch für seinen Build.
    test("die benötigten Werkzeuge sind vorhanden") {
        LuaLatexTypesetter(BuiltInPdfAValidator).checkAvailable()
    }

    // Ob jede Rechnungsart ein gültiges PDF/A-1b ergibt, prüft
    // PflichtangabenTest – für alle Arten, nicht nur für drei.

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
        val (first, _) = run.generate("rechnung", "reproduzierbar-1")
        val (second, _) = run.generate("rechnung", "reproduzierbar-2")

        val bytes = first.readBytes()
        withClue("${first.fileSize()} und ${second.fileSize()} Bytes") {
            bytes.contentEquals(second.readBytes()) shouldBe true
        }
        // ISO-8859-1 bildet jedes Byte auf ein Zeichen ab, so lässt sich im
        // PDF nach Text suchen, ohne dass der Binärteil beim Dekodieren stört.
        // Die Rechnung ist auf den 14.08.2026 datiert.
        String(bytes, Charsets.ISO_8859_1) shouldContain "/CreationDate(D:20260814000000+00'00')"
    }

    /**
     * Der Jobname steht nicht in [PdfSources] und bestimmt trotzdem Bytes:
     * pdfx bildet die `xmpMM:DocumentID` aus der MD5-Summe über
     * "<Jobname>.pdf" (#297). Deshalb bleibt er Rechnungsart und -nummer –
     * ein fester Name gäbe allen Rechnungen dieselbe DocumentID, ein anderer
     * änderte jedes PDF.
     *
     * Beides hält dieser Test fest: woraus die DocumentID entsteht, und dass
     * sie das Einzige ist, was am Jobnamen hängt. Bildet pdfx sie eines Tages
     * anders, wird er rot, und die Begründung in ADR-0005 gehört neu geprüft.
     */
    test("der Jobname bestimmt allein die DocumentID, und die bildet pdfx aus ihm") {
        val invoice = YamlLoader.loadInvoice(Path.of("src/test/resources/rechnung.yaml"))
        val config = YamlLoader.loadSellerConfig(Path.of("src/test/resources/stammdaten.yaml"))
        val accepted = CalculatedInvoice(invoice, config).accepted()
        val sources = InvoiceRenderer.render(accepted).sources

        val jobNames = listOf(accepted.fileBaseName, "rechnung")
        val pdfs =
            jobNames.map { jobName ->
                val ownDir = createTempDirectory(workDir, "jobname")
                String(LatexCompiler(ExternalTool).compile(ownDir, jobName, sources).readBytes(), Charsets.ISO_8859_1)
            }

        val documentId = Regex("<xmpMM:DocumentID>uuid:([0-9A-F-]+)</xmpMM:DocumentID>")
        jobNames.zip(pdfs).forEach { (jobName, pdf) ->
            documentId.find(pdf)!!.groupValues[1] shouldBe pdfxUuid("$jobName.pdf")
        }
        pdfs.map { documentId.replace(it, "") }.distinct().size shouldBe 1
    }

    /**
     * Dasselbe mit Logo (#198): Das Logo liegt als eigene Datei neben der .tex
     * und geht in die Trailer-ID ein. Zwei Läufe mit denselben Quellen samt
     * Logo ergeben also auch hier dasselbe PDF – und das Logo steht darin.
     */
    test("mit Logo ergeben zwei Läufe dasselbe PDF, und das Logo steht darin") {
        val logo = Path.of("src/test/resources/logo.tex").toAbsolutePath()
        val sellerConfig =
            workDir.resolve("stammdaten-logo.yaml").also {
                it.writeText(Path.of("src/test/resources/stammdaten.yaml").readText() + "design: prag\nlogo: $logo\n")
            }

        val (first, _) = run.generate("rechnung", "logo-1", sellerConfig = sellerConfig)
        val (second, _) = run.generate("rechnung", "logo-2", sellerConfig = sellerConfig)

        first.readBytes().contentEquals(second.readBytes()) shouldBe true
        run.textOf(first) shouldContain "MUSTERIT"
    }

    /**
     * Eine Rechnung hat keine Lesezeichen, und das PDF sagt das auch (#321):
     * Mit Lesezeichen verlangte hyperref ohne Not einen weiteren Lauf, und
     * `/UseOutlines` öffnete im Viewer eine leere Seitenleiste. Jedes Design:
     * pdfx lädt zwar die Grundlage, aber was eine Vorlage danach lädt, kann
     * hyperref noch umstellen.
     */
    test("das PDF hat keine Lesezeichen und öffnet keine Lesezeichenleiste") {
        val pdfs = Design.entries.map { run.generate("rechnung", "lesezeichen-$it", sellerConfig = sellerConfigFor(it)).first }

        pdfs.forEach { pdf ->
            val text = String(pdf.readBytes(), Charsets.ISO_8859_1)
            text shouldContain "/PageMode/UseNone"
            text shouldNotContain "/Outlines"
        }
    }

    /**
     * Der Faktor der Logodatei kommt an (#199):Derselbe Kreis, einmal mit
     * `\logoskalierung` 1.2 und einmal ohne, steht im Bild 20 % höher.
     * Gemessen an den Zeilen mit blauen Pixeln im oberen Drittel der Seite –
     * dort steht sonst nur schwarzer Text.
     *
     * Mit 300 dpi: Das Logo ist dann rund 142 und 170 Pixel hoch, und ein
     * Pixel Rundung an jedem Rand verschiebt das Verhältnis um weniger als
     * 1 %. Bei 100 dpi waren es 47 und 57 Pixel, und schon ein Pixel je
     * Messung konnte die Toleranz überschreiten.
     */
    test("der Skalierungsfaktor der Logodatei setzt das Logo größer") {
        val scaledLogo = Path.of("src/test/resources/logo-skaliert.tex")
        val unscaledLogo =
            workDir.resolve("logo-ohne-faktor.tex").also { file ->
                file.writeText(scaledLogo.readLines().filterNot { "logoskalierung" in it }.joinToString("\n"))
            }

        fun logoHeight(logo: Path): Int {
            val sellerConfig =
                workDir.resolve("stammdaten-${logo.fileName}.yaml").also {
                    it.writeText(
                        Path.of("src/test/resources/stammdaten.yaml").readText() +
                            "design: prag\nlogo: ${logo.toAbsolutePath()}\n",
                    )
                }
            val (pdf, output) = run.generate("rechnung", "hoehe-${logo.fileName}", sellerConfig = sellerConfig)
            withClue(output.joinToString("\n")) { output shouldContain "PDF/A-1b: gültig" }

            val image = run.imageOf(pdf, dpi = 300)
            return (0 until image.height / 3).count { y ->
                (0 until image.width).any { x ->
                    val rgb = image.getRGB(x, y)
                    val red = (rgb shr 16) and 0xFF
                    val blue = rgb and 0xFF
                    blue - red > 80
                }
            }
        }

        val scaledHeight = logoHeight(scaledLogo)
        val unscaledHeight = logoHeight(unscaledLogo)

        withClue("$scaledHeight und $unscaledHeight Pixel") { scaledHeight.toDouble() / unscaledHeight shouldBe (1.2 plusOrMinus 0.03) }
    }

    /**
     * Text aus den Eingabedaten steht im PDF so, wie er in der YAML-Datei
     * steht. Ohne `Ligatures=TeXOff` in der Vorlage zöge die Schrift "A--C" zu
     * "A–C" zusammen und "<<A>>" zu "«A»" (#81) – das LaTeX sähe dabei richtig
     * aus, deshalb prüft das nur ein echter Lauf. Jedes Design, weil jedes
     * seine Schrift selbst wählt.
     *
     * Jede Folge steht in einer eigenen, kurzen Bezeichnung: In der schmalen
     * Spalte könnte ein Umbruch eine lange Bezeichnung zerteilen, und dann
     * fände `contains` sie nicht, ohne dass etwas falsch gesetzt wäre.
     */
    test("Zeichenfolgen, die TeX zu einem Zeichen zusammenzieht, stehen unverändert im PDF") {
        val names =
            listOf("Paket A--C", "Paket A---C", "Paket <<A>>", "Paket ``A''", "Paket ,,A''")
        val items =
            names.joinToString("") {
                "  - bezeichnung: \"$it\"\n    menge: 1\n    einheit: pauschal\n    einzelpreis: 10.00\n"
            }
        val example = Path.of("src/test/resources/rechnung.yaml").readText()
        val input = workDir.resolve("ligaturen.yaml")
        input.writeText(example.substringBefore("positionen:") + "positionen:\n" + items)

        for (design in Design.entries) {
            val (pdf, _) = run.generate("ligaturen-$design", input = input, sellerConfig = sellerConfigFor(design))

            val typesetText = run.textOf(pdf)
            withClue(design) { names.forEach { typesetText shouldContain it } }
        }
    }

    /**
     * Nach einem Punkt, der kein Satzende ist, steht der gewöhnliche
     * Wortzwischenraum (#143): "Dr. Erika" wie "Erika Muster". Von sich aus
     * setzte TeX nach "Dr." den breiteren Abstand eines Satzendes. Im
     * Seitenfuß ebenso, den LaTeX in der Ausgaberoutine mit `\normalsfcodes`
     * setzt und nicht mit dem `\frenchspacing` des Textes (#353).
     *
     * Gemessen wird innerhalb einer Zeile, wo Schrift und Grad gleich sind:
     * der Abstand nach "Dr." gegen den nach "Erika". Die Firma trägt das "Dr."
     * in den Fuß, die Position in den Text. Wie das LaTeX aussieht, sagt dazu
     * nichts, deshalb ein echter Lauf – je Design, weil jedes seinen Fuß
     * selbst setzt.
     */
    test("nach einer Abkürzung steht der gewöhnliche Wortabstand, auch im Seitenfuß") {
        val example = Path.of("src/test/resources/rechnung.yaml").readText()
        val input = workDir.resolve("abkuerzung.yaml")
        input.writeText(
            example.substringBefore("positionen:") + "positionen:\n" +
                "  - bezeichnung: \"Termin mit Dr. Erika Muster\"\n    menge: 1\n    einheit: pauschal\n    einzelpreis: 10.00\n",
        )

        for (design in Design.entries) {
            val (pdf, _) =
                run.generate("abkuerzung-$design", input = input, sellerConfig = sellerConfigFor(design, firm = "Praxis Dr. Erika Muster"))
            val page = run.wordsOf(pdf)

            // Je Fundstelle: "Dr.", "Erika" und das Wort danach, in einer Zeile.
            val measured =
                page.words.windowed(3).filter { (dr, erika, next) ->
                    dr.text == "Dr." && erika.text == "Erika" && dr.yMin == erika.yMin && erika.yMin == next.yMin
                }
            withClue("$design: Fundstellen im Fuß") { measured.count { (dr) -> dr.yMin > 0.85 * page.height } shouldBeGreaterThan 0 }
            withClue("$design: Fundstellen im Text") { measured.count { (dr) -> dr.yMin < 0.5 * page.height } shouldBeGreaterThan 0 }
            measured.forEach { (dr, erika, next) ->
                withClue("$design, Zeile bei ${dr.yMin} pt") { erika.xMin - dr.xMax shouldBe (next.xMin - erika.xMax plusOrMinus 0.05) }
            }
        }
    }

    /**
     * Eine Zeile, die mit `[` beginnt, steht unverändert im PDF. Hinter einem
     * rohen `\\` liest LaTeX die Klammer als Abstandsangabe – `\\[Hinterhaus]`
     * – und bricht ab (#147). Das LaTeX sähe dabei richtig aus: `[` ist kein
     * Sonderzeichen, und `escapeLatex` lässt es stehen.
     */
    test("eine Anschriftszeile, die mit eckiger Klammer beginnt, steht unverändert im PDF") {
        val street = "[Hinterhaus] Industriestraße 1"
        val example = Path.of("src/test/resources/rechnung.yaml").readText()
        val input = workDir.resolve("klammer.yaml")
        input.writeText(example.replace("strasse: Industriestraße 1", "strasse: \"$street\""))

        val (pdf, _) = run.generate("klammer", input = input)

        run.textOf(pdf) shouldContain street
    }

    /**
     * Ein tagesgenauer Zeitraum steht als Text im PDF. Das schmale Leerzeichen
     * um den Strich kennt TeX Gyre Heros nicht; roh durchgereicht las
     * `pdftotext` an seiner Stelle zweimal U+FFFD (#192).
     */
    test("ein tagesgenauer Leistungszeitraum steht als Text im PDF") {
        val (pdf, _) = run.generate("schlussrechnung", targetName = "zeitraum")

        run.textOf(pdf) shouldContain "Leistungszeitraum: 01.06.2026 – 31.10.2026"
    }

    /**
     * Sieht aus wie ein Doppelgänger von `ExternalPdfAValidatorTest`, ist aber
     * das Gegenstück dazu: Der schnelle Test beweist, dass wir einen
     * aufgezeichneten Bericht richtig lesen. Dieser hier beweist, dass die
     * Aufzeichnung noch stimmt – dass echtes veraPDF bei einem nicht konformen
     * PDF weiterhin Exitcode 1 liefert und weiterhin dieses XML.
     *
     * Ein Mitschnitt ist die Momentaufnahme eines fremden Programms. Er wird
     * still falsch, wenn das Programm sein Format ändert, und dann sind die
     * schnellen Tests weiterhin grün und prüfen das Falsche. Wer hier aufräumen
     * will, nimmt genau das Netz weg, das die anderen trägt.
     */
    test("ein nicht konformes PDF wird als solches gemeldet, nicht als Fehler")
        .config(enabledOrReasonIf = { withVerapdf }) {
            val plain =
                LatexCompiler(ExternalTool).compile(
                    workingDir = workDir,
                    jobName = "ohne-pdfa",
                    sources =
                        PdfSources(
                            tex = "\\documentclass{article}\\begin{document}Kein PDF/A.\\end{document}",
                            xmpMetadata = "",
                            creationDate = LocalDate.of(2026, 1, 1),
                        ),
                )

            val validation = ExternalPdfAValidator(ExternalTool).validate(plain)

            validation.valid shouldBe false
            validation.violations.shouldNotBeEmpty()
        }

    /**
     * Das Gegenstück zum Regressionstest in `ExternalPdfAValidatorTest`: Der
     * schnelle Test beweist, dass ein Protokoll auf stderr uns nicht stört.
     * Dieser hier beweist, dass echtes veraPDF sein Protokoll weiterhin dorthin
     * schreibt und den Bericht auf stdout lässt (#227).
     *
     * `nicht-konform-mit-warnung.pdf` trägt dafür eine falsche `/Length` im
     * Inhaltsstrom. veraPDF meldet das, und vor #227 stand diese Meldung mitten
     * im XML – der Parser brach ab, und für die Benutzerin sah es aus wie ein
     * Absturz des Generators.
     */
    test("ein PDF, über das veraPDF sich beschwert, wird trotzdem gelesen")
        .config(enabledOrReasonIf = { withVerapdf }) {
            val noisy =
                Path
                    .of("src/test/resources/nicht-konform-mit-warnung.pdf")
                    .copyTo(workDir.resolve("nicht-konform-mit-warnung.pdf"), overwrite = true)

            val result = ExternalPdfAValidator(ExternalTool).validate(noisy)

            result.valid shouldBe false
            result.violations.shouldNotBeEmpty()
        }

    /**
     * Zwei Prüfer über dasselbe PDF, und sie sollen dasselbe sagen (ADR-0014).
     *
     * Das ist der Preis dafür, dass es zwei gibt: Die eingebaute Bibliothek
     * kommt aus Maven Central, das Programm `verapdf` aus einer eigenen
     * Installation, und beide können auseinanderlaufen. Dieser Test wird rot,
     * sobald sie es tun – und das ist sein Zweck, nicht ein Defekt.
     *
     * Verglichen werden Urteil und Regelkennungen, nicht der Wortlaut der
     * Meldungen: Den setzt das Programm mit den Werten des Dokuments zusammen
     * ("first four bytes = 0, 0, 0, 0"), die Bibliothek liefert die Vorlage
     * aus dem Profil ("%1, %2, %3, %4"). Der Wortlaut ist Darstellung, die
     * Regelkennung ist die Aussage.
     *
     * Zwei Dokumente, weil ein gültiges keine Regelliste hat, an der sich ein
     * Unterschied zeigen könnte. Woher `nicht-konform.pdf` kommt und welche
     * Regeln es verletzt, steht seit #238 bei `BuiltInPdfAValidatorTest`: Der
     * läuft in jedem Build, dieser hier nur mit installiertem veraPDF.
     *
     * Dazu ein drittes, über das keiner urteilen kann: `kein-pdf.pdf`. Dort
     * gibt es kein Urteil zu vergleichen, verglichen wird der Vertrag der Naht
     * – beide werfen [ToolchainException] (#294).
     */
    test("beide Prüfer urteilen über dasselbe PDF gleich")
        .config(enabledOrReasonIf = { withVerapdf }) {
            val (valid, _) = run.generate("rechnung", targetName = "zwei-pruefer")
            // Ins Arbeitsverzeichnis kopiert: Der externe Prüfer legt seine
            // Ausgabedatei neben das PDF, und das wäre hier der Testbestand.
            val nonCompliant =
                Path.of("src/test/resources/nicht-konform.pdf").copyTo(workDir.resolve("nicht-konform.pdf"), overwrite = true)

            for (pdf in listOf(valid, nonCompliant)) {
                val builtIn = BuiltInPdfAValidator.validate(pdf)
                val external = ExternalPdfAValidator(ExternalTool).validate(pdf)

                // Die Kennung als eine Zeichenkette: So steht im Fehlerfall
                // "6.3.4-1" im Bericht und nicht ein Paar.
                fun rules(result: ValidationResult) =
                    result.violations.map { "${it.clause}-${it.testNumber}" }.sorted()

                withClue("$pdf: Urteil") { builtIn.valid shouldBe external.valid }
                withClue("$pdf: Regeln") { rules(builtIn) shouldBe rules(external) }
            }

            val noPdf =
                Path.of("src/test/resources/kein-pdf.pdf").copyTo(workDir.resolve("kein-pdf.pdf"), overwrite = true)
            withClue("eingebaut") { shouldThrow<ToolchainException> { BuiltInPdfAValidator.validate(noPdf) } }
            withClue("extern") { shouldThrow<ToolchainException> { ExternalPdfAValidator(ExternalTool).validate(noPdf) } }
        }

    /**
     * Das Gegenstück zu `LatexCompilerTest` aus demselben Grund: Dieser Test
     * belegt, dass echtes LuaLaTeX seine Fehler weiterhin mit "!" beginnt – die
     * Annahme, auf der das Auswerten des Protokolls beruht.
     */
    test("ein LaTeX-Fehler kommt als ToolchainException an") {
        val error =
            shouldThrow<ToolchainException> {
                LatexCompiler(ExternalTool).compile(
                    workingDir = workDir,
                    jobName = "kaputt",
                    sources =
                        PdfSources(
                            tex = "\\documentclass{article}\\begin{document}\\gibtesnicht\\end{document}",
                            xmpMetadata = "",
                            creationDate = LocalDate.of(2026, 1, 1),
                        ),
                )
            }

        error.message!! shouldContain "gibtesnicht"
    }
})

/** Die UUID, wie pdfx sie aus einem Text bildet: dessen MD5-Summe, gruppiert 8-4-4-4-12. */
private fun pdfxUuid(text: String): String {
    val md5 = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("MD5").digest(text.toByteArray()))
    return listOf(md5.substring(0, 8), md5.substring(8, 12), md5.substring(12, 16), md5.substring(16, 20), md5.substring(20))
        .joinToString("-")
}
