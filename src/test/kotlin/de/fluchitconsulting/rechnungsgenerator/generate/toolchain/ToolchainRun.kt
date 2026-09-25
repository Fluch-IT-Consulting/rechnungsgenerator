package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import de.fluchitconsulting.rechnungsgenerator.RechnungsgeneratorCommand
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.io.path.readText

/**
 * Läufe mit den echten Werkzeugen, geteilt von `ToolchainIntegrationTest` und
 * `PflichtangabenTest`. Alles landet in [workDir].
 */
class ToolchainRun(
    private val workDir: Path,
) {
    /**
     * Erzeugt eine Rechnung mit den echten Werkzeugen – über dasselbe Kommando,
     * das auch ein Mensch aufruft.
     *
     * Nicht über die Einzelteile: Rechnen, Setzen und Prüfen stehen genau
     * einmal zusammen, nämlich im Kommando. Baute ein Test sie noch einmal
     * nach, gäbe es die Abfolge an zwei Stellen, und die zweite wäre die, die
     * niemand pflegt.
     *
     * Die Ausgaben werden eingesammelt, damit die Meldung von veraPDF im
     * Testbericht landet – sonst stünde dort nur "expected true but was false".
     */
    fun generate(
        example: String,
        targetName: String = example,
        input: Path = Path.of("src/test/resources/$example.yaml"),
        sellerConfig: Path = Path.of("src/test/resources/stammdaten.yaml"),
    ): Pair<Path, List<String>> {
        val target = workDir.resolve("$targetName.pdf")
        val output = mutableListOf<String>()
        val command = RechnungsgeneratorCommand()
        command.context { echoMessage = { _, message, _, _ -> output += message.toString() } }

        val error =
            runCatching {
                command.parse(
                    arrayOf("erzeuge", input.toString(), "-s", sellerConfig.toString(), "-o", target.toString()),
                )
            }.exceptionOrNull()

        withClue(output.joinToString("\n")) { error shouldBe null }
        return target to output
    }

    /**
     * Der Text eines PDF, wie `pdftotext` ihn liest – für die Frage, ob etwas
     * wirklich so gesetzt wurde, wie es in der Eingabe steht.
     */
    fun textOf(pdf: Path): String {
        // Die Ausgabe in eine Datei, nicht in einen Stream – aus demselben
        // Grund wie bei den Werkzeugen des Programms (siehe ProcessRunner).
        val text = workDir.resolve("${pdf.fileName}.txt")
        val process =
            ProcessBuilder("pdftotext", "-enc", "UTF-8", pdf.toString(), text.toString())
                .redirectErrorStream(true)
                .redirectOutput(workDir.resolve("pdftotext.log").toFile())
                .start()
        process.waitFor(30, TimeUnit.SECONDS) shouldBe true
        process.exitValue() shouldBe 0
        return text.readText()
    }

    /**
     * Die erste Seite eines PDF als Bild, wie `pdftoppm` sie rastert – für die
     * Frage, wie groß etwas gesetzt wurde, die kein Text beantwortet.
     */
    fun imageOf(
        pdf: Path,
        dpi: Int = 100,
    ): BufferedImage {
        val prefix = workDir.resolve(pdf.fileName.toString().removeSuffix(".pdf"))
        val process =
            ProcessBuilder("pdftoppm", "-png", "-singlefile", "-r", dpi.toString(), pdf.toString(), prefix.toString())
                .redirectErrorStream(true)
                .redirectOutput(workDir.resolve("pdftoppm.log").toFile())
                .start()
        process.waitFor(30, TimeUnit.SECONDS) shouldBe true
        process.exitValue() shouldBe 0
        return ImageIO.read(workDir.resolve("${prefix.fileName}.png").toFile())
    }

    /**
     * Die Wörter der ersten Seite mit ihrer Lage, wie `pdftotext -bbox` sie
     * liest – für die Frage, wie weit zwei Wörter auseinanderstehen, die weder
     * der Text noch ein Bild ohne Zuordnung beantwortet.
     */
    fun wordsOf(pdf: Path): PlacedPage {
        val html = workDir.resolve("${pdf.fileName}.html")
        val process =
            ProcessBuilder("pdftotext", "-bbox", "-enc", "UTF-8", "-l", "1", pdf.toString(), html.toString())
                .redirectErrorStream(true)
                .redirectOutput(workDir.resolve("pdftotext.log").toFile())
                .start()
        process.waitFor(30, TimeUnit.SECONDS) shouldBe true
        process.exitValue() shouldBe 0

        val text = html.readText()
        val height = Regex("""<page width="[\d.]+" height="([\d.]+)">""").find(text)!!.groupValues[1].toDouble()
        val words =
            Regex("""<word xMin="([\d.]+)" yMin="([\d.]+)" xMax="([\d.]+)" yMax="[\d.]+">([^<]*)</word>""")
                .findAll(text)
                .map { match ->
                    val (xMin, yMin, xMax, word) = match.destructured
                    PlacedWord(word, xMin.toDouble(), xMax.toDouble(), yMin.toDouble())
                }.toList()
        return PlacedPage(height, words)
    }
}

/** Eine Seite als Wörter in Lesereihenfolge; [height] und alle Lagen in pt, von oben gemessen. */
class PlacedPage(
    val height: Double,
    val words: List<PlacedWord>,
)

data class PlacedWord(
    val text: String,
    val xMin: Double,
    val xMax: Double,
    val yMin: Double,
)
