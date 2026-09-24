package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import java.io.IOException
import java.nio.file.Path
import java.security.MessageDigest
import java.time.ZoneOffset
import java.util.HexFormat
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

/**
 * Setzt eine .tex-Datei mit LuaLaTeX zu einem PDF/A.
 *
 * Die .xmpdata-Datei muss neben der .tex liegen und denselben Namen tragen –
 * daraus baut das Paket pdfx die XMP-Metadaten des PDF/A-Dokuments.
 *
 * Der [runner] kommt herein, statt hier gegriffen zu werden: Dadurch lässt sich
 * das, was zwischen Prozessaufruf und PDF passiert – Exitcode deuten, Protokoll
 * lesen, Läufe zählen –, mit einem aufgezeichneten Lauf prüfen, ohne dass
 * LuaLaTeX vorhanden sein muss.
 */
class LatexCompiler(private val runner: ProcessRunner) {
    fun compile(
        workingDir: Path,
        jobName: String,
        sources: PdfSources,
    ): Path {
        // Ein Name mit Verzeichnis landete außerhalb des Arbeitsverzeichnisses,
        // einer wie die .tex überschriebe sie. "." und ".." sind für Path
        // schlichte Namen, meinen aber Verzeichnisse; ein leerer Name das
        // Arbeitsverzeichnis selbst.
        sources.files.keys.forEach { name ->
            require(name.isNotBlank() && name != "." && name != "..") { "Kein Dateiname: '$name'" }
            require(name == Path.of(name).fileName.toString()) { "Kein schlichter Dateiname: $name" }
            require(name != "$jobName.tex" && name != "$jobName.xmpdata") { "$name ist schon vergeben" }
        }
        // Das Arbeitsverzeichnis ist ein Temp-Verzeichnis; scheitert das
        // Schreiben dort, kommt der Satz nicht zustande – ein Fehler des
        // Werkzeugs und nicht einer beim Schreiben der Rechnung (#300).
        try {
            workingDir.resolve("$jobName.tex").writeText(sources.tex)
            workingDir.resolve("$jobName.xmpdata").writeText(sources.xmpMetadata)
            sources.files.forEach { (name, content) -> workingDir.resolve(name).writeBytes(content) }
        } catch (e: IOException) {
            throw ToolchainException("Die Quellen für $PROGRAM ließen sich nicht nach $workingDir schreiben: ${e.message}", e)
        }

        val id = trailerId(sources)
        val params =
            listOf(
                "-interaction=nonstopmode",
                "-halt-on-error",
                "-jobname=$jobName",
                "\\pdfvariable trailerid {[ <$id> <$id> ]}\\input{$jobName.tex}",
            )
        val environment =
            mapOf(
                "SOURCE_DATE_EPOCH" to
                    sources.creationDate
                        .atStartOfDay(ZoneOffset.UTC)
                        .toEpochSecond()
                        .toString(),
                "TZ" to "UTC",
            )

        var log: String
        var passes = 0
        do {
            val result = runner.run(PROGRAM, params, workingDir, environment = environment)
            log = workingDir.resolve("$jobName.log").let { if (it.exists()) it.readText() else result.output }
            if (result.failed) throw ToolchainException(errorReport(jobName, log))
            passes++
        } while (needsAnotherPass(log) && passes < MAX_PASSES)
        // Gefragt wird das Protokoll des letzten Laufs: Aus ihm stammt das PDF.
        overflowReport(jobName, log)?.let { throw ToolchainException(it) }
        return pdfOrFail(workingDir, jobName)
    }

