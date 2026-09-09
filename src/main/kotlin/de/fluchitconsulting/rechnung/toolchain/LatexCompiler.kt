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

    // Die Obergrenze, nicht die Anzahl: Gelaufen wird, solange LaTeX einen
    // weiteren Lauf verlangt – siehe LatexLog.needsAnotherPass. Bei den
    // Rechnungen dieses Projekts ist das heute keiner; sie haben weder
    // Querverweise noch Lesezeichen, und nachgemessen mit TeX Live 2026 sind
    // die PDFs nach einem und nach zwei Läufen im Satzbild gleich.
    //
    // Drei als Grenze, weil ein Dokument, das nach dreien immer noch einen
    // weiteren verlangt, nicht konvergiert – ein vierter änderte daran nichts.
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
            if (result.failed) throw ToolchainException(LatexLog.errorReport(jobName, log))
            if (!LatexLog.needsAnotherPass(log)) return pdfOrFail(workingDir, jobName)
        }
        return pdfOrFail(workingDir, jobName)
    }

    private fun pdfOrFail(workingDir: Path, jobName: String): Path =
        workingDir.resolve("$jobName.pdf").also {
            if (!it.exists()) throw ToolchainException("$PROGRAM meldete Erfolg, hat aber kein PDF geschrieben: $it")
        }
}
