package de.fluchitconsulting.rechnungsgenerator.core.model

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.YamlTaggedNode

/**
 * Sucht Steuerzeichen in den Werten einer YAML-Datei (#391).
 *
 * Ein echtes Steuerzeichen lässt YAML im Text nicht zu; hinein kommt es über
 * ein Escape in doppelten Anführungszeichen wie `"\x0B"`. Ungeprüft käme es bis
 * in den Satz, wo LuaLaTeX mit einer Meldung über seinen eigenen Quelltext
 * abbricht, und ins XML der E-Rechnung, das dann keines mehr ist – beides
 * schickt die Benutzerin an die falsche Stelle.
 *
 * Geprüft wird der Knotenbaum vor dem Decodieren und nicht Feld für Feld: So
 * sind Stammdaten und Rechnung gleich gedeckt, und ein neues Freitextfeld
 * braucht keine eigene Zeile, die man vergessen könnte. Keine Regel mit Befund
 * (ADR-0004): Das Zeichen ist ein Fehler der Datei wie ungültiges YAML, keine
 * Aussage über die Rechnung. Schlüssel bleiben ungeprüft, einen unbekannten
 * lehnt kaml ohnehin ab.
 */
internal object ControlCharacters {
    /** Je Wert mit Steuerzeichen eine Zeile: Pfad, Ort und die Zeichen. */
    fun findIn(root: YamlNode): List<String> = scalars(root).mapNotNull(::describe).toList()

    private fun scalars(node: YamlNode): Sequence<YamlScalar> =
        when (node) {
            is YamlScalar -> {
                sequenceOf(node)
            }
            is YamlMap -> {
                node.entries.values
                    .asSequence()
                    .flatMap(::scalars)
            }
            is YamlList -> {
                node.items.asSequence().flatMap(::scalars)
            }
            is YamlTaggedNode -> {
                scalars(node.innerNode)
            }
            is YamlNull -> {
                emptySequence()
            }
        }

    private fun describe(scalar: YamlScalar): String? {
        val found =
            scalar.content
                .codePoints()
                .filter(::isForbidden)
                .distinct()
                .toArray()
        if (found.isEmpty()) return null
        val location = scalar.path.endLocation
        val names = found.joinToString { "U+%04X".format(it) }
        return "${scalar.path.toHumanReadableString()} (Zeile ${location.line}, Spalte ${location.column}): $names"
    }

    /**
     * Tabulator und Zeilenumbruch bleiben erlaubt, ein mehrzeiliges Feld trägt
     * sie mit Absicht. CR nicht: YAML macht aus jedem echten Zeilenende ein LF,
     * ein CR kommt also nur über `"\r"` herein, und TeX verschluckt dahinter
     * still den Rest der Zeile. DEL und C1 erlaubt XML zwar, aber sie sind
     * Steuerzeichen wie die übrigen, und DEL bricht LaTeX ab wie `\x0B`. Über
     * die Steuerzeichen hinaus verbietet XML 1.0 noch U+FFFE, U+FFFF und ein
     * Surrogat ohne Partner – eines mit Partner hat `codePoints` schon zu
     * einem Zeichen zusammengesetzt.
     */
    private fun isForbidden(codePoint: Int): Boolean =
        when (codePoint) {
            '\t'.code, '\n'.code -> false
            0xFFFE, 0xFFFF -> true
            in Character.MIN_SURROGATE.code..Character.MAX_SURROGATE.code -> true
            else -> Character.getType(codePoint) == Character.CONTROL.toInt()
        }
}
