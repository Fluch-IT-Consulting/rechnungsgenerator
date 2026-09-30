package de.fluchitconsulting.rechnungsgenerator.generate.typesetting

import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import de.fluchitconsulting.rechnungsgenerator.toolchain.process.LuaLatex
import de.fluchitconsulting.rechnungsgenerator.toolchain.process.LuaLatex.Companion.PROGRAM
import java.io.IOException
import java.nio.file.Path
import java.security.MessageDigest
import java.time.ZoneOffset
import java.util.HexFormat
import kotlin.io.path.exists
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

/** Setzt eine .tex mit LuaLaTeX zu einem PDF/A. Die .xmpdata trägt denselben Namen, daraus baut pdfx das XMP. */
class LatexCompiler(private val luaLatex: LuaLatex) {
    fun checkAvailable() = luaLatex.checkAvailable()

    fun compile(
        workingDir: Path,
        jobName: String,
        sources: PdfSources,
    ): Path {
        // "." und ".." sind für Path schlichte Namen, meinen aber Verzeichnisse.
        sources.files.keys.forEach { name ->
            require(name.isNotBlank() && name != "." && name != "..") { "Kein Dateiname: '$name'" }
            require(name == Path.of(name).fileName.toString()) { "Kein schlichter Dateiname: $name" }
            require(name != "$jobName.tex" && name != "$jobName.xmpdata") { "$name ist schon vergeben" }
        }
        // Scheitert das Schreiben im Temp-Verzeichnis, ist das ein Fehler des Werkzeugs, nicht der Rechnung (#300).
        try {
            workingDir.resolve("$jobName.tex").writeText(sources.tex)
            workingDir.resolve("$jobName.xmpdata").writeText(sources.xmpMetadata)
            sources.files.forEach { (name, content) -> workingDir.resolve(name).writeBytes(content) }
        } catch (e: IOException) {
            throw ToolchainException("Die Quellen für $PROGRAM ließen sich nicht nach $workingDir schreiben: ${e.message}", e)
        }

        val id = trailerId(sources)
        val creationDate = sources.creationDate.atStartOfDay(ZoneOffset.UTC).toInstant()

        var log: String
        var passes = 0
        do {
            val run = luaLatex.run(workingDir, jobName, id, creationDate)
            log = run.log
            if (run.failed) throw ToolchainException(errorReport(jobName, log))
            passes++
        } while (needsAnotherPass(log) && passes < MAX_PASSES)
        // Gefragt wird das Protokoll des letzten Laufs: Aus ihm stammt das PDF.
        overflowReport(jobName, log)?.let { throw ToolchainException(it) }
        return pdfOrFail(workingDir, jobName)
    }

    /**
     * Aus den Quellen statt, wie LuaTeX von sich aus, aus dem Pfad des Temp-Verzeichnisses. Die Null trennt die
     * Quellen („ab“ + „c“ ist nicht „a“ + „bc“), bei Bytes die Länge, weil dort die Null selbst vorkommt. Die
     * Dateien gehen mit ein, sonst änderte ein Logo das PDF, aber nicht die ID (#198).
     */
    private fun trailerId(sources: PdfSources): String {
        val md5 = MessageDigest.getInstance("MD5")
        md5.update(sources.tex.toByteArray())
        md5.update(0.toByte())
        md5.update(sources.xmpMetadata.toByteArray())
        sources.files.toSortedMap().forEach { (name, content) ->
            md5.update(0.toByte())
            md5.update(name.toByteArray())
            md5.update(0.toByte())
            md5.update(content.size.toString().toByteArray())
            md5.update(0.toByte())
            md5.update(content)
        }
        return HexFormat.of().withUpperCase().formatHex(md5.digest())
    }

    /**
     * Nicht jedes Paket sagt es gleich: lastpage „Rerun to get“, longtable „Rerun LaTeX“. Fehlte eine Wendung,
     * fiele das erst auf, wenn kein anderes Paket den Lauf mehr verlangt – dann entstünde das PDF aus dem ersten (#321).
     */
    private fun needsAnotherPass(log: String): Boolean =
        log.lineSequence().any { it.contains("Rerun to get") || it.contains("Please rerun") || it.contains("Rerun LaTeX") }

    /**
     * Bei `Overfull \vbox` schneidet LuaLaTeX mit Exitcode 0 ab, was übersteht – ein gültiges PDF/A ohne den Inhalt
     * (ADR-0020). `Overfull \hbox` ragt nur in den Rand und bleibt erlaubt.
     */
    private fun overflowReport(jobName: String, log: String): String? {
        val lines = log.lines().filter { it.startsWith("Overfull \\vbox") }.distinct()
        if (lines.isEmpty()) return null
        return "$PROGRAM hat $jobName.tex gesetzt, aber auf einer Seite steht mehr, als das Blatt fasst. " +
            "Was übersteht, fehlte im PDF:\n\n${lines.joinToString("\n")}"
    }

    /** Die Zeilen ab „!“ samt zwei Folgezeilen mit der Fundstelle; ohne „!“ der Schluss des Protokolls. */
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

    private companion object {
        // Obergrenze, nicht Anzahl: Wer nach drei Läufen noch einen will, konvergiert nicht.
        private const val MAX_PASSES = 3
    }

    private fun pdfOrFail(workingDir: Path, jobName: String): Path =
        workingDir.resolve("$jobName.pdf").also {
            if (!it.exists()) throw ToolchainException("$PROGRAM meldete Erfolg, hat aber kein PDF geschrieben: $it")
        }
}
