package de.fluchitconsulting.rechnungsgenerator.toolchain.process

import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.readText

/** Ein Lauf von `lualatex`: ob er scheiterte, und das Protokoll, das er hinterließ. */
data class LuaLatexRun(
    val failed: Boolean,
    val log: String,
)

/** Das Programm `lualatex`, ein Lauf je Aufruf. Wie viele es braucht und was das Protokoll sagt, weiß der Aufrufer. */
class LuaLatex(private val runner: ProcessRunner) {
    fun checkAvailable() = runner.requireAll(PROGRAM to "TeX Live, siehe https://tug.org/texlive/")

    /** Setzt `[jobName].tex` aus [workingDir]; [trailerId] und [creationDate] ersetzen, was LuaTeX sonst aus Pfad und Uhr nähme. */
    fun run(
        workingDir: Path,
        jobName: String,
        trailerId: String,
        creationDate: Instant,
    ): LuaLatexRun {
        val params =
            listOf(
                "-interaction=nonstopmode",
                "-halt-on-error",
                "-jobname=$jobName",
                "\\pdfvariable trailerid {[ <$trailerId> <$trailerId> ]}\\input{$jobName.tex}",
            )
        val environment = mapOf("SOURCE_DATE_EPOCH" to creationDate.epochSecond.toString(), "TZ" to "UTC")
        val result = runner.run(PROGRAM, params, workingDir, environment = environment)
        return LuaLatexRun(result.failed, readLog(workingDir.resolve("$jobName.log")) ?: result.output)
    }

    /**
     * `null`, wenn LuaLaTeX abstürzte, bevor es eines anlegte; dann bleibt die Prozessausgabe. Unlesbar ist es ein
     * Fehler des Werkzeugs (#336). Eine FileSystemException nennt in message nur den Pfad, den Grund in reason.
     */
    private fun readLog(file: Path): String? {
        if (!file.exists()) return null
        return try {
            file.readText()
        } catch (e: IOException) {
            val reason = if (e is FileSystemException) e.reason else e.message
            throw ToolchainException("Das Protokoll $file von $PROGRAM ließ sich nicht lesen" + (reason?.let { ": $it" } ?: "."), e)
        }
    }

    companion object {
        const val PROGRAM = "lualatex"
    }
}
