package de.fluchitconsulting.rechnungsgenerator.toolchain.process

import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.time.Duration

/**
 * Hinterlässt die Dateien, die das Werkzeug hinterlassen würde, denn
 * `LatexCompiler` liest sein Protokoll von der Platte. Die Läufe gehen der
 * Reihe nach, damit ein zweiter anders antworten kann als der erste.
 */
class FakeProcessRunner(
    vararg runs: Run,
    private val missing: Set<String> = emptySet(),
) : ProcessRunner {
    data class Run(
        val exitCode: Int = 0,
        val output: String = "",
        val errorOutput: String = "",
        val files: Map<String, String> = emptyMap(),
    )

    private val runs = runs.toList()

    val commands = mutableListOf<List<String>>()

    val environments = mutableListOf<Map<String, String>>()

    // Nie die Testmaschine fragen (#245).
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
