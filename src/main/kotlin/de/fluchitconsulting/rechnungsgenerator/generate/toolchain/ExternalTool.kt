package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isExecutable
import kotlin.io.path.readText
import kotlin.time.Duration

/** Wird geworfen, wenn ein externes Werkzeug fehlt, abstürzt oder hängen bleibt. */
class ToolchainException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Was ein Programmlauf hinterlassen hat.
 *
 * stdout und stderr kommen getrennt zurück (#227). Für die meisten Aufrufer
 * ist das gleichgültig – sie nehmen [output] und bekommen beides. Für einen
 * ist es entscheidend: `verapdf` schreibt seinen Bericht auf stdout und sein
 * Protokoll auf stderr, und zusammengeführt landen Log-Zeilen mitten im XML.
 *
 * [output] hängt die Kanäle aneinander, statt sie zu verschränken. Die
 * Reihenfolge zwischen ihnen geht damit verloren; innerhalb eines Kanals
 * bleibt sie. Für den Zweck – ein Protokoll in eine Fehlermeldung legen –
 * genügt das.
 */
data class ToolResult(
    val exitCode: Int,
    val standardOutput: String,
    val errorOutput: String = "",
) {
    val failed: Boolean get() = exitCode != 0

    /** Beide Kanäle, für Meldungen und für Protokolle, die alles wollen. */
    val output: String get() = standardOutput + errorOutput
}

/**
 * Dünne Hülle um [ProcessBuilder] für die beiden externen Werkzeuge.
 *
 * Die Ausgabe wird in eine Datei umgeleitet statt in einen Stream gelesen. Das
 * klingt umständlich, erspart aber einen Lesethread: Würde man den Stream im
 * selben Thread lesen und danach auf das Ende warten, blockiert das Lesen bei
 * einem hängenden Prozess für immer – die Zeitgrenze käme nie zum Zug.
 *
 * Zwei Dateien, weil stdout und stderr Verschiedenes bedeuten können (#227).
 */
object ExternalTool : ProcessRunner {
    // Verzeichnisse, in denen die Werkzeuge typischerweise liegen, falls die JVM
    // einen kürzeren PATH geerbt hat als die Shell (häufig bei GUI-gestarteten IDEs).
    private val EXTRA_PATHS = listOf("/Library/TeX/texbin", "/usr/local/bin", "/opt/homebrew/bin")

    /** Sucht ein Programm im PATH. `null`, wenn es nirgends gefunden wird. */
    fun locate(name: String): Path? {
        val fromPath = System.getenv("PATH").orEmpty().split(java.io.File.pathSeparator)
        return (fromPath + EXTRA_PATHS)
            .asSequence()
            .filter { it.isNotBlank() }
            .map { Path.of(it, name) }
            .firstOrNull { it.isExecutable() }
    }

    /**
     * Prüft, ob alle genannten Werkzeuge vorhanden sind, und meldet die fehlenden
     * in einer Meldung, die sagt, was zu tun ist. Wird beim Start aufgerufen –
     * ein Abbruch mitten im Satz wäre schwerer zu deuten.
     */
    fun requireAll(vararg tools: Pair<String, String>) {
        val missing = tools.filter { (name, _) -> locate(name) == null }
        if (missing.isNotEmpty()) {
            val list = missing.joinToString("\n") { (name, hint) -> "  - $name ($hint)" }
            throw ToolchainException("Diese Programme fehlen im PATH:\n$list")
        }
    }

    override fun run(
        command: List<String>,
        workingDir: Path,
        timeout: Duration,
        environment: Map<String, String>,
    ): ToolResult {
        val program = command.first()
        val executable = locate(program) ?: throw ToolchainException("Programm nicht gefunden: $program")
        val outFile = Files.createTempFile(workingDir, "tool-", ".out")
        val errFile = Files.createTempFile(workingDir, "tool-", ".err")
        return try {
            val process =
                ProcessBuilder(listOf(executable.toString()) + command.drop(1))
                    .directory(workingDir.toFile())
                    .redirectOutput(outFile.toFile())
                    .redirectError(errFile.toFile())
                    // environment() liefert die geerbte Umgebung als veränderbare
                    // Map; was hier hineinkommt, sieht nur dieser Prozess.
                    .also { it.environment().putAll(environment) }
                    .start()
            if (!process.waitFor(timeout.inWholeSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw ToolchainException("$program hat nach $timeout nicht geantwortet und wurde abgebrochen.")
            }
            ToolResult(process.exitValue(), outFile.readText(), errFile.readText())
        } finally {
            outFile.deleteIfExists()
            errFile.deleteIfExists()
        }
    }
}
