package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.text.TextTemplate
import de.fluchitconsulting.rechnungsgenerator.core.text.TextValue
import de.fluchitconsulting.rechnungsgenerator.core.text.VariableResolver

/**
 * Ein fertiges Stück LaTeX. Hinein kommt nur, was einen von vier Wegen nimmt: [escapeLatex] für Daten,
 * [escapeXmpData] für Daten in der .xmpdata, [DateFormat] und [Numbers] für Daten und Zahlen, [raw] für Markup.
 * Ein unmaskiertes `&` oder `%` zerlegte sonst den Satz; deshalb ist auch der Konstruktor privat.
 */
@JvmInline
value class Latex private constructor(val tex: String) {
    operator fun plus(other: Latex): Latex = Latex(tex + other.tex)

    // Bewusst kein toString(), das den Inhalt liefert: Sonst rutschte ein Fragment still in "$fragment".

    companion object {
        val EMPTY = Latex("")

        /**
         * Markup, das der Renderer selbst schreibt – nie Daten, die umgingen die Maskierung. Zeilenenden (`\\`)
         * gehören nicht dazu, sie stehen als Makro in der Vorlage, und [Template.fillIn] bricht sonst ab.
         */
        fun raw(tex: String): Latex = Latex(tex)

        /**
         * Ein Makro der Vorlage: `\positionszeile{a}{b}{c}{d}{e}`. Ohne Argument mit leerem `{}`: Sonst
         * verlängerte ein folgender Buchstabe den Makronamen, und erst LuaLaTeX sähe das.
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

/** Maskiert Text, der aus den YAML-Dateien stammt, für den LaTeX-Satz. */
fun String.escapeLatex(): Latex =
    Latex.raw(
        buildString(length) {
            for (char in this@escapeLatex) {
                when (char) {
                    '\\' -> append("\\textbackslash{}")
                    '&', '%', '$', '#', '_', '{', '}' -> append('\\').append(char)
                    // In LaTeX Akzentbefehle, keine Zeichen.
                    '~' -> append("\\textasciitilde{}")
                    '^' -> append("\\textasciicircum{}")
                    // Das geschützte Leerzeichen aus DateFormat wird Markup: ~ ist dehnbar wie ein Wortabstand,
                    // U+00A0 dagegen fest und fiele in einem Blocksatz als schmale Lücke auf.
                    '\u00A0' -> append('~')
                    // TeX Gyre Heros hat kein U+2009; pdftotext läse an seiner Stelle U+FFFD (#192).
                    '\u2009' -> append("\\,")
                    else -> append(char)
                }
            }
        },
    )

/**
 * Maskiert Text für die .xmpdata. pdfx liest sie mit eigenen Catcodes: `_ # ^ ~ $` sind dort gewöhnliche Zeichen, `&`
 * setzt es selbst um. Maskiert stünden Info-Dictionary und XMP verschieden da, gegen PDF/A 6.7.3 (#485, pdfx.sty).
 */
fun String.escapeXmpData(): Latex =
    Latex.raw(
        buildString(length) {
            var afterBackslash = false
            for (char in this@escapeXmpData) {
                when {
                    // `{}` stünde im XMP wörtlich da; das Leerzeichen danach verschluckt TeX, ebenso das nächste.
                    char == '\\' -> append("\\backslash ")
                    char == '%' || char == '{' || char == '}' -> append('\\').append(char)
                    char.isWhitespace() && afterBackslash -> append("\\ ")
                    // pdfx verbietet eine Leerzeile, und `~` stünde wörtlich da.
                    char.isWhitespace() -> append(' ')
                    else -> append(char)
                }
                afterBackslash = char == '\\'
            }
        },
    )

/**
 * Setzt einen Wortlaut: den festen Text maskiert, jeden Wert aus [resolver] in [format]. Jeder Teil wird für
 * sich und genau einmal maskiert – erst einsetzen, dann maskieren, machte aus `\zusammen` ein
 * `\textbackslash{}zusammen`. In `\zusammen` stehen die Werte, damit Datum und Nummer nicht umbrechen.
 */
fun <V> TextTemplate<V>.toLatex(
    format: DateFormat,
    resolver: VariableResolver<V>,
): Latex =
    map(
        text = { it.escapeLatex() },
        variable = { variable ->
            when (val value = resolver.resolve(variable)) {
                is TextValue.Text -> together(value.text.escapeLatex())
                is TextValue.Date -> together(format.date(value.date))
            }
        },
    ).joinToLatex()

/** Fügt Fragmente zusammen. Der Trenner ist Markup, ein Zeilenende nimmt die Überladung mit einem Makro. */
fun Iterable<Latex>.joinToLatex(separator: String = ""): Latex = Latex.raw(joinToString(separator) { it.tex })

fun Iterable<Latex>.joinToLatex(separator: Latex): Latex = joinToLatex(separator.tex)
