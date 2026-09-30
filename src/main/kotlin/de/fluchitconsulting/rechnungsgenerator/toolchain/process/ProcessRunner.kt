package de.fluchitconsulting.rechnungsgenerator.toolchain.process

import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Die Naht zum Betriebssystem, eine Etage unter `Typesetter`; im Test spielt `FakeProcessRunner` einen Lauf nach.
 * Nach [timeout] wird hart abgeräumt, [environment] ergänzt die geerbte Umgebung, statt sie zu ersetzen.
 */
interface ProcessRunner {
    fun run(
        program: String,
        params: List<String>,
        workingDir: Path,
        timeout: Duration = 2.minutes,
        environment: Map<String, String> = emptyMap(),
    ): ToolResult

    /** Über dieselbe Naht wie der Aufruf, sonst hinge ein Test daran, was installiert ist (#245). */
    fun canRun(program: String): Boolean
}

/** Eine Erweiterung und keine Methode der Naht: Kein Adapter soll den Wortlaut abwandeln. */
fun ProcessRunner.requireAll(vararg tools: Pair<String, String>) {
    val missing = tools.filter { (name, _) -> !canRun(name) }
    if (missing.isNotEmpty()) {
        val list = missing.joinToString("\n") { (name, hint) -> "  - $name ($hint)" }
        throw ToolchainException("Diese Programme fehlen im PATH:\n$list")
    }
}
