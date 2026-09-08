package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.model.SellerConfig
import de.fluchitconsulting.rechnung.render.InvoiceRenderer
import de.fluchitconsulting.rechnung.toolchain.ExternalTool
import de.fluchitconsulting.rechnung.toolchain.LatexCompiler
import de.fluchitconsulting.rechnung.toolchain.PdfAValidator
import de.fluchitconsulting.rechnung.toolchain.ToolchainException
import de.fluchitconsulting.rechnung.toolchain.ValidationResult
import java.nio.file.Path

/**
 * Der ganze Weg von den Eingabedaten zum geprüften PDF an einer Stelle:
 * rechnen, setzen, übersetzen, validieren.
 *
 * Die Schritte davor bleiben ohne LaTeX testbar (InvoiceCalculator, Renderer);
 * erst hier kommen die externen Werkzeuge ins Spiel.
 */
object InvoiceGenerator {
    private val REQUIRED_TOOLS =
        arrayOf(
            "lualatex" to "TeX Live, siehe https://tug.org/texlive/",
            "verapdf" to "brew install verapdf",
        )

    /** Prüft die Werkzeuge einmal beim Start, statt mitten im Satz zu scheitern. */
    fun checkTools() = ExternalTool.requireAll(*REQUIRED_TOOLS)

    data class Result(
        val pdf: Path,
        val validation: ValidationResult,
        /** Hinweise für die eigene Akte – siehe InvoiceTotals.hints. */
        val hints: List<String> = emptyList(),
    )

    /**
     * Erzeugt das PDF in [workingDir] und prüft es. Der Dateiname leitet sich aus
     * der Rechnungsnummer ab; im Arbeitsverzeichnis landen daneben die .tex und
     * die Hilfsdateien von LuaLaTeX – nützlich, wenn der Satz mal schiefgeht.
     */
    fun generate(invoice: Invoice, config: SellerConfig, workingDir: Path): Result {
        val totals = InvoiceCalculator.calculate(invoice, config)
        val jobName = jobNameFor(invoice)
        val pdf =
            LatexCompiler.compile(
                workingDir = workingDir,
                jobName = jobName,
                texSource = InvoiceRenderer.render(invoice, config, totals),
                xmpMetadata = InvoiceRenderer.renderMetadata(invoice, config),
            )
        return Result(pdf, PdfAValidator.validate(pdf), totals.hints)
    }

    /**
     * Rechnungsart und -nummer als Dateiname – "Abschlagsrechnung-R26-009".
     * Im Ordner liegen die Dokumente zu einem Vorgang nebeneinander; welches
     * davon der Abschlag ist, soll man am Namen sehen und nicht erst nach dem
     * Öffnen. LuaLaTeX stolpert über Leerzeichen und Sonderzeichen im
     * Jobnamen, deshalb bleibt von der Nummer nur Unverfängliches übrig.
     */
    internal fun jobNameFor(invoice: Invoice): String {
        val safe = invoice.number.replace(Regex("[^A-Za-z0-9._-]"), "-").trim('-')
        if (safe.isEmpty()) throw ToolchainException("Die Rechnungsnummer '${invoice.number}' ergibt keinen Dateinamen.")
        return "${invoice.type.documentName}-$safe"
    }
}
