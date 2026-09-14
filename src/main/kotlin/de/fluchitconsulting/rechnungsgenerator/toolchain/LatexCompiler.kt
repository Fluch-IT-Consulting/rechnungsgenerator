package de.fluchitconsulting.rechnungsgenerator.toolchain

import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.HexFormat
import kotlin.io.path.exists
import kotlin.io.path.readText
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
        texSource: String,
        xmpMetadata: String,
        creationDate: LocalDate,
    ): Path {
        workingDir.resolve("$jobName.tex").writeText(texSource)
        workingDir.resolve("$jobName.xmpdata").writeText(xmpMetadata)

        val id = trailerId(texSource, xmpMetadata)
        val command =
            listOf(
                PROGRAM,
                "-interaction=nonstopmode",
                "-halt-on-error",
                "-jobname=$jobName",
                "\\pdfvariable trailerid {[ <$id> <$id> ]}\\input{$jobName.tex}",
            )
        val environment =
            mapOf(
                "SOURCE_DATE_EPOCH" to creationDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond().toString(),
                "TZ" to "UTC",
            )

        repeat(MAX_PASSES) { pass ->
            val result = runner.run(command, workingDir, environment = environment)
            val log = workingDir.resolve("$jobName.log").let { if (it.exists()) it.readText() else result.output }
            if (result.failed) throw ToolchainException(errorReport(jobName, log))
            if (!needsAnotherPass(log)) return pdfOrFail(workingDir, jobName)
        }
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
     */
    private fun trailerId(texSource: String, xmpMetadata: String): String {
        val md5 = MessageDigest.getInstance("MD5")
        md5.update(texSource.toByteArray())
        md5.update(0.toByte())
        md5.update(xmpMetadata.toByteArray())
        return HexFormat.of().withUpperCase().formatHex(md5.digest())
    }

    /**
     * Verlangt LaTeX einen weiteren Lauf?
     *
     * Es sagt das in Worten und nicht über einen Rückgabewert: "Rerun to get
     * cross-references right", "Rerun to get outlines right" – je nach Paket
     * verschieden formuliert, aber immer mit derselben Wendung.
     *
     * Bei den Rechnungen dieses Projekts kommt das heute nicht vor: Sie haben
     * weder Querverweise noch Lesezeichen, und der erste Lauf genügt
     * (nachgemessen mit TeX Live 2026 – die PDFs beider Läufe sind im Satzbild
     * gleich). Gefragt wird trotzdem, statt eine feste Zahl von Läufen
     * anzunehmen: Wer der Vorlage ein Inhaltsverzeichnis hinzufügt, bekommt
     * dann den zweiten Lauf, ohne dass jemand daran denken muss.
     */
    private fun needsAnotherPass(log: String): Boolean =
        log.lineSequence().any { it.contains("Rerun to get") || it.contains("Please rerun") }

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
        // weiteren Lauf verlangt – siehe needsAnotherPass. Bei den
        // Rechnungen dieses Projekts ist das heute keiner; sie haben weder
        // Querverweise noch Lesezeichen, und nachgemessen mit TeX Live 2026 sind
        // die PDFs nach einem und nach zwei Läufen im Satzbild gleich.
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
