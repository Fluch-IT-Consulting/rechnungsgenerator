package de.fluchitconsulting.rechnung.toolchain

import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Ein fremdes Programm laufen lassen – die Naht zum Betriebssystem.
 *
 * Sie liegt eine Etage unter [Typesetter]: Dort wird entschieden, ob überhaupt
 * gesetzt und geprüft wird, hier, womit. Dazwischen liegt das, was tatsächlich
 * schiefgehen kann – ein Exitcode, der gedeutet werden will, ein Protokoll, das
 * gelesen wird, eine Schleife, die abbricht. Solange diese Naht fehlte, war all
 * das nur mit echtem LuaLaTeX und veraPDF erreichbar.
 *
 * Zwei Adapter erfüllen sie, und deshalb ist sie eine echte und keine gedachte:
 * [ExternalTool] startet den Prozess, `FakeProcessRunner` im Test spielt einen
 * aufgezeichneten Lauf nach.
 *
 * Was ein Aufrufer wissen muss, steht am Adapter [ExternalTool] und gilt für
 * jeden weiteren: stderr läuft in dieselbe Ausgabe wie stdout, die Ausgabe geht
 * über eine Datei und nicht über einen Stream, und nach [timeout] wird der
 * Prozess hart abgeräumt statt höflich gebeten. [environment] kommt zur
 * geerbten Umgebung hinzu, statt sie zu ersetzen.
 */
interface ProcessRunner {
    fun run(
        command: List<String>,
        workingDir: Path,
        timeout: Duration = 2.minutes,
        environment: Map<String, String> = emptyMap(),
    ): ToolResult
}
