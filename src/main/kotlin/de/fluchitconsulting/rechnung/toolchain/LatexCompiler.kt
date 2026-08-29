package de.fluchitconsulting.rechnung.toolchain

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Setzt eine .tex-Datei mit LuaLaTeX zu einem PDF/A.
 *
 * Die .xmpdata-Datei muss neben der .tex liegen und denselben Namen tragen –
 * daraus baut das Paket pdfx die XMP-Metadaten des PDF/A-Dokuments.
 */
object LatexCompiler {
    private const val PROGRAM = "lualatex"

    // Mehr als zwei Läufe braucht dieses Dokument nicht: Es hat weder
    // Inhaltsverzeichnis noch Querverweise. Der zweite Lauf ist nötig, weil
    // hyperref die .out-Datei erst im ersten schreibt ("Rerun to get outlines
    // right") – nachgemessen unterscheiden sich die PDFs beider Läufe.
    private const val MAX_PASSES = 3

    fun compile(
        workingDir: Path,
        jobName: String,
        texSource: String,
        xmpMetadata: String,
    ): Path {
        workingDir.resolve("$jobName.tex").writeText(texSource)
        workingDir.resolve("$jobName.xmpdata").writeText(xmpMetadata)

        repeat(MAX_PASSES) { pass ->
            val result =
                ExternalTool.run(
                    listOf(PROGRAM, "-interaction=nonstopmode", "-halt-on-error", "$jobName.tex"),
                    workingDir,
                )
            val log = workingDir.resolve("$jobName.log").let { if (it.exists()) it.readText() else result.output }
            if (result.failed) throw ToolchainException(errorReport(jobName, log))
            if (!needsAnotherPass(log)) return pdfOrFail(workingDir, jobName)
        }
        // Nach MAX_PASSES nicht mehr weiterlaufen: Verlangt das Dokument dann
        // immer noch einen weiteren Lauf, konvergiert es nicht, und ein vierter
        // Versuch würde daran nichts ändern.
        return pdfOrFail(workingDir, jobName)
    }

    private fun pdfOrFail(workingDir: Path, jobName: String): Path =
        workingDir.resolve("$jobName.pdf").also {
            if (!it.exists()) throw ToolchainException("$PROGRAM meldete Erfolg, hat aber kein PDF geschrieben: $it")
        }

    private fun needsAnotherPass(log: String): Boolean =
        log.lineSequence().any { it.contains("Rerun to get") || it.contains("Please rerun") }

    /**
     * Zieht die Fehlerzeilen aus dem LaTeX-Log. LuaLaTeX schreibt gut tausend
     * Zeilen Paketgeplauder; die Meldungen, auf die es ankommt, beginnen mit "!".
     * Die zwei Folgezeilen kommen mit, weil dort meist die Fundstelle steht.
     */
    private fun errorReport(jobName: String, log: String): String {
        val lines = log.lines()
        val relevant =
            lines
                .withIndex()
                .filter { (_, line) -> line.startsWith("!") }
                .flatMap { (index, _) -> lines.subList(index, minOf(index + 3, lines.size)) }
                .distinct()
        val details = if (relevant.isEmpty()) log.takeLast(800) else relevant.joinToString("\n")
        return "$PROGRAM konnte $jobName.tex nicht übersetzen:\n\n$details"
    }
}
