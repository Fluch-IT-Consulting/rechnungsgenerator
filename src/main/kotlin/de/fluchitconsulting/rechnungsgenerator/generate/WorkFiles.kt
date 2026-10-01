package de.fluchitconsulting.rechnungsgenerator.generate

import com.github.ajalt.clikt.core.CliktError
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

/** Das Verzeichnis für den Satz. Was nach dem Lauf wo zum Ansehen liegt, sagt nur dieser Typ (#517). */
internal class WorkFiles private constructor(
    val dir: Path,
    private val jobName: String,
    private val keep: Boolean,
) : AutoCloseable {
    // Die Meldung des Satzes zitiert Zeilen der .tex (#507).
    fun dirNote(): String =
        if (keep) {
            "Die Arbeitsdateien samt $jobName.tex liegen unter $dir."
        } else {
            "Mit --arbeitsdateien-behalten bleiben die Arbeitsdateien samt $jobName.tex zum Ansehen liegen."
        }

    // Die Datei und nicht das Verzeichnis: Mit E-Rechnung liegt dort auch das gültige PDF/A-1b (#390).
    fun pdfNote(pdf: Path): String =
        if (keep) {
            "Das PDF liegt zum Ansehen unter $pdf."
        } else {
            "Mit --arbeitsdateien-behalten bleibt es zum Ansehen liegen."
        }

    // Der Hinweis auf den Überstand nennt die .tex (#488).
    fun texNote(): String =
        if (keep) {
            "Die Datei liegt unter ${dir.resolve("$jobName.tex")}."
        } else {
            "Mit --arbeitsdateien-behalten bleibt $jobName.tex zum Ansehen liegen."
        }

    /** Die Zeile nach einem Erfolg; ohne den Schalter gibt es keine. */
    val summary: String? get() = if (keep) "Arbeitsdateien: $dir" else null

    override fun close() {
        if (!keep) dir.toFile().deleteRecursively()
    }

    companion object {
        fun create(
            jobName: String,
            keep: Boolean,
        ): WorkFiles =
            try {
                WorkFiles(createTempDirectory("rechnungsgenerator"), jobName, keep)
            } catch (e: IOException) {
                throw CliktError("Das Arbeitsverzeichnis für den Satz ließ sich nicht anlegen: ${e.message}")
            }
    }
}
