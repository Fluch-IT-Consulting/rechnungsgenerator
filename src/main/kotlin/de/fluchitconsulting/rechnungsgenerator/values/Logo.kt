package de.fluchitconsulting.rechnungsgenerator.values

import java.nio.file.Path
import kotlin.io.path.extension

/**
 * Das Logo des Rechnungsstellers: die Datei, aus der es stammt, und ihr Inhalt.
 *
 * Die Endung bestimmt die Art, ohne Rücksicht auf Groß- und Kleinschreibung;
 * [extension] liefert sie klein. Zulässig ist bisher nur `tex`, ein
 * LaTeX-Fragment.
 *
 * Keine `data class`: Deren `equals` verglich ein [ByteArray] nach Identität,
 * nicht nach Inhalt – zwei Logos aus derselben Datei wären verschieden.
 */
class Logo(
    val source: Path,
    val content: ByteArray,
) {
    init {
        require(isAllowed(source)) { unsupported(source) }
    }

    val extension: String get() = source.extension.lowercase()

    override fun equals(other: Any?): Boolean = other is Logo && source == other.source && content.contentEquals(other.content)

    override fun hashCode(): Int = 31 * source.hashCode() + content.contentHashCode()

    override fun toString(): String = "Logo($source, ${content.size} Bytes)"

    // `companion object`: was zur Klasse gehört, aber an keinem Exemplar hängt –
    // hier die Prüfung, die schon vor dem Lesen der Datei gilt.
    companion object {
        val EXTENSIONS = listOf("tex")

        fun isAllowed(source: Path): Boolean = source.extension.lowercase() in EXTENSIONS

        fun unsupported(source: Path): String =
            "Das Logo $source hat keine zulässige Endung; zulässig ist ${EXTENSIONS.joinToString { ".$it" }}."
    }
}
