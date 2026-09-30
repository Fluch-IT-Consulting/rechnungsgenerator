package de.fluchitconsulting.rechnungsgenerator.generate.typesetting

import de.fluchitconsulting.rechnungsgenerator.core.accepted.accepted
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Design
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.generate.einvoice.CiiWriter
import de.fluchitconsulting.rechnungsgenerator.generate.render.InvoiceRenderer
import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import de.fluchitconsulting.rechnungsgenerator.toolchain.process.ExternalTool
import de.fluchitconsulting.rechnungsgenerator.toolchain.process.LuaLatex
import de.fluchitconsulting.rechnungsgenerator.toolchain.process.VeraPdf
import de.fluchitconsulting.rechnungsgenerator.toolchain.validation.BuiltInPdfAValidator
import de.fluchitconsulting.rechnungsgenerator.toolchain.validation.ExternalPdfAValidator
import de.fluchitconsulting.rechnungsgenerator.toolchain.validation.PdfAProfile
import de.fluchitconsulting.rechnungsgenerator.toolchain.validation.ValidationResult
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
import org.apache.pdfbox.Loader
import java.awt.image.BufferedImage
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
import kotlin.math.abs

/** Die Tests, die die echten Werkzeuge brauchen; `-PohneToolchain` wählt sie ab (#136). */
object Toolchain : Tag()

// Übersprungen, nicht bestanden: veraPDF ist seit #226 keine Voraussetzung mehr.
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

