package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import java.nio.file.Path

/**
 * Ein [Typesetter], sobald der Prüfer feststeht.
 *
 * Eine Funktion und kein fertiger Wert, weil die Reihenfolge es verlangt: Den
 * Prüfer wählt der Schalter `--externe-pdfa-pruefung`, und der ist erst
 * gelesen, wenn das Kommando schon läuft. Für den Test bleibt es dieselbe
 * Naht – er reicht einen, der den Prüfer gar nicht anschaut.
 */
typealias TypesetterFactory = (PdfAValidator) -> Typesetter

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
 * werden –, ließe sich ohne diese Naht kein Test schreiben, der nicht die
 * Werkzeuge voraussetzte.
 *
 * Zwei Adapter erfüllen dieses Interface, und deshalb ist die Naht eine echte
 * und keine gedachte: [LuaLatexTypesetter] im Betrieb, im Test einer, der ein
 * vorbereitetes PDF und ein vorbereitetes Prüfergebnis liefert.
 *
 * Welcher Prüfer hinter der Naht steht, ist noch einmal eine eigene Wahl:
 * siehe [PdfAValidator].
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
     *
     * Scheitert Satz oder Prüfung, wirft er [ToolchainException] – auch dort,
     * wo das Betriebssystem eine IOException meldet, etwa bei einem vollen
     * Temp-Verzeichnis. Nur so kann das Kommando eine IOException als das
     * lesen, was sie bei ihm heißt: Die Zieldatei ließ sich nicht schreiben
     * (#300).
     */
    fun typeset(
        workingDir: Path,
        jobName: String,
        sources: PdfSources,
    ): TypesetPdf
}

/**
 * Der Adapter für den Betrieb: LuaLaTeX setzt, ein [PdfAValidator] prüft.
 *
 * Der Prüfer kommt von außen, weil er gewählt werden kann – eingebaut oder als
 * Programm `verapdf` (ADR-0014). Eine Klasse also und kein `object`: Welcher
 * es ist, entscheidet das Kommando anhand seines Schalters, und erst danach
 * steht der Typesetter fest.
 */
class LuaLatexTypesetter(private val validator: PdfAValidator) : Typesetter {
    // Bekommt den echten Prozessläufer; im Test steht an seiner Stelle ein
    // aufgezeichneter Lauf.
    private val compiler = LatexCompiler(ExternalTool)

    /**
     * LuaLaTeX braucht es immer, den Prüfer fragt sein Adapter selbst: Der
     * eingebaute braucht nichts, der externe ein installiertes Programm. Ohne
     * `--externe-pdfa-pruefung` läuft der Generator deshalb ganz ohne veraPDF.
     */
    override fun checkAvailable() {
        ExternalTool.requireAll("lualatex" to "TeX Live, siehe https://tug.org/texlive/")
        validator.checkAvailable()
    }

    override fun typeset(
        workingDir: Path,
        jobName: String,
        sources: PdfSources,
    ): TypesetPdf {
        val pdf = compiler.compile(workingDir, jobName, sources)
        return TypesetPdf(pdf, validator.validate(pdf, PdfAProfile.PDFA_1B))
    }
}
