package de.fluchitconsulting.rechnungsgenerator.core.model

import java.nio.file.Path
import kotlin.io.path.extension

/** Zulässig ist nur `tex` (ADR-0012). Keine `data class`: Ihr `equals` verglich das [ByteArray] nach Identität. */
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

    companion object {
        val EXTENSIONS = listOf("tex")

        fun isAllowed(source: Path): Boolean = source.extension.lowercase() in EXTENSIONS

        fun unsupported(source: Path): String =
            "Das Logo $source hat keine zulässige Endung; zulässig ist ${EXTENSIONS.joinToString { ".$it" }}."
    }
}
