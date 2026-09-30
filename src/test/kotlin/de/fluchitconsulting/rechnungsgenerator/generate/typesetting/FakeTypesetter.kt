package de.fluchitconsulting.rechnungsgenerator.generate.typesetting

import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import de.fluchitconsulting.rechnungsgenerator.toolchain.validation.PdfAViolation
import de.fluchitconsulting.rechnungsgenerator.toolchain.validation.ValidationResult
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.writeText

/** Merkt sich seinen Aufruf: Das Arbeitsverzeichnis legt das Kommando selbst an und nennt es niemandem. */
class FakeTypesetter(
    private val valid: Boolean = true,
    private val failure: String? = null,
    // Bricht den Vertrag der Naht absichtlich (#300).
    private val ioFailure: String? = null,
    private val einvoiceFailure: String? = null,
    private val overflowHint: String? = null,
) : Typesetter {
    var workingDir: Path? = null
    var jobName: String? = null

    // Als Ganzes, damit keine Quelle fehlt (#202).
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
        if (einvoiceFailure != null) throw InvalidEInvoiceException(einvoiceFailure)
        if (failure != null) throw ToolchainException(failure)
        if (ioFailure != null) throw IOException(ioFailure)
        // Der Name, den LuaLatexTypesetter dem eingebetteten PDF gibt (#390).
        val name = if (sources.einvoice == null) "$jobName.pdf" else "$jobName-zugferd.pdf"
        val pdf = workingDir.resolve(name).also { it.writeText("%PDF-1.7 kein echtes PDF") }
        val violations =
            if (valid) {
                emptyList()
            } else {
                listOf(PdfAViolation("ISO 19005-1:2005", "6.1.2", "1", "Datei ist keine PDF/A", "so nicht"))
            }
        return TypesetPdf(pdf, ValidationResult(sources.pdfaProfile, valid, violations), overflowHint)
    }
}
