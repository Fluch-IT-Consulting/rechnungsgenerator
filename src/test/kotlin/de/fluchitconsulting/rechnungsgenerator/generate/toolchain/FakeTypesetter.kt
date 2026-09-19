package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.writeText

/**
 * Der Adapter fürs Testen: liefert ein vorbereitetes PDF und ein vorbereitetes
 * Prüfergebnis, ohne LuaLaTeX und veraPDF zu starten.
 *
 * Er merkt sich, womit er gerufen wurde – das Arbeitsverzeichnis lässt sich
 * sonst nicht prüfen, weil das Kommando es selbst anlegt und niemandem nennt.
 */
class FakeTypesetter(
    private val valid: Boolean = true,
    // Scheitert der Satz, wirft er, was LatexCompiler würfe.
    private val failure: String? = null,
) : Typesetter {
    var workingDir: Path? = null
    var jobName: String? = null
    var texSource: String? = null
    var creationDate: LocalDate? = null
    var files: Map<String, ByteArray>? = null

    override fun checkAvailable() = Unit

    override fun typeset(
        workingDir: Path,
        jobName: String,
        texSource: String,
        xmpMetadata: String,
        creationDate: LocalDate,
        files: Map<String, ByteArray>,
    ): TypesetPdf {
        this.workingDir = workingDir
        this.jobName = jobName
        this.texSource = texSource
        this.creationDate = creationDate
        this.files = files
        if (failure != null) throw ToolchainException(failure)
        val pdf = workingDir.resolve("$jobName.pdf").also { it.writeText("%PDF-1.7 kein echtes PDF") }
        val violations =
            if (valid) {
                emptyList()
            } else {
                listOf(PdfAViolation("6.1.2", "1", "Datei ist keine PDF/A", "so nicht"))
            }
        return TypesetPdf(pdf, ValidationResult(valid, violations))
    }
}
