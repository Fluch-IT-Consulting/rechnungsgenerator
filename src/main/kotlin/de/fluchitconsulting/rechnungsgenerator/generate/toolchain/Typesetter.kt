package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import java.nio.file.Path
import java.time.LocalDate

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
     *
     * [creationDate] ist das Erstellungsdatum im PDF. Es kommt von außen statt
     * von der Uhr, damit dieselben Quellen dasselbe PDF ergeben.
     *
     * [files] sind weitere Quellen, die der Quelltext unter ihrem Namen
     * einbindet – etwa `logo.tex` (#198). Sie gehören zu den Quellen wie
     * `.tex` und `.xmpdata`, auch für die Frage, ob dasselbe PDF entsteht.
     * Bytes und nicht Text, weil ein Bild später auch welche ist.
     */
    fun typeset(
        workingDir: Path,
        jobName: String,
        texSource: String,
        xmpMetadata: String,
        creationDate: LocalDate,
        files: Map<String, ByteArray>,
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
        texSource: String,
        xmpMetadata: String,
        creationDate: LocalDate,
        files: Map<String, ByteArray>,
    ): TypesetPdf {
        val pdf = compiler.compile(workingDir, jobName, texSource, xmpMetadata, creationDate, files)
        return TypesetPdf(pdf, validator.validate(pdf))
    }
}
