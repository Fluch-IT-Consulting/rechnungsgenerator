package de.fluchitconsulting.rechnung.toolchain

/**
 * Liest das Protokoll, das LuaLaTeX hinterlässt.
 *
 * Ein eigenes Modul, weil hier nichts von Prozessen, Dateien oder Zeitgrenzen
 * vorkommt: Text geht hinein, eine Antwort kommt heraus. Solange die beiden
 * Funktionen im [LatexCompiler] steckten, war die einzige Art, sie zu prüfen,
 * ein echter Lauf mit absichtlich kaputtem LaTeX – zwei Sekunden für eine
 * Frage, die Millisekunden braucht.
 *
 * Der Deletion-Test fällt für dieses Modul negativ aus: Gelöscht wandert die
 * Komplexität zurück in `compile`, es verschwindet nichts. Verdient hat sich
 * nicht das Modul, sondern die **Naht** – der Ort, an dem Testdaten eintreten
 * können.
 */
object LatexLog {
    /**
     * Verlangt LaTeX einen weiteren Lauf?
     *
     * Es sagt das in Worten und nicht über einen Rückgabewert: "Rerun to get
     * cross-references right", "Rerun to get outlines right" – je nach Paket
     * verschieden formuliert, aber immer mit derselben Wendung.
     *
     * Bei den Rechnungen dieses Projekts kommt das heute nicht vor: Sie haben
     * weder Querverweise noch Lesezeichen, und der erste Lauf genügt
     * (nachgemessen mit TeX Live 2026 – die PDFs beider Läufe sind im Satzbild
     * gleich). Gefragt wird trotzdem, statt eine feste Zahl von Läufen
     * anzunehmen: Wer der Vorlage ein Inhaltsverzeichnis hinzufügt, bekommt
     * dann den zweiten Lauf, ohne dass jemand daran denken muss.
     */
    fun needsAnotherPass(log: String): Boolean =
        log.lineSequence().any { it.contains("Rerun to get") || it.contains("Please rerun") }

    /**
     * Zieht die Fehlerzeilen aus dem Protokoll.
     *
     * LuaLaTeX schreibt gut tausend Zeilen Paketgeplauder; die Meldungen, auf
     * die es ankommt, beginnen mit "!". Die zwei Folgezeilen kommen mit, weil
     * dort meist die Fundstelle steht.
     *
     * `distinct()` fängt den Fall ab, dass zwei Fehler dicht beieinanderliegen:
     * Dann überlappen ihre Dreierblöcke, und ohne das stünde dieselbe Zeile
     * zweimal im Bericht.
     *
     * Findet sich kein "!", bleibt der Schluss des Protokolls übrig – besser
     * als eine leere Meldung, denn irgendetwas ist ja schiefgegangen.
     */
    fun errorReport(jobName: String, log: String): String {
        val lines = log.lines()
        val relevant =
            lines
                .withIndex()
                .filter { (_, line) -> line.startsWith("!") }
                .flatMap { (index, _) -> lines.subList(index, minOf(index + 3, lines.size)) }
                .distinct()
        val details = if (relevant.isEmpty()) log.takeLast(800) else relevant.joinToString("\n")
        return "lualatex konnte $jobName.tex nicht übersetzen:\n\n$details"
    }
}
