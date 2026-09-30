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

/** [overflowHint]: Was über den Rand seiner Zeile oder Spalte steht; das PDF ist trotzdem vollständig (#480). */
data class CompiledPdf(val pdf: Path, val overflowHint: String?)

/** Setzt eine .tex mit LuaLaTeX zu einem PDF/A. Die .xmpdata trägt denselben Namen, daraus baut pdfx das XMP. */
class LatexCompiler(private val luaLatex: LuaLatex) {
    fun checkAvailable() = luaLatex.checkAvailable()

    fun compile(
        workingDir: Path,
        jobName: String,
        sources: PdfSources,
    ): CompiledPdf {
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
            if (run.failed) throw TypesettingException(errorReport(jobName, log))
            passes++
        } while (needsAnotherPass(log) && passes < MAX_PASSES)
        // Gefragt wird das Protokoll des letzten Laufs: Aus ihm stammt das PDF.
        overflowReport(jobName, log)?.let { throw TypesettingException(it) }
        missingCharactersReport(jobName, log)?.let { throw TypesettingException(it) }
        return CompiledPdf(pdfOrFail(workingDir, jobName), overflowHint(jobName, log))
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
     * (ADR-0020). `Overfull \hbox` ragt nur über den Rand, das meldet [overflowHint].
     */
    private fun overflowReport(jobName: String, log: String): String? {
        val lines = log.lines().filter { it.startsWith("Overfull \\vbox") }.distinct()
        if (lines.isEmpty()) return null
        return "$PROGRAM hat $jobName.tex gesetzt, aber auf einer Seite steht mehr, als das Blatt fasst. " +
            "Was übersteht, fehlte im PDF:\n\n${lines.joinToString("\n")}"
    }

    /**
     * Ein Zeichen, das die Schrift nicht hat, lässt LuaLaTeX mit Exitcode 0 weg – womöglich aus einer Pflichtangabe
     * (#487, ADR-0020). Das Protokoll bricht die Meldung nach 79 Zeichen um, auch mitten im Namen der Schrift.
     */
    private fun missingCharactersReport(jobName: String, log: String): String? {
        val byFont =
            MISSING_CHARACTER
                .findAll(log)
                .map { match ->
                    val (character, codePoint, font) = match.destructured
                    val fontName = font.replace("\n", "").removePrefix("\"name:").substringBefore(":")
                    fontName to "$character (U+$codePoint)"
                }.distinct()
                .groupBy({ it.first }, { it.second })
        if (byFont.isEmpty()) return null
        return "$PROGRAM hat $jobName.tex gesetzt, aber die Schrift hat nicht jedes Zeichen der Rechnung. " +
            "Diese Zeichen fehlten im PDF:\n\n" +
            byFont.entries.joinToString("\n") { (font, characters) -> "  $font: ${characters.joinToString(", ")}" } +
            "\n\nDie Zeichen in Rechnungsdatei oder Stammdaten ersetzen oder ein Design wählen, dessen Schrift sie hat."
    }

    /**
     * Ein Hinweis und kein Abbruch (#480): Was übersteht, ist im PDF, in einer Tabelle aber womöglich über der Spalte
     * daneben. Die Folgezeile nennt den Text, der übersteht.
     */
    private fun overflowHint(jobName: String, log: String): String? {
        val lines = log.lines()
        val found =
            lines
                .withIndex()
                .filter { (_, line) -> line.startsWith("Overfull \\hbox") }
                .map { (index, line) -> line + lines.getOrNull(index + 1)?.let { "\n  ${it.trim()}" }.orEmpty() }
                .distinct()
        if (found.isEmpty()) return null
        return "In $jobName.tex steht etwas über den Rand seiner Zeile oder Spalte hinaus, in einer Tabelle womöglich " +
            "über der Spalte daneben. Vor dem Versand das PDF ansehen. $PROGRAM meldet:\n\n${found.joinToString("\n")}"
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

        private val MISSING_CHARACTER = Regex("""Missing character: There is no (.+?) \(U\+([0-9A-F]+)\) in font ([^!]*)!""")
    }

    private fun pdfOrFail(workingDir: Path, jobName: String): Path =
        workingDir.resolve("$jobName.pdf").also {
            if (!it.exists()) throw ToolchainException("$PROGRAM meldete Erfolg, hat aber kein PDF geschrieben: $it")
        }
}

/**
 * LuaLaTeX ist an den Quellen gescheitert. Nur hier kann das Logo schuld sein, nicht an Prüfer, XML oder Einbetten;
 * deshalb nennt `erzeuge` es allein bei diesem Typ (#491).
 */
class TypesettingException(message: String) : ToolchainException(message)
