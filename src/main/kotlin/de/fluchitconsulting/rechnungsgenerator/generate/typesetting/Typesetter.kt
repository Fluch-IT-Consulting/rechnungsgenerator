package de.fluchitconsulting.rechnungsgenerator.generate.typesetting

import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import de.fluchitconsulting.rechnungsgenerator.toolchain.process.ExternalTool
import de.fluchitconsulting.rechnungsgenerator.toolchain.process.LuaLatex
import de.fluchitconsulting.rechnungsgenerator.toolchain.validation.EInvoiceValidator
import de.fluchitconsulting.rechnungsgenerator.toolchain.validation.PdfAValidator
import de.fluchitconsulting.rechnungsgenerator.toolchain.validation.ValidationResult
import java.nio.file.Path

/** Eine Funktion, weil der Prüfer erst mit dem Schalter `--externe-pdfa-pruefung` feststeht. */
typealias TypesetterFactory = (PdfAValidator) -> Typesetter

/** Die Hinweise ohne Vorgabewert: Ein Typesetter, der sie nicht weiterreicht, soll nicht übersetzen (#488). */
data class TypesetPdf(
    val pdf: Path,
    val validation: ValidationResult,
    val overflowHint: String?,
    val rerunHint: String?,
)

/** Aus Quelltext wird ein geprüftes PDF: die Naht zu den Werkzeugen, im Test mit vorbereitetem Ergebnis. */
interface Typesetter {
    /** Vor dem ersten Lauf, nicht mitten im Satz. */
    fun checkAvailable()

    /**
     * Die Arbeitsdateien bleiben in [workingDir] liegen. Wirft [ToolchainException], auch für eine IOException:
     * Beim Kommando heißt eine IOException, die Zieldatei ließ sich nicht schreiben (#300). Scheitert der Satz
     * selbst, ist es eine [TypesettingException] (#491).
     */
    fun typeset(
        workingDir: Path,
        jobName: String,
        sources: PdfSources,
    ): TypesetPdf
}

/** LuaLaTeX setzt, ein [PdfAValidator] prüft (ADR-0014); mit E-Rechnung prüft Mustang davor das XML (ADR-0023). */
class LuaLatexTypesetter(private val validator: PdfAValidator) : Typesetter {
    private val compiler = LatexCompiler(LuaLatex(ExternalTool))

    /** Jeder Adapter fragt nach seinem Programm: Ohne `--externe-pdfa-pruefung` braucht es kein veraPDF. */
    override fun checkAvailable() {
        compiler.checkAvailable()
        validator.checkAvailable()
    }

    override fun typeset(
        workingDir: Path,
        jobName: String,
        sources: PdfSources,
    ): TypesetPdf {
        // Vor dem Satz: Ein ungültiges XML soll gar kein PDF ergeben.
        sources.einvoice?.let(::checkEInvoice)
        val compiled = compiler.compile(workingDir, jobName, sources)
        val pdf =
            sources.einvoice?.let { xml ->
                workingDir.resolve("$jobName-zugferd.pdf").also { EInvoiceEmbedder.embed(compiled.pdf, xml, it) }
            } ?: compiled.pdf
        return TypesetPdf(pdf, validator.validate(pdf, sources.pdfaProfile), compiled.overflowHint, compiled.rerunHint)
    }

    /** Ein Fehler im XML ist eine Lücke im Generator, kein Urteil über die Rechnung. Warnungen hält `CiiWriterTest`. */
    private fun checkEInvoice(xml: ByteArray) {
        val result = EInvoiceValidator.validate(xml)
        if (!result.valid) {
            throw ToolchainException(
                "Das XML der E-Rechnung ist ungültig. Das ist ein Fehler des Generators, nicht der Rechnung:\n" +
                    result.errors.joinToString("\n") { "  $it" },
            )
        }
    }
}
