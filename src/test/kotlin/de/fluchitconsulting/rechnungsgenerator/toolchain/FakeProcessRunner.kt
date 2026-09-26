package de.fluchitconsulting.rechnungsgenerator.toolchain

import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.time.Duration

/**
 * Spielt einen aufgezeichneten Programmlauf nach.
 *
 * Er benimmt sich wie das Werkzeug und nicht wie ein Rückgabewert: Ein Lauf
 * hinterlässt die Dateien, die LuaLaTeX hinterlassen würde. Das ist der Grund,
 * warum `LatexCompiler` fürs Testen nicht umgebaut werden musste – das
 * Protokoll ist eine Datei, und dass bei einem harten Abbruch keine entsteht,
 * ist ein eigener Fall, den nur ein Adapter nachstellen kann, der Dateien
 * schreiben *darf*.
 *
 * Die Läufe werden der Reihe nach abgearbeitet. Dass es mehrere sein können,
 * ist keine Bequemlichkeit: LuaLaTeX läuft, solange sein Protokoll einen
 * weiteren Lauf verlangt, und diese Schleife lässt sich nur prüfen, wenn der
 * zweite Lauf etwas anderes antwortet als der erste.
 */
class FakeProcessRunner(
    vararg runs: Run,
    private val missing: Set<String> = emptySet(),
) : ProcessRunner {
    /**
     * Ein vorbereiteter Lauf. [files] sind Name und Inhalt dessen, was er im
     * Arbeitsverzeichnis hinterlässt.
     */
    data class Run(
        val exitCode: Int = 0,
        val output: String = "",
        val errorOutput: String = "",
        val files: Map<String, String> = emptyMap(),
    )

    private val runs = runs.toList()

    /** Womit gerufen wurde – sonst ließe sich nicht prüfen, wie oft gelaufen wurde. */
    val commands = mutableListOf<List<String>>()

    /** Mit welcher Umgebung gerufen wurde, je Lauf. */
    val environments = mutableListOf<Map<String, String>>()

    /**
     * Da ist, was nicht in [missing] steht – vorgegeben ist also der Rechner,
     * auf dem alles installiert ist. Der andere Fall wird ausdrücklich genannt
     * und hängt damit nicht daran, was auf der Testmaschine liegt (#245).
     */
    override fun canRun(program: String) = program !in missing

    override fun run(
        program: String,
        params: List<String>,
        workingDir: Path,
        timeout: Duration,
        environment: Map<String, String>,
    ): ToolResult {
        val run =
            runs.getOrNull(commands.size)
                ?: error("Es war ${runs.size} Lauf/Läufe vorbereitet, das Modul ruft den ${commands.size + 1}.")
        commands += listOf(program) + params
        environments += environment
        run.files.forEach { (name, content) -> workingDir.resolve(name).writeText(content) }
        return ToolResult(run.exitCode, run.output, run.errorOutput)
    }
}
