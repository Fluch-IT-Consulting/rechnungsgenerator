package de.fluchitconsulting.rechnung.toolchain

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
     * Setzt [texSource] samt [xmpMetadata] und prüft das Ergebnis gegen
     * PDF/A-1b. Die Arbeitsdateien bleiben in [workingDir] liegen; wer sie
     * loswerden will, räumt das Verzeichnis selbst auf.
     */
    fun typeset(workingDir: Path, jobName: String, texSource: String, xmpMetadata: String): TypesetPdf
}

/** Der Adapter für den Betrieb: LuaLaTeX setzt, veraPDF prüft. */
object LuaLatexTypesetter : Typesetter {
    private val REQUIRED_TOOLS =
        arrayOf(
            "lualatex" to "TeX Live, siehe https://tug.org/texlive/",
            "verapdf" to "brew install verapdf",
        )

    override fun checkAvailable() = ExternalTool.requireAll(*REQUIRED_TOOLS)

    override fun typeset(workingDir: Path, jobName: String, texSource: String, xmpMetadata: String): TypesetPdf {
        val pdf = LatexCompiler.compile(workingDir, jobName, texSource, xmpMetadata)
        return TypesetPdf(pdf, PdfAValidator.validate(pdf))
    }
}
