package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import de.fluchitconsulting.rechnungsgenerator.toolchain.EInvoiceValidator
import de.fluchitconsulting.rechnungsgenerator.toolchain.ExternalTool
import de.fluchitconsulting.rechnungsgenerator.toolchain.PdfAValidator
import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import de.fluchitconsulting.rechnungsgenerator.toolchain.ValidationResult
import java.nio.file.Path

/** Eine Funktion, weil der Prüfer erst mit dem Schalter `--externe-pdfa-pruefung` feststeht. */
typealias TypesetterFactory = (PdfAValidator) -> Typesetter

data class TypesetPdf(val pdf: Path, val validation: ValidationResult)

/** Aus Quelltext wird ein geprüftes PDF: die Naht zu den Werkzeugen, im Test mit vorbereitetem Ergebnis. */
interface Typesetter {
    /** Vor dem ersten Lauf, nicht mitten im Satz. */
    fun checkAvailable()

    /**
     * Die Arbeitsdateien bleiben in [workingDir] liegen. Wirft [ToolchainException], auch für eine IOException:
     * Beim Kommando heißt eine IOException, die Zieldatei ließ sich nicht schreiben (#300).
     */
    fun typeset(
        workingDir: Path,
        jobName: String,
        sources: PdfSources,
    ): TypesetPdf
}

/** LuaLaTeX setzt, ein [PdfAValidator] prüft (ADR-0014); mit E-Rechnung prüft Mustang davor das XML (ADR-0023). */
class LuaLatexTypesetter(private val validator: PdfAValidator) : Typesetter {
    private val compiler = LatexCompiler(ExternalTool)

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
        val typeset = compiler.compile(workingDir, jobName, sources)
        val pdf =
            sources.einvoice?.let { xml ->
                workingDir.resolve("$jobName-zugferd.pdf").also { EInvoiceEmbedder.embed(typeset, xml, it) }
            } ?: typeset
        return TypesetPdf(pdf, validator.validate(pdf, sources.pdfaProfile))
    }

    /** Ein Fehler im XML ist eine Lücke im Generator, kein Urteil über die Rechnung. Warnungen hält `CiiWriterTest`. */
    private fun checkEInvoice(xml: ByteArray) {
        val result = EInvoiceValidator.validate(xml)
        if (!result.valid) {
            throw InvalidEInvoiceException(
                "Das XML der E-Rechnung ist ungültig. Das ist ein Fehler des Generators, nicht der Rechnung:\n" +
                    result.errors.joinToString("\n") { "  $it" },
            )
        }
    }
}

/** Ein eigener Typ: Am XML ist, anders als am Satz, nie das Logo schuld (#386). */
class InvalidEInvoiceException(message: String) : ToolchainException(message)
