package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import java.nio.file.Path

/** Ein gesetztes und geprüftes PDF. */
data class TypesetPdf(val pdf: Path, val validation: ValidationResult)

/**
 * Aus Quelltext wird ein geprüftes PDF – die Naht zu den externen Programmen.
 *
 * Alles vor dieser Linie (Einlesen, Rechnen, Setzen der Vorlage) läuft ohne
 * fremde Prozesse und ist entsprechend geprüft. Hinter ihr liegen LuaLaTeX und
 * veraPDF: zwei Programme, ein Temp-Verzeichnis, ein Exitcode und ein paar
 * Sekunden Laufzeit. Genau an der Stelle, an der beides zusammenkommt – wo
 * Dateien kopiert, Hinweise ausgegeben und Arbeitsverzeichnisse gelöscht
 * werden –, gab es bisher keinen Test, weil jeder Lauf die Werkzeuge
 * vorausgesetzt hätte.
 *
 * Zwei Adapter erfüllen dieses Interface, und deshalb ist die Naht eine echte
 * und keine gedachte: [LuaLatexTypesetter] im Betrieb, im Test einer, der ein
 * vorbereitetes PDF und ein vorbereitetes Prüfergebnis liefert.
 */
interface Typesetter {
    /**
     * Prüft, ob die Werkzeuge vorhanden sind – vor dem ersten Lauf, nicht
     * mitten im Satz. Gehört hierher und nicht daneben: Ein Adapter, der keine
     * fremden Programme braucht, hat auch nichts zu prüfen.
     */
    fun checkAvailable()

    /**
     * Setzt die [sources] und prüft das Ergebnis gegen PDF/A-1b. Die
     * Arbeitsdateien bleiben in [workingDir] liegen; wer sie loswerden will,
     * räumt das Verzeichnis selbst auf.
     *
     * Die Quellen kommen als ein Wert und nicht als vier Parameter (#202):
     * Was in die Bytes des PDF eingeht, steht damit an einer Stelle, und ein
     * neuer Wert – etwa die XML-Datei für ZUGFeRD – kommt dort an, ohne dass
     * jeder Durchreicher davon erfährt. [workingDir] und [jobName] bleiben
     * daneben: Sie sagen, wohin gesetzt wird, nicht woraus.
     */
    fun typeset(
        workingDir: Path,
        jobName: String,
        sources: PdfSources,
    ): TypesetPdf
}

/** Der Adapter für den Betrieb: LuaLaTeX setzt, veraPDF prüft. */
object LuaLatexTypesetter : Typesetter {
    private val REQUIRED_TOOLS =
        arrayOf(
            "lualatex" to "TeX Live, siehe https://tug.org/texlive/",
            "verapdf" to "brew install verapdf",
        )

    // Die beiden Teile bekommen den echten Prozessläufer; im Test steht an
    // seiner Stelle ein aufgezeichneter Lauf.
    private val compiler = LatexCompiler(ExternalTool)
    private val validator = PdfAValidator(ExternalTool)

    override fun checkAvailable() = ExternalTool.requireAll(*REQUIRED_TOOLS)

    override fun typeset(
        workingDir: Path,
        jobName: String,
        sources: PdfSources,
    ): TypesetPdf {
        val pdf = compiler.compile(workingDir, jobName, sources)
        return TypesetPdf(pdf, validator.validate(pdf))
    }
}
