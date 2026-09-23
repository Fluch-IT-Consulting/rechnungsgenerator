package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.values.TextTemplate
import de.fluchitconsulting.rechnungsgenerator.core.values.TextValue
import de.fluchitconsulting.rechnungsgenerator.core.values.VariableResolver

/**
 * Ein fertiges Stück LaTeX – gesetzt aus Text, der maskiert wurde, oder aus
 * Markup, das der Renderer bewusst selbst schreibt.
 *
 * Der Typ ersetzt eine Disziplin durch eine Prüfung: Entschiede jede
 * Aufrufstelle für sich, ob ein Wert durch [escapeLatex] muss, wäre ein
 * vergessener Aufruf kein Übersetzungsfehler, sondern ein zerlegter Satz – ein
 * `&` beendet die Tabellenspalte, ein `%` verschluckt den Rest der Zeile. So
 * kommt nichts ins Dokument, das nicht einen der drei Wege genommen hat:
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
         * Markup, das der Renderer selbst schreibt: die Klammern eines
         * Makroaufrufs, Leerraum und Umbrüche, die das erzeugte .tex lesbar
         * halten. Zeilenenden von LaTeX gehören nicht dazu, sie stehen als Makro
         * in der Vorlage (#147) – und [Template.fillIn] bricht ab, wenn doch
         * eines hier hereinkommt. Der Name ist die Warnung – wer hier Daten
         * hineinreicht, umgeht die Maskierung.
         *
         * Für den häufigsten Fall gibt es [macro]; sonst stünde diese Warnung
         * an zwanzig Stellen, von denen siebzehn nur Klammern setzen, und wäre
         * damit keine mehr.
         */
        fun raw(tex: String): Latex = Latex(tex)

        /**
         * Ein Makro der Vorlage: `\positionszeile{a}{b}{c}{d}{e}`.
         *
         * Der einzige Weg zu einem Makroaufruf. Der Name steht nicht als
         * Zeichenkette da, sondern als [TemplateMacro] – und damit ist er
         * prüfbar (#76): Mit einem Namen als Zeichenkette schriebe der Renderer
         * `\mbox` an der Prüfung vorbei.
         *
         * Eine Liste statt `vararg`, weil Kotlin `vararg` für einen
         * `value class` verbietet – und einer ist [Latex]. Die Stelligkeit prüft
         * schon der Aufruf: Eine Zeile mit vier Zellen für ein fünfspaltiges
         * Makro fiele sonst erst LuaLaTeX auf.
         *
         * Ein Makro ohne Argument bekommt ein leeres `{}`. Ohne es fräße TeX
         * den Leerraum dahinter, oder – schlimmer – ein folgender Buchstabe
         * verlängerte den Namen: `\zeilenwechselMusterweg` ist ein unbekanntes
         * Makro, und das sähe erst LuaLaTeX.
         */
        fun macro(macro: TemplateMacro, arguments: List<Latex> = emptyList()): Latex {
            require(arguments.size == macro.arity) {
                "\\${macro.macroName} nimmt ${macro.arity} Argumente, hier sind es ${arguments.size}."
            }
            if (arguments.isEmpty()) return raw("\\${macro.macroName}{}")
            return raw("\\${macro.macroName}") + arguments.map { raw("{") + it + raw("}") }.joinToLatex()
        }
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
                    // Das schmale Leerzeichen (im Zeitraum um den Strich) wird
                    // zu \, – aus demselben Grund wie oben Markup statt
                    // Zeichen, hier aber zwingend: TeX Gyre Heros hat kein
                    // U+2009. Der Abstand erschien zwar, doch pdftotext las
                    // an seiner Stelle U+FFFD (#192).
                    '\u2009' -> append("\\,")
                    else -> append(char)
                }
            }
        },
    )

/**
 * Setzt einen Wortlaut mit Variablen: den festen Text maskiert, jeden Wert, den
 * [resolver] liefert, so, wie er im Fließtext stehen muss.
 *
 * Maskiert wird jeder Teil für sich und genau einmal. Erst einsetzen und dann
 * maskieren hieße, einen schon gesetzten Wert ein zweites Mal anzufassen – aus
 * `\zusammen` würde `\textbackslash{}zusammen`. Und weil der Wortlaut schon
 * beim Bau zerlegt ist, bleibt ein Wert, der selbst `<<…>>` enthält, Inhalt.
 *
 * Jeder Wert steht in \zusammen: Mitten im Satz dürfte "15. Juli 2026" sonst
 * hinter dem Tag umbrechen und eine Nummer wie "R26-042" am Bindestrich.
 */
fun <V> TextTemplate<V>.toLatex(resolver: VariableResolver<V>): Latex =
    map(
        text = { it.escapeLatex() },
        variable = { variable ->
            when (val value = resolver.resolve(variable)) {
                is TextValue.Text -> together(value.text.escapeLatex())
                is TextValue.Date -> together(GermanFormat.date(value.date))
            }
        },
    ).joinToLatex()

/**
 * Fügt Fragmente zusammen. Der Trenner ist Markup und deshalb eine rohe
 * Zeichenkette – hier der Mittelpunkt ` · ` zwischen den Angaben einer Zeile
 * und der Umbruch, der das erzeugte .tex lesbar hält. Ein Zeilenende von LaTeX
 * ist dagegen ein Makro der Vorlage und nimmt die Überladung darunter (#147).
 */
fun Iterable<Latex>.joinToLatex(separator: String = ""): Latex = Latex.raw(joinToString(separator) { it.tex })

/**
 * Dasselbe mit einem Trenner, der selbst ein Fragment ist – etwa ein Makro der
 * Vorlage wie `\positionstrenner{}`. Eine Überladung: Kotlin wählt nach dem Typ
 * des Arguments, welche Fassung gilt.
 */
fun Iterable<Latex>.joinToLatex(separator: Latex): Latex = joinToLatex(separator.tex)
