package de.fluchitconsulting.rechnungsgenerator.toolchain.process

import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isExecutable
import kotlin.io.path.readText
import kotlin.time.Duration

/** stdout und stderr getrennt: `verapdf` schreibt den Bericht auf stdout und sein Protokoll auf stderr (#227). */
data class ToolResult(
    val exitCode: Int,
    val standardOutput: String,
    val errorOutput: String = "",
) {
    val failed: Boolean get() = exitCode != 0

    /** Beide Kanäle nacheinander. Der Umbruch an der Nahtstelle verhindert eine Zeile aus zwei Quellen (#246). */
    val output: String
        get() =
            when {
                standardOutput.isEmpty() || errorOutput.isEmpty() -> standardOutput + errorOutput
                standardOutput.endsWith("\n") -> standardOutput + errorOutput
                else -> standardOutput + "\n" + errorOutput
            }
}

/**
 * Ausgabe in Dateien statt Streams: Das Lesen im selben Thread blockierte bei einem hängenden Prozess, und die
 * Zeitgrenze käme nie zum Zug.
 */
object ExternalTool : ProcessRunner {
    // Falls die JVM einen kürzeren PATH geerbt hat als die Shell, etwa in einer IDE.
    private val EXTRA_PATHS = listOf("/Library/TeX/texbin", "/usr/local/bin", "/opt/homebrew/bin")

    private fun locate(name: String): Path? {
        val fromPath = System.getenv("PATH").orEmpty().split(java.io.File.pathSeparator)
        return (fromPath + EXTRA_PATHS)
            .asSequence()
            .filter { it.isNotBlank() }
            .map { Path.of(it, name) }
            .firstOrNull { it.isExecutable() }
    }

    override fun canRun(program: String) = locate(program) != null

    override fun run(
        program: String,
        params: List<String>,
        workingDir: Path,
        timeout: Duration,
        environment: Map<String, String>,
    ): ToolResult {
        val executable = locate(program) ?: throw ToolchainException("Programm nicht gefunden: $program")
        // Eine IOException heißt hier, der Lauf kam nicht zustande: ein Fehler des Werkzeugs (#300).
        return try {
            runLocated(executable, params, workingDir, timeout, environment)
        } catch (e: IOException) {
            throw ToolchainException("$program ließ sich nicht ausführen: ${e.message}", e)
        }
    }

    private fun runLocated(
        executable: Path,
        params: List<String>,
        workingDir: Path,
        timeout: Duration,
        environment: Map<String, String>,
    ): ToolResult {
        val outFile = Files.createTempFile(workingDir, "tool-", ".out")
        val errFile = Files.createTempFile(workingDir, "tool-", ".err")
        return try {
            val process =
                ProcessBuilder(listOf(executable.toString()) + params)
                    .directory(workingDir.toFile())
                    .redirectOutput(outFile.toFile())
                    .redirectError(errFile.toFile())
                    .also { it.environment().putAll(environment) }
                    .start()
            if (!process.waitFor(timeout.inWholeSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw ToolchainException("${executable.fileName} hat nach $timeout nicht geantwortet und wurde abgebrochen.")
            }
            ToolResult(process.exitValue(), outFile.readText(), errFile.readText())
        } finally {
            outFile.deleteIfExists()
            errFile.deleteIfExists()
        }
    }
}
