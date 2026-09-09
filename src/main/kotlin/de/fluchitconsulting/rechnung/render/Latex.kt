package de.fluchitconsulting.rechnung.render

/**
 * Ein fertiges Stück LaTeX – gesetzt aus Text, der maskiert wurde, oder aus
 * Markup, das der Renderer bewusst selbst schreibt.
 *
 * Der Typ ersetzt eine Disziplin durch eine Prüfung. Vorher entschied jede
 * Aufrufstelle für sich, ob ein Wert durch [escapeLatex] muss; ein vergessener
 * Aufruf war kein Übersetzungsfehler, sondern ein zerlegter Satz – ein `&`
 * beendet die Tabellenspalte, ein `%` verschluckt den Rest der Zeile. Jetzt
 * kommt nichts mehr ins Dokument, das nicht einen der drei Wege genommen hat:
 *
 *  - [escapeLatex] für alles, was aus den YAML-Dateien stammt,
 *  - [GermanFormat] für Beträge, Sätze, Mengen und Daten,
 *  - [raw] für Markup – die eine benannte Luke, absichtlich zählbar.
 *
 * `value class`: Zur Laufzeit bleibt es eine Zeichenkette, es entsteht kein
 * Objekt, das man später beim Debuggen wieder auspacken müsste. Der
 * Konstruktor ist privat, sonst wäre `Latex("beliebiger Text")` die Hintertür,
 * die den ganzen Zug wertlos machte.
 */
@JvmInline
value class Latex private constructor(val tex: String) {
    operator fun plus(other: Latex): Latex = Latex(tex + other.tex)

    // Bewusst KEIN toString(), das den Inhalt liefert: Sonst rutschte ein
    // Fragment unbemerkt in eine Zeichenkette ("$fragment"), und die Prüfung,
    // die dieser Typ sein soll, hätte ein stilles Loch. Wer den Text will,
    // schreibt .tex und sagt damit, dass er die Naht verlässt.

    companion object {
        val EMPTY = Latex("")

        /**
         * Markup, das der Renderer selbst schreibt: `\midrule`, Spaltentrenner,
         * Zeilenumbrüche mit Abstand. Der Name ist die Warnung – wer hier Daten
         * hineinreicht, umgeht die Maskierung.
         *
         * Für den häufigsten Fall gibt es [macro]; sonst stünde diese Warnung
         * an zwanzig Stellen, von denen siebzehn nur Klammern setzen, und wäre
         * damit keine mehr.
         */
        fun raw(tex: String): Latex = Latex(tex)

        /**
         * Ein Makroaufruf mit einem Argument: `\name{inhalt}`.
         *
         * Bildet nicht die einzelnen Makros der Vorlage ab – das wäre eine
         * zweite Beschreibung des Satzes in Kotlin –, sondern ihre gemeinsame
         * Form. Der Name bleibt eine Zeichenkette an genau einer Stelle; wer
         * ein Makro in der Vorlage umbenennt, ändert hier ein Wort.
         *
         * [name] ist ein Makroname aus der Vorlage und **niemals** ein Wert aus
         * den Eingabedaten: Er landet unmaskiert hinter einem Backslash.
         *
         * Bewusst **keine** zweite Fassung, die einen `String` nimmt und selbst
         * maskiert. Sie wäre kürzer, machte aber die Aufrufstelle stumm: Heute
         * liest man dort, ob Daten hereinkommen (`x.escapeLatex()`) oder ein
         * fertiges Fragment (`anteile`) – mit einer Überladung hinge das am Typ
         * einer Variablen, den man nachschlagen müsste. Sicherheit gewänne man
         * nicht, der Compiler lässt ohnehin keinen `String` durch; man verlöre
         * eine der drei benannten Türen von Text zu Markup ([escapeLatex],
         * [GermanFormat], [raw]), die man durchsuchen kann. Und
         * `macro("textbf", "\\LaTeX")` würde still das Falsche drucken, statt
         * nicht zu übersetzen.
         */
        fun macro(name: String, content: Latex): Latex = raw("\\$name{") + content + raw("}")
    }
}

/**
 * Maskiert Text, der aus den YAML-Dateien stammt, für den LaTeX-Satz.
 *
 * Ohne das würde ein Kundenname wie "Meier & Söhne" den Lauf abbrechen (`&` ist
 * in LaTeX der Spaltentrenner) und "50 % Anzahlung" den Rest der Zeile
 * verschlucken (`%` leitet einen Kommentar ein).
 *
 * Auf einen Text der Vorlage angewandt ist das ein leerer Lauf – deshalb dürfen
 * auch feste Beschriftungen wie "Nettobetrag" diesen Weg nehmen. [Latex.raw]
 * bleibt dem Markup vorbehalten und behält so seine Warnwirkung.
 */
fun String.escapeLatex(): Latex =
    Latex.raw(
        buildString(length) {
            for (char in this@escapeLatex) {
                when (char) {
                    // Der Backslash muss zuerst behandelt werden – er ist das
                    // Fluchtzeichen selbst und darf nicht doppelt maskiert werden.
                    '\\' -> append("\\textbackslash{}")
                    '&', '%', '$', '#', '_', '{', '}' -> append('\\').append(char)
                    // Diese beiden sind in LaTeX Akzent-Befehle, keine Zeichen.
                    '~' -> append("\\textasciitilde{}")
                    '^' -> append("\\textasciicircum{}")
                    // Das geschützte Leerzeichen aus GermanFormat wird zur
                    // Tilde – die ist in LaTeX kein Zeichen, sondern die
                    // Abkürzung für \nobreakspace (latex.ltx: \def~{...}).
                    // Sie wird also NICHT von der Zeile darüber wieder
                    // maskiert: Dort geht eine Tilde aus den Eingabedaten
                    // hinein, hier kommt Markup heraus.
                    //
                    // Warum überhaupt: LuaLaTeX bricht auch am nackten U+00A0
                    // nicht um, aber das Zeichen ist ein FESTER Zwischenraum,
                    // während ~ dehnbar ist wie ein gewöhnlicher. Nachgemessen
                    // in einer \hbox fester Breite: Beim U+00A0 bleibt die
                    // Lücke schmal, die übrigen nehmen die Dehnung auf. Im
                    // Flattersatz dieser Vorlage fällt das nicht auf; in einem
                    // Block, dem jemand den Flattersatz vergisst, schon.
                    '\u00A0' -> append('~')
                    else -> append(char)
                }
            }
        },
    )

/**
 * Fügt Fragmente zusammen. Der Trenner ist Markup und deshalb eine rohe
 * Zeichenkette – in dieser Vorlage sind es Spaltentrenner (` & `) und
 * Zeilenumbrüche mit Abstand (` \\[2.5mm]`).
 */
fun Iterable<Latex>.joinToLatex(separator: String = ""): Latex = Latex.raw(joinToString(separator) { it.tex })
