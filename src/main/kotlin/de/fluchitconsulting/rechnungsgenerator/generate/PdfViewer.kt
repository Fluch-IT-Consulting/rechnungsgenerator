package de.fluchitconsulting.rechnungsgenerator.generate

import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Öffnet ein fertiges PDF zum Ansehen – die Naht zum Viewer des Systems.
 *
 * Bewusst nicht in `toolchain`: Dort liegen die Werkzeuge, ohne die kein PDF
 * entsteht, und `checkAvailable()` prüft sie beim Start. Fehlt der Viewer,
 * entsteht die Rechnung trotzdem. Deshalb wirft [open] auch nicht, sondern sagt
 * im Ergebnis, ob es geklappt hat – ein Fehlschlag ist hier ein Hinweis, kein
 * Abbruch.
 *
 * Zwei Adapter: [SystemPdfViewer] im Betrieb, ein Fake im Test. Ohne die Naht
 * ginge bei jedem `./gradlew build` ein Fenster auf.
 */
interface PdfViewer {
    fun open(pdf: Path): ViewerResult
}

/** Ob sich das PDF öffnen ließ – und wenn nicht, warum. */
sealed interface ViewerResult {
    // `data object`: ein Singleton wie `object`, aber mit lesbarem toString()
    // ("Opened" statt "ViewerResult$Opened@1b2c3d") – praktisch in Testmeldungen.
    data object Opened : ViewerResult

    data class Failed(val reason: String) : ViewerResult
}

/**
 * Der Viewer, den das System für PDFs eingerichtet hat: `open` auf macOS,
 * `xdg-open` auf Linux.
 *
 * Gestartet wird über [ProcessBuilder] direkt und nicht über `ProcessRunner`:
 * Dessen Vertrag wartet bis zum Ende und räumt nach der Zeitgrenze hart ab.
 * Hier ist beides falsch. Manche Linux-Desktops lassen `xdg-open` laufen, bis
 * der Viewer geschlossen wird, und abgeräumt risse er den Viewer mit.
 */
object SystemPdfViewer : PdfViewer {
    // So lange wird auf den Exitcode gewartet. Nur so fällt "kein Programm für
    // PDF eingerichtet" auf; `open` kehrt auf macOS sofort zurück.
    private const val WAIT_SECONDS = 5L

    override fun open(pdf: Path): ViewerResult {
        val osName = System.getProperty("os.name")
        val command =
            commandFor(osName, pdf.toAbsolutePath())
                ?: return ViewerResult.Failed("Öffnen wird auf diesem System ($osName) nicht unterstützt.")

        val process =
            try {
                // Ausgabe verworfen statt gelesen: Ein Puffer, den niemand
                // leert, könnte den Prozess sonst blockieren.
                ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            } catch (e: IOException) {
                return ViewerResult.Failed("${command.first()} ließ sich nicht starten: ${e.message}")
            }

        // Läuft der Aufruf nach der Wartezeit noch, bleibt er stehen: Dann wartet
        // er auf den Viewer, und der ist offen.
        if (!process.waitFor(WAIT_SECONDS, TimeUnit.SECONDS)) return ViewerResult.Opened
        return if (process.exitValue() == 0) {
            ViewerResult.Opened
        } else {
            ViewerResult.Failed("${command.first()} endete mit Exitcode ${process.exitValue()}.")
        }
    }

    /**
     * Der Befehl zum Öffnen, je nach Betriebssystem – oder `null`, wo es keinen
     * gibt. Eine reine Funktion, damit die Wahl ohne Prozessstart prüfbar ist.
     *
     * Windows fehlt absichtlich: LuaLaTeX und veraPDF liefen dort nie, und Code
     * für ein System, auf dem niemand testet, veraltet unbemerkt.
     */
    internal fun commandFor(osName: String, pdf: Path): List<String>? =
        when {
            osName.startsWith("Mac") -> listOf("open", pdf.toString())
            osName.startsWith("Linux") -> listOf("xdg-open", pdf.toString())
            else -> null
        }
}
