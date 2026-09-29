package de.fluchitconsulting.rechnungsgenerator.core.text

/**
 * Nicht die Vorlage eines Designs: Ein TextTemplate kennt weder Format noch Vokabular (ADR-0019). Zerlegt wird beim
 * Bau, damit ein unbekannter Name dort wirft, wo der Wortlaut steht.
 */
class TextTemplate<out V> private constructor(
    private val parts: List<Part<V>>,
) {
    /** Zwei Funktionen, weil ein Format auch den festen Text betrifft: LaTeX maskiert ihn. */
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

        /** Ein Name, den [V] nicht kennt, wirft. */
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
