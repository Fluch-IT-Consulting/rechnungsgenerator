package de.fluchitconsulting.rechnungsgenerator.core.values

/**
 * Ein Wortlaut mit Platzhaltern `<<NAME>>`, zerlegt in festen Text und
 * Variablen vom Typ [V].
 *
 * Nicht zu verwechseln mit der Vorlage eines Designs in `generate.render`:
 * Die ist LaTeX und wird vom Renderer gefüllt. Ein `TextTemplate` kennt kein
 * Ausgabeformat und kein Vokabular – welche Variablen es gibt, bringt [V] mit,
 * wie Text und Werte aussehen, entscheidet der Aufrufer von [map].
 *
 * Zerlegt wird beim Bau und nicht erst beim Einsetzen: Ein unbekannter Name
 * wirft dann dort, wo der Wortlaut steht, und wer ihn einsetzt, bekommt nur
 * noch Werte von [V] zu sehen.
 */
class TextTemplate<out V> private constructor(
    private val parts: List<Part<V>>,
) {
    /**
     * Überführt jeden Teil mit der passenden Funktion – den festen Text mit
     * [text], jede Variable mit [variable] – und liefert die Teile in ihrer
     * Reihenfolge.
     *
     * Zwei Funktionen und nicht eine für die Variablen allein, weil ein Format
     * auch den festen Text betrifft: LaTeX maskiert ihn, ein String lässt ihn,
     * wie er ist. Liefe alles durch eine einzige Zeichenkette, wären Text und
     * eingesetzte Werte schon vermischt, bevor das Format sie trennen könnte.
     */
    fun <R> map(
        text: (String) -> R,
        variable: (V) -> R,
    ): List<R> =
        parts.map { part ->
            when (part) {
                is Part.Text -> text(part.text)
                is Part.Variable -> variable(part.variable)
            }
        }

    private sealed interface Part<out V> {
        data class Text(val text: String) : Part<Nothing>

        data class Variable<out V>(val variable: V) : Part<V>
    }

    companion object {
        // Nur Großbuchstaben und Unterstrich: `<<name>>` bleibt Text.
        private val PLACEHOLDER = Regex("<<([A-Z_]+)>>")

        /**
         * Zerlegt [text] und schlägt jeden Namen unter den Einträgen von [V]
         * nach. Ein Name, den [V] nicht kennt, wirft.
         *
         * `inline` mit `reified`, damit [V] zur Laufzeit bekannt ist und
         * `enumValues` die Einträge liefern kann – sonst müsste jeder Aufrufer
         * sie selbst übergeben.
         */
        inline fun <reified V : Enum<V>> parse(text: String): TextTemplate<V> =
            parse(text, enumValues<V>().associateBy { it.name })

        @PublishedApi
        internal fun <V> parse(
            text: String,
            variables: Map<String, V>,
        ): TextTemplate<V> {
            val parts = mutableListOf<Part<V>>()
            var textStart = 0
            PLACEHOLDER.findAll(text).forEach { match ->
                val name = match.groupValues[1]
                val variable = requireNotNull(variables[name]) { "Unbekannte Variable <<$name>>." }
                parts.addText(text.substring(textStart, match.range.first))
                parts += Part.Variable(variable)
                textStart = match.range.last + 1
            }
            parts.addText(text.substring(textStart))
            return TextTemplate(parts)
        }

        // Leerer Text zwischen zwei Platzhaltern oder am Rand ist kein Teil.
        private fun <V> MutableList<Part<V>>.addText(text: String) {
            if (text.isNotEmpty()) this += Part.Text(text)
        }
    }
}
