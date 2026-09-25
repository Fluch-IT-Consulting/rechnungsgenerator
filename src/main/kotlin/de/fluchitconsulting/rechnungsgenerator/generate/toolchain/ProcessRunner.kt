package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Ein fremdes Programm laufen lassen – die Naht zum Betriebssystem.
 *
 * Sie liegt eine Etage unter [Typesetter]: Dort wird entschieden, ob überhaupt
 * gesetzt und geprüft wird, hier, womit. Dazwischen liegt das, was tatsächlich
 * schiefgehen kann – ein Exitcode, der gedeutet werden will, ein Protokoll, das
 * gelesen wird, eine Schleife, die abbricht. Ohne diese Naht wäre all das nur
 * mit echtem LuaLaTeX und veraPDF erreichbar.
 *
 * Zwei Adapter erfüllen sie, und deshalb ist sie eine echte und keine gedachte:
 * [ExternalTool] startet den Prozess, `FakeProcessRunner` im Test spielt einen
 * aufgezeichneten Lauf nach.
 *
 * Was ein Aufrufer wissen muss, steht am Adapter [ExternalTool] und gilt für
 * jeden weiteren: stdout und stderr kommen getrennt zurück (#227), die Ausgabe
 * geht über Dateien und nicht über Streams, und nach [timeout] wird der
 * Prozess hart abgeräumt statt höflich gebeten. [environment] kommt zur
 * geerbten Umgebung hinzu, statt sie zu ersetzen.
 */
interface ProcessRunner {
    fun run(
        program: String,
        params: List<String>,
        workingDir: Path,
        timeout: Duration = 2.minutes,
        environment: Map<String, String> = emptyMap(),
    ): ToolResult

    /**
     * Ist dieses Programm da?
     *
     * Der zweite Begriff der Naht (#245): Wer ein Werkzeug aufruft, fragt
     * vorher, ob es überhaupt zu finden ist – und zwar dort, wo auch der Aufruf
     * hingeht. Ein fester Aufruf am Adapter vorbei hinge sonst daran, was auf
     * der Maschine installiert ist, auf der der Test gerade läuft.
     */
    fun canRun(program: String): Boolean
}

/**
 * Prüft, ob alle genannten Programme da sind, und meldet die fehlenden in einer
 * Meldung, die sagt, was zu tun ist. Wird beim Start aufgerufen – ein Abbruch
 * mitten im Satz wäre schwerer zu deuten.
 *
 * Eine Erweiterung und keine Methode der Naht: Gefragt wird über [canRun], also
 * über dieselbe Naht wie der Aufruf, aber der Wortlaut gehört zum Vertrag und
 * nicht zum Adapter – kein Adapter soll ihn abwandeln.
 */
fun ProcessRunner.requireAll(vararg tools: Pair<String, String>) {
    val missing = tools.filter { (name, _) -> !canRun(name) }
    if (missing.isNotEmpty()) {
        val list = missing.joinToString("\n") { (name, hint) -> "  - $name ($hint)" }
        throw ToolchainException("Diese Programme fehlen im PATH:\n$list")
    }
}
