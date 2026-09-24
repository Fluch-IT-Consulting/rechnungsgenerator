package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import de.fluchitconsulting.rechnungsgenerator.core.accepted.accepted
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.generate.render.InvoiceRenderer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.Tag
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.Enabled
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
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
private val mitVerapdf =
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
                val eigenesVerzeichnis = createTempDirectory(workDir, "jobname")
                String(LatexCompiler(ExternalTool).compile(eigenesVerzeichnis, jobName, sources).readBytes(), Charsets.ISO_8859_1)
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
        val stammdaten =
            workDir.resolve("stammdaten-logo.yaml").also {
                it.writeText(Path.of("src/test/resources/stammdaten.yaml").readText() + "design: raster\nlogo: $logo\n")
            }

        val (erster, _) = run.erzeuge("rechnung", "logo-1", stammdaten = stammdaten)
        val (zweiter, _) = run.erzeuge("rechnung", "logo-2", stammdaten = stammdaten)

        erster.readBytes().contentEquals(zweiter.readBytes()) shouldBe true
        run.textOf(erster) shouldContain "MUSTERIT"
    }

    /**
     * Eine Rechnung hat keine Lesezeichen, und das PDF sagt das auch (#321):
     * Mit Lesezeichen verlangte hyperref ohne Not einen weiteren Lauf, und
     * `/UseOutlines` öffnete im Viewer eine leere Seitenleiste. Beide Designs,
     * weil jede Vorlage pdfx selbst lädt.
     */
    test("das PDF hat keine Lesezeichen und öffnet keine Lesezeichenleiste") {
        val raster =
            workDir.resolve("stammdaten-raster.yaml").also {
                it.writeText(Path.of("src/test/resources/stammdaten.yaml").readText() + "design: raster\n")
            }
        val pdfs =
            listOf(
                run.erzeuge("rechnung", "lesezeichen-balken").first,
                run.erzeuge("rechnung", "lesezeichen-raster", stammdaten = raster).first,
            )

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
        val skaliert = Path.of("src/test/resources/logo-skaliert.tex")
        val ohneFaktor =
            workDir.resolve("logo-ohne-faktor.tex").also { file ->
                file.writeText(skaliert.readLines().filterNot { "logoskalierung" in it }.joinToString("\n"))
            }

        fun logoHoehe(logo: Path): Int {
            val stammdaten =
                workDir.resolve("stammdaten-${logo.fileName}.yaml").also {
                    it.writeText(
                        Path.of("src/test/resources/stammdaten.yaml").readText() +
                            "design: raster\nlogo: ${logo.toAbsolutePath()}\n",
                    )
                }
            val (pdf, ausgabe) = run.erzeuge("rechnung", "hoehe-${logo.fileName}", stammdaten = stammdaten)
            withClue(ausgabe.joinToString("\n")) { ausgabe shouldContain "PDF/A-1b: gültig" }

            val bild = run.imageOf(pdf, dpi = 300)
            return (0 until bild.height / 3).count { y ->
                (0 until bild.width).any { x ->
                    val rgb = bild.getRGB(x, y)
                    val rot = (rgb shr 16) and 0xFF
                    val blau = rgb and 0xFF
                    blau - rot > 80
                }
            }
        }

        val mitFaktor = logoHoehe(skaliert)
        val ohne = logoHoehe(ohneFaktor)

        withClue("$mitFaktor und $ohne Pixel") { mitFaktor.toDouble() / ohne shouldBe (1.2 plusOrMinus 0.03) }
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
        .config(enabledOrReasonIf = { mitVerapdf }) {
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
        .config(enabledOrReasonIf = { mitVerapdf }) {
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
        .config(enabledOrReasonIf = { mitVerapdf }) {
            val (gueltig, _) = run.erzeuge("rechnung", zielname = "zwei-pruefer")
            // Ins Arbeitsverzeichnis kopiert: Der externe Prüfer legt seine
            // Ausgabedatei neben das PDF, und das wäre hier der Testbestand.
            val nichtKonform =
                Path.of("src/test/resources/nicht-konform.pdf").copyTo(workDir.resolve("nicht-konform.pdf"), overwrite = true)

            for (pdf in listOf(gueltig, nichtKonform)) {
                val eingebaut = BuiltInPdfAValidator.validate(pdf)
                val extern = ExternalPdfAValidator(ExternalTool).validate(pdf)

                // Die Kennung als eine Zeichenkette: So steht im Fehlerfall
                // "6.3.4-1" im Bericht und nicht ein Paar.
                fun regeln(result: ValidationResult) =
                    result.violations.map { "${it.clause}-${it.testNumber}" }.sorted()

                withClue("$pdf: Urteil") { eingebaut.valid shouldBe extern.valid }
                withClue("$pdf: Regeln") { regeln(eingebaut) shouldBe regeln(extern) }
            }

            val keinPdf =
                Path.of("src/test/resources/kein-pdf.pdf").copyTo(workDir.resolve("kein-pdf.pdf"), overwrite = true)
            withClue("eingebaut") { shouldThrow<ToolchainException> { BuiltInPdfAValidator.validate(keinPdf) } }
            withClue("extern") { shouldThrow<ToolchainException> { ExternalPdfAValidator(ExternalTool).validate(keinPdf) } }
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
                    sources =
                        PdfSources(
                            tex = "\\documentclass{article}\\begin{document}\\gibtesnicht\\end{document}",
                            xmpMetadata = "",
                            creationDate = LocalDate.of(2026, 1, 1),
                        ),
                )
            }

        fehler.message!! shouldContain "gibtesnicht"
    }
})

/** Die UUID, wie pdfx sie aus einem Text bildet: dessen MD5-Summe, gruppiert 8-4-4-4-12. */
private fun pdfxUuid(text: String): String {
    val md5 = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("MD5").digest(text.toByteArray()))
    return listOf(md5.substring(0, 8), md5.substring(8, 12), md5.substring(12, 16), md5.substring(16, 20), md5.substring(20))
        .joinToString("-")
}