/** Fehlt LuaLaTeX, schlägt er fehl, statt sich zu überspringen. */
class ToolchainIntegrationTest : FunSpec({
    tags(Toolchain)

    val workDir = createTempDirectory("rechnungsgenerator-test")
    val run = ToolchainRun(workDir)

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

    fun typesetWithEInvoice(name: String): Pair<TypesetPdf, ByteArray> {
        val invoice = YamlLoader.loadInvoice(Path.of("src/test/resources/rechnung.yaml"))
        val config = YamlLoader.loadSellerConfig(Path.of("src/test/resources/stammdaten.yaml"))
        val accepted = CalculatedInvoice(invoice, config).accepted()
        val xml = CiiWriter.write(accepted).toByteArray()
        val rendered = InvoiceRenderer.render(accepted).sources
        val sources = PdfSources(rendered.tex, rendered.xmpMetadata, rendered.creationDate, rendered.files, xml)
        val typeset = LuaLatexTypesetter(BuiltInPdfAValidator).typeset(createTempDirectory(workDir, name), accepted.fileBaseName, sources)
        return typeset to xml
    }

    // Nur LuaLaTeX: Auch der Build läuft ohne veraPDF (#226).
    test("die benötigten Werkzeuge sind vorhanden") {
        LuaLatexTypesetter(BuiltInPdfAValidator).checkAvailable()
    }

    // Ob LuaLaTeX Datum und Trailer-ID aus den Quellen übernimmt, zeigt nur ein echter Lauf.
    test("zwei Läufe über dieselbe Datei ergeben dasselbe PDF, datiert auf das Rechnungsdatum") {
        val (first, _) = run.generate("rechnung", "reproduzierbar-1")
        val (second, _) = run.generate("rechnung", "reproduzierbar-2")

        val bytes = first.readBytes()
        withClue("${first.fileSize()} und ${second.fileSize()} Bytes") {
            bytes.contentEquals(second.readBytes()) shouldBe true
        }
        // ISO-8859-1, weil es jedes Byte auf ein Zeichen abbildet.
        String(bytes, Charsets.ISO_8859_1) shouldContain "/CreationDate(D:20260814000000+00'00')"
    }

    // ADR-0023; die Metadaten beim Einbetten prüft `EInvoiceEmbedderTest`.
    test("mit E-Rechnung entsteht ein gültiges PDF/A-3u, das ihr XML trägt") {
        val (typeset, xml) = typesetWithEInvoice("e-rechnung")

        typeset.validation.profile shouldBe PdfAProfile.PDFA_3U
        withClue(typeset.validation.describe()) { typeset.validation.valid shouldBe true }
        Loader.loadPDF(typeset.pdf.toFile()).use { document ->
            val attachment =
                document.documentCatalog.names.embeddedFiles.names
                    .getValue("factur-x.xml")
            attachment.embeddedFile.toByteArray().contentEquals(xml) shouldBe true
        }
    }

    // #297. Wird der Test rot, gehört die Begründung in ADR-0005 neu geprüft.
    test("der Jobname bestimmt allein die DocumentID, und die bildet pdfx aus ihm") {
        val invoice = YamlLoader.loadInvoice(Path.of("src/test/resources/rechnung.yaml"))
        val config = YamlLoader.loadSellerConfig(Path.of("src/test/resources/stammdaten.yaml"))
        val accepted = CalculatedInvoice(invoice, config).accepted()
        val sources = InvoiceRenderer.render(accepted).sources

        val jobNames = listOf(accepted.fileBaseName, "rechnung")
        val pdfs =
            jobNames.map { jobName ->
                val ownDir = createTempDirectory(workDir, "jobname")
                String(LatexCompiler(LuaLatex(ExternalTool)).compile(ownDir, jobName, sources).pdf.readBytes(), Charsets.ISO_8859_1)
            }

        val documentId = Regex("<xmpMM:DocumentID>uuid:([0-9A-F-]+)</xmpMM:DocumentID>")
        jobNames.zip(pdfs).forEach { (jobName, pdf) ->
            documentId.find(pdf)!!.groupValues[1] shouldBe pdfxUuid("$jobName.pdf")
        }
        pdfs.map { documentId.replace(it, "") }.distinct().size shouldBe 1
    }

    // #198
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

    // #199. Blau steht im oberen Drittel nur im Logo. Unter 300 dpi kann schon
    // ein Pixel Rundung die Toleranz überschreiten.
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

    // #371. Fast genau die Farbe: Das Innere des Balkens glättet pdftoppm nicht.
    test("die Akzentfarbe der Stammdaten färbt den Balken von helsinki") {
        fun imageWith(accentColor: String?): BufferedImage {
            val name = accentColor ?: "ohne"
            val sellerConfig =
                workDir.resolve("stammdaten-akzent-$name.yaml").also {
                    it.writeText(
                        Path.of("src/test/resources/stammdaten.yaml").readText() +
                            accentColor?.let { color -> "akzentfarbe: $color\n" }.orEmpty(),
                    )
                }
            val (pdf, output) = run.generate("rechnung", "akzent-$name", sellerConfig = sellerConfig)
            withClue(output.joinToString("\n")) { output shouldContain "PDF/A-1b: gültig" }
            return run.imageOf(pdf, dpi = 100)
        }

        fun BufferedImage.countOf(rgb: Int): Int {
            fun near(color: Int, shift: Int) = abs(((color shr shift) and 0xFF) - ((rgb shr shift) and 0xFF)) <= 2
            return (0 until height).sumOf { y ->
                (0 until width).count { x ->
                    val color = getRGB(x, y)
                    near(color, 16) && near(color, 8) && near(color, 0)
                }
            }
        }

        val green = 0x31AE34
        val red = 0x8D313A

        withClue("mit Akzentfarbe") {
            val image = imageWith("31AE34")
            image.countOf(green) shouldBeGreaterThan 1000
            image.countOf(red) shouldBe 0
        }
        withClue("ohne Akzentfarbe") {
            val image = imageWith(null)
            image.countOf(red) shouldBeGreaterThan 1000
            image.countOf(green) shouldBe 0
        }
    }

    // #81; das LaTeX sähe richtig aus. Kurze Bezeichnungen, damit kein Umbruch
    // in der schmalen Spalte eine zerteilt.
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

    // #143, im Fuß #353: Die Ausgaberoutine setzt mit `\normalsfcodes`. Gemessen
    // innerhalb einer Zeile, wo Schrift und Grad gleich sind.
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

    // #147: Hinter `\\` liest LaTeX `[` als Abstandsangabe; das LaTeX sähe richtig aus.
    test("eine Anschriftszeile, die mit eckiger Klammer beginnt, steht unverändert im PDF") {
        val street = "[Hinterhaus] Industriestraße 1"
        val example = Path.of("src/test/resources/rechnung.yaml").readText()
        val input = workDir.resolve("klammer.yaml")
        input.writeText(example.replace("strasse: Industriestraße 1", "strasse: \"$street\""))

        val (pdf, _) = run.generate("klammer", input = input)

        run.textOf(pdf) shouldContain street
    }

    // Kein Doppelgänger von `ExternalPdfAValidatorTest`: Dieser hält fest, dass
    // dessen Mitschnitt von veraPDF noch stimmt.
    test("ein nicht konformes PDF wird als solches gemeldet, nicht als Fehler")
        .config(enabledOrReasonIf = { withVerapdf }) {
            val plain =
                LatexCompiler(LuaLatex(ExternalTool))
                    .compile(
                        workingDir = workDir,
                        jobName = "ohne-pdfa",
                        sources =
                            PdfSources(
                                tex = "\\documentclass{article}\\begin{document}Kein PDF/A.\\end{document}",
                                xmpMetadata = "",
                                creationDate = LocalDate.of(2026, 1, 1),
                            ),
                    ).pdf

            val validation = ExternalPdfAValidator(VeraPdf(ExternalTool)).validate(plain, PdfAProfile.PDFA_1B)

            validation.valid shouldBe false
            validation.violations.shouldNotBeEmpty()
        }

    // Hält den Mitschnitt in `ExternalPdfAValidatorTest` fest: veraPDF protokolliert
    // auf stderr (#227). Das PDF trägt dafür eine falsche `/Length`.
    test("ein PDF, über das veraPDF sich beschwert, wird trotzdem gelesen")
        .config(enabledOrReasonIf = { withVerapdf }) {
            val noisy =
                Path
                    .of("src/test/resources/nicht-konform-mit-warnung.pdf")
                    .copyTo(workDir.resolve("nicht-konform-mit-warnung.pdf"), overwrite = true)

            val result = ExternalPdfAValidator(VeraPdf(ExternalTool)).validate(noisy, PdfAProfile.PDFA_1B)

            result.valid shouldBe false
            result.violations.shouldNotBeEmpty()
        }

    /**
     * ADR-0014. Wird er rot, laufen Bibliothek und Programm auseinander, und das
     * soll er melden. Verglichen werden Regelkennungen, nicht der Wortlaut: Nur
     * das Programm füllt ihn mit den Werten des Dokuments. Bei `kein-pdf.pdf`
     * gibt es kein Urteil, dort gilt der Vertrag der Naht (#294).
     */
    test("beide Prüfer urteilen über dasselbe PDF gleich")
        .config(enabledOrReasonIf = { withVerapdf }) {
            val (valid, _) = run.generate("rechnung", targetName = "zwei-pruefer")
            // Kopiert, weil der externe Prüfer seine Ausgabe neben das PDF legt.
            val nonCompliant =
                Path.of("src/test/resources/nicht-konform.pdf").copyTo(workDir.resolve("nicht-konform.pdf"), overwrite = true)

            val (einvoice, _) = typesetWithEInvoice("zwei-pruefer-e-rechnung")
            val cases =
                listOf(
                    valid to PdfAProfile.PDFA_1B,
                    nonCompliant to PdfAProfile.PDFA_1B,
                    einvoice.pdf to PdfAProfile.PDFA_3U,
                )

            for ((pdf, profile) in cases) {
                val builtIn = BuiltInPdfAValidator.validate(pdf, profile)
                val external = ExternalPdfAValidator(VeraPdf(ExternalTool)).validate(pdf, profile)

                // Mit dem Teil der Norm, weil jeder Weg ihn selbst aus veraPDF liest.
                fun rules(result: ValidationResult) =
                    result.violations.map { "${it.specification} ${it.clause}-${it.testNumber}" }.sorted()

                withClue("$pdf: Urteil") { builtIn.valid shouldBe external.valid }
                withClue("$pdf: Regeln") { rules(builtIn) shouldBe rules(external) }
            }

            val noPdf =
                Path.of("src/test/resources/kein-pdf.pdf").copyTo(workDir.resolve("kein-pdf.pdf"), overwrite = true)
            val external = ExternalPdfAValidator(VeraPdf(ExternalTool))
            withClue("eingebaut") { shouldThrow<ToolchainException> { BuiltInPdfAValidator.validate(noPdf, PdfAProfile.PDFA_1B) } }
            withClue("extern") { shouldThrow<ToolchainException> { external.validate(noPdf, PdfAProfile.PDFA_1B) } }
        }

    // Hält die Annahme von `LatexCompilerTest` fest: LuaLaTeX beginnt Fehler mit "!".
    test("ein LaTeX-Fehler kommt als ToolchainException an") {
        val error =
            shouldThrow<ToolchainException> {
                LatexCompiler(LuaLatex(ExternalTool)).compile(
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

/** Wie pdfx: die MD5-Summe, gruppiert 8-4-4-4-12. */
private fun pdfxUuid(text: String): String {
    val md5 = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("MD5").digest(text.toByteArray()))
    return listOf(md5.substring(0, 8), md5.substring(8, 12), md5.substring(12, 16), md5.substring(16, 20), md5.substring(20))
        .joinToString("-")
}
