package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import de.fluchitconsulting.rechnungsgenerator.RechnungsgeneratorCommand
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import java.util.concurrent.TimeUnit
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
    fun erzeuge(
        rechnung: String,
        zielname: String = rechnung,
        eingabe: Path = Path.of("src/test/resources/$rechnung.yaml"),
        stammdaten: Path = Path.of("src/test/resources/stammdaten.yaml"),
    ): Pair<Path, List<String>> {
        val ziel = workDir.resolve("$zielname.pdf")
        val ausgabe = mutableListOf<String>()
        val command = RechnungsgeneratorCommand()
        command.context { echoMessage = { _, message, _, _ -> ausgabe += message.toString() } }

        val fehler =
            runCatching {
                command.parse(
                    arrayOf("erzeuge", eingabe.toString(), "-s", stammdaten.toString(), "-o", ziel.toString()),
                )
            }.exceptionOrNull()

        withClue(ausgabe.joinToString("\n")) { fehler shouldBe null }
        return ziel to ausgabe
    }

    /**
     * Der Text eines PDF, wie `pdftotext` ihn liest – für die Frage, ob etwas
     * wirklich so gesetzt wurde, wie es in der Eingabe steht.
     */
    fun textOf(pdf: Path): String {
        // Die Ausgabe in eine Datei, nicht in einen Stream – aus demselben
        // Grund wie bei den Werkzeugen des Programms (siehe ProcessRunner).
        val text = workDir.resolve("${pdf.fileName}.txt")
        val prozess =
            ProcessBuilder("pdftotext", "-enc", "UTF-8", pdf.toString(), text.toString())
                .redirectErrorStream(true)
                .redirectOutput(workDir.resolve("pdftotext.log").toFile())
                .start()
        prozess.waitFor(30, TimeUnit.SECONDS) shouldBe true
        prozess.exitValue() shouldBe 0
        return text.readText()
    }
}
