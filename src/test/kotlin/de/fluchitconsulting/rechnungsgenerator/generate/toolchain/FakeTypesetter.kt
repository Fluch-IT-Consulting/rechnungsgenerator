package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import de.fluchitconsulting.rechnungsgenerator.toolchain.PdfAViolation
import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import de.fluchitconsulting.rechnungsgenerator.toolchain.ValidationResult
import java.io.IOException
import java.nio.file.Path
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
    // Bricht den Vertrag der Naht, der für alles Scheitern eine
    // ToolchainException zusagt – für den Test, dass das Kommando eine
    // IOException aus dem Satz nicht als Schreibfehler ausgibt (#300).
    private val ioFailure: String? = null,
) : Typesetter {
    var workingDir: Path? = null
    var jobName: String? = null

    // Die Quellen als Ganzes (#202): Einzeln gemerkt fehlte leicht einer –
    // und dass das Kommando ihn weiterreicht, prüfte dann allein der Lauf mit
    // den echten Werkzeugen.
    var sources: PdfSources? = null

    override fun checkAvailable() = Unit

    override fun typeset(
        workingDir: Path,
        jobName: String,
        sources: PdfSources,
    ): TypesetPdf {
        this.workingDir = workingDir
        this.jobName = jobName
        this.sources = sources
        if (failure != null) throw ToolchainException(failure)
        if (ioFailure != null) throw IOException(ioFailure)
        val pdf = workingDir.resolve("$jobName.pdf").also { it.writeText("%PDF-1.7 kein echtes PDF") }
        val violations =
            if (valid) {
                emptyList()
            } else {
                listOf(PdfAViolation("ISO 19005-1:2005", "6.1.2", "1", "Datei ist keine PDF/A", "so nicht"))
            }
        return TypesetPdf(pdf, ValidationResult(sources.pdfaProfile, valid, violations))
    }
}
