package de.fluchitconsulting.rechnungsgenerator.core.model

import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar

/**
 * Steuerzeichen in den Werten einer YAML-Datei (#391). Geprüft wird der Knotenbaum vor dem Decodieren, damit
 * ein neues Freitextfeld keine eigene Zeile braucht. Kein Befund nach ADR-0004: Das Zeichen ist ein Fehler der Datei.
 */
internal object ControlCharacters {
    fun findIn(root: YamlNode): List<String> = scalarsOf(root).mapNotNull(::describe).toList()

    private fun describe(scalar: YamlScalar): String? {
        val found =
            scalar.content
                .codePoints()
                .filter(::isForbidden)
                .distinct()
                .toArray()
        if (found.isEmpty()) return null
        return "${scalar.location()}: ${found.joinToString { "U+%04X".format(it) }}"
    }

    /**
     * Tab und LF bleiben erlaubt. CR nicht: Ein echtes Zeilenende macht YAML zu LF, und hinter `"\r"` verschluckt TeX
     * still den Rest der Zeile. Dazu U+FFFE, U+FFFF und einzelne Surrogate, die XML 1.0 verbietet; ein Paar hat
     * `codePoints` schon zusammengesetzt.
     */
    private fun isForbidden(codePoint: Int): Boolean =
        when (codePoint) {
            '\t'.code, '\n'.code -> false
            0xFFFE, 0xFFFF -> true
            in Character.MIN_SURROGATE.code..Character.MAX_SURROGATE.code -> true
            else -> Character.getType(codePoint) == Character.CONTROL.toInt()
        }
}