    /**
     * Die Trailer-ID des PDF, gebildet aus den Quellen: gleicher Inhalt, gleiche
     * ID, geänderter Inhalt, andere ID.
     *
     * Von sich aus leitet LuaTeX sie auch aus dem Pfad ab, und jeder Lauf setzt
     * in einem eigenen Temp-Verzeichnis – dieselbe Rechnung ergäbe nie dasselbe
     * PDF. Das Datum ist der andere Teil davon: `SOURCE_DATE_EPOCH` ersetzt die
     * Uhr, `TZ=UTC` legt fest, wie LuaTeX es schreibt. Gesetzt wird beides hier
     * und nicht in der Vorlage, denn es ist eine Frage des Werkzeugs, nicht des
     * Satzes.
     *
     * Die Null zwischen den beiden Quellen trennt sie: Ohne sie ergäben "ab" +
     * "c" und "a" + "bc" dieselbe Prüfsumme.
     *
     * Die weiteren Dateien gehen mit Namen und Inhalt ein (#198): Ein Logo, das
     * die .tex nur per `\input` nennt, änderte sonst das PDF, aber nicht die ID.
     * Sortiert nach Namen, weil eine Map keine Reihenfolge verspricht. Die Länge
     * vor jedem Inhalt trennt wie die Null – nur taugt die Null bei Bytes nicht,
     * weil sie selbst darin vorkommen kann.
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
     * Verlangt LaTeX einen weiteren Lauf?
     *
     * Es sagt das in Worten und nicht über einen Rückgabewert, und nicht jedes
     * Paket in denselben: Der Kern und lastpage schreiben "Rerun to get …",
     * longtable schreibt "Table widths have changed. Rerun LaTeX." Gefragt wird
     * nach jeder dieser Wendungen. Fehlte eine, fiele es nicht auf, solange ein
     * anderes Paket im selben Lauf ebenfalls einen weiteren verlangt – und
     * sobald es das nicht mehr tut, entstünde ein PDF aus dem ersten Lauf (#321).
     *
     * Bei den Rechnungen dieses Projekts sind es zwei Läufe (nachgemessen mit
     * TeX Live 2026): longtable kennt die Spaltenbreiten der Positionen erst aus
     * dem ersten, und nach ihm stehen die Spalten sichtbar anders als nach dem
     * zweiten; lastpage kennt die Zahl der Seiten für "Seite n von N" ebenso
     * erst danach. Gefragt wird trotzdem, statt zwei Läufe fest anzunehmen:
     * Kommt ein Paket hinzu, das einen dritten braucht, bekommt es ihn, ohne
     * dass jemand daran denken muss.
     */
    private fun needsAnotherPass(log: String): Boolean =
        log.lineSequence().any { it.contains("Rerun to get") || it.contains("Please rerun") || it.contains("Rerun LaTeX") }

    /**
     * Die Meldung, wenn eine Seite mehr trägt, als das Blatt fasst – oder null.
     *
     * LuaLaTeX setzt eine solche Seite trotzdem, mit Exitcode 0, und schneidet
     * ab, was unten übersteht. Das PDF ist dann gültiges PDF/A, und veraPDF
     * hat nichts einzuwenden; nur das Protokoll sagt `Overfull \vbox`. Deshalb
     * entsteht keines (ADR-0020).
     *
     * `Overfull \hbox` dagegen bleibt stehen: Eine zu breite Zeile ragt in den
     * Rand, aber sie steht auf dem Blatt.
     */
    private fun overflowReport(jobName: String, log: String): String? {
        val lines = log.lines().filter { it.startsWith("Overfull \\vbox") }.distinct()
        if (lines.isEmpty()) return null
        return "$PROGRAM hat $jobName.tex gesetzt, aber auf einer Seite steht mehr, als das Blatt fasst. " +
            "Was übersteht, fehlte im PDF:\n\n${lines.joinToString("\n")}"
    }

    /**
     * Zieht die Fehlerzeilen aus dem Protokoll.
     *
     * LuaLaTeX schreibt gut tausend Zeilen Paketgeplauder; die Meldungen, auf
     * die es ankommt, beginnen mit "!". Die zwei Folgezeilen kommen mit, weil
     * dort meist die Fundstelle steht.
     *
     * `distinct()` fängt den Fall ab, dass zwei Fehler dicht beieinanderliegen:
     * Dann überlappen ihre Dreierblöcke, und ohne das stünde dieselbe Zeile
     * zweimal im Bericht.
     *
     * Findet sich kein "!", bleibt der Schluss des Protokolls übrig – besser
     * als eine leere Meldung, denn irgendetwas ist ja schiefgegangen.
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
        return "lualatex konnte $jobName.tex nicht übersetzen:\n\n$details"
    }

    private companion object {
        private const val PROGRAM = "lualatex"

        // Die Obergrenze, nicht die Anzahl: Gelaufen wird, solange LaTeX einen
        // weiteren Lauf verlangt – siehe needsAnotherPass, dort auch, warum es
        // bei den Rechnungen dieses Projekts zwei sind.
        //
        // Drei als Grenze, weil ein Dokument, das nach dreien immer noch einen
        // weiteren verlangt, nicht konvergiert – ein vierter änderte daran nichts.
        private const val MAX_PASSES = 3
    }

    private fun pdfOrFail(workingDir: Path, jobName: String): Path =
        workingDir.resolve("$jobName.pdf").also {
            if (!it.exists()) throw ToolchainException("$PROGRAM meldete Erfolg, hat aber kein PDF geschrieben: $it")
        }
}
