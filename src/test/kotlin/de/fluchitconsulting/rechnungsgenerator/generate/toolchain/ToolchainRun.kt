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

class ToolchainRun(
    private val workDir: Path,
) {
    // Über das Kommando, nicht die Einzelteile: Nur dort steht die Abfolge von
    // Rechnen, Setzen und Prüfen. Die Ausgabe tragen die Tests in ihre Meldung.
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

    fun textOf(pdf: Path): String {
        // In eine Datei, nicht in einen Stream, wie im ProcessRunner.
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

/** Alle Lagen in pt, von oben gemessen. */
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
