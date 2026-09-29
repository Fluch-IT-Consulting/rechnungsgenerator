package de.fluchitconsulting.rechnungsgenerator.generate

import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Nicht in `toolchain`: Fehlt der Viewer, entsteht die Rechnung trotzdem, deshalb wirft [open] nicht (#144). Im
 * Test steht ein Fake dahinter, sonst ginge bei jedem Build ein Fenster auf.
 */
interface PdfViewer {
    fun open(pdf: Path): ViewerResult
}

sealed interface ViewerResult {
    data object Opened : ViewerResult

    data class Failed(val reason: String) : ViewerResult
}

/**
 * Über [ProcessBuilder] und nicht `ProcessRunner`, der bis zum Ende wartet und hart abräumt: Manche Linux-Desktops
 * lassen `xdg-open` laufen, bis der Viewer zu ist, und abgeräumt risse er ihn mit.
 */
object SystemPdfViewer : PdfViewer {
    // Nur so fällt „kein Programm für PDF eingerichtet“ auf; `open` kehrt auf macOS sofort zurück.
    private const val WAIT_SECONDS = 5L

    override fun open(pdf: Path): ViewerResult {
        val osName = System.getProperty("os.name")
        val command =
            commandFor(osName, pdf.toAbsolutePath())
                ?: return ViewerResult.Failed("Öffnen wird auf diesem System ($osName) nicht unterstützt.")

        val process =
            try {
                // Verworfen statt gelesen: Ein Puffer, den niemand leert, könnte den Prozess blockieren.
                ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            } catch (e: IOException) {
                return ViewerResult.Failed("${command.first()} ließ sich nicht starten: ${e.message}")
            }

        // Läuft er noch, wartet er auf den offenen Viewer und bleibt stehen.
        if (!process.waitFor(WAIT_SECONDS, TimeUnit.SECONDS)) return ViewerResult.Opened
        return if (process.exitValue() == 0) {
            ViewerResult.Opened
        } else {
            ViewerResult.Failed("${command.first()} endete mit Exitcode ${process.exitValue()}.")
        }
    }

    /** Windows fehlt absichtlich: LuaLaTeX und veraPDF liefen dort nie, ungetesteter Code veraltete unbemerkt. */
    internal fun commandFor(osName: String, pdf: Path): List<String>? =
        when {
            osName.startsWith("Mac") -> listOf("open", pdf.toString())
            osName.startsWith("Linux") -> listOf("xdg-open", pdf.toString())
            else -> null
        }
}
