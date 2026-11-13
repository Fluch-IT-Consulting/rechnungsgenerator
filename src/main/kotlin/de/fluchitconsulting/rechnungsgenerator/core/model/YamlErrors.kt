package de.fluchitconsulting.rechnungsgenerator.core.model

import com.charleskorn.kaml.DuplicateKeyException
import com.charleskorn.kaml.EmptyYamlDocumentException
import com.charleskorn.kaml.ForbiddenAnchorOrAliasException
import com.charleskorn.kaml.IncorrectTypeException
import com.charleskorn.kaml.MalformedYamlException
import com.charleskorn.kaml.MissingRequiredPropertyException
import com.charleskorn.kaml.UnexpectedNullValueException
import com.charleskorn.kaml.UnknownPropertyException
import com.charleskorn.kaml.YamlException
import com.charleskorn.kaml.YamlPath
import com.charleskorn.kaml.YamlScalarFormatException
import de.fluchitconsulting.rechnungsgenerator.core.serialization.MissingValueException
import de.fluchitconsulting.rechnungsgenerator.core.serialization.RejectedValueException

/**
 * Die Lesefehler von kaml auf Deutsch und mit Fundstelle wie bei [BlankValues] (#515, ADR-0025). kaml verschachtelt
 * sie je Ebene in `InvalidPropertyValueException`; den ganzen Pfad trägt die innerste. Ebenso eingepackt kommt
 * [RejectedValueException], der schon deutsche Text eines Serializers, dem nur die Fundstelle fehlt (#545), und
 * [MissingValueException] mit dem Hinweis eines Serializers zum leeren Wert (#548).
 */
internal object YamlErrors {
    fun describe(e: YamlException): String {
        val innermost = generateSequence<Throwable>(e) { it.cause }.filterIsInstance<YamlException>().last()
        val where = where(innermost.path)
        return when (innermost) {
            is MissingRequiredPropertyException -> {
                val missing = "fehlt der Pflichtschlüssel '${innermost.propertyName}'."
                if (innermost.path == YamlPath.root) "Es $missing" else "Unter $where $missing"
            }
            is UnexpectedNullValueException -> {
                "$where steht ohne Wert da, braucht aber einen."
            }
            is MissingValueException -> {
                "$where steht ohne Wert da. ${innermost.message}"
            }
            is UnknownPropertyException -> {
                "Unbekannter Schlüssel $where. Bekannt sind dort: ${innermost.validPropertyNames.sorted().joinToString()}."
            }
            is DuplicateKeyException -> {
                val first = innermost.originalLocation
                "Der Schlüssel $where steht doppelt, zuerst in Zeile ${first.line}, Spalte ${first.column}."
            }
            is IncorrectTypeException -> {
                "$where: ${shape(innermost.message)}"
            }
            is YamlScalarFormatException -> {
                // Ganze Zahlen sind die einzigen Werte, die kaml selbst liest; alle anderen lesen die Serializer.
                val expected = if ("integer" in innermost.message) "keine ganze Zahl" else "kein gültiger Wert"
                "$where: '${innermost.originalValue}' ist $expected."
            }
            is MalformedYamlException -> {
                "Kein gültiges YAML in Zeile ${innermost.line}, Spalte ${innermost.column} oder kurz davor. Häufig ist " +
                    "es eine Einrückung, ein Tabulator, eine offene Klammer oder ein fehlendes Anführungszeichen."
            }
            is EmptyYamlDocumentException -> {
                "Die Datei ist leer oder enthält nur Kommentare."
            }
            is RejectedValueException -> {
                "$where: ${innermost.message}"
            }
            is ForbiddenAnchorOrAliasException -> {
                "$where: Anker und Verweise (& und *) gehen nicht; bitte den Wert ausschreiben."
            }
            else -> {
                "$where: Die Angabe lässt sich nicht lesen."
            }
        }
    }

    private fun where(path: YamlPath): String = if (path == YamlPath.root) "Auf oberster Ebene" else path.location()

    /** Aus „Expected a list, but got a scalar value“; die Wendungen stammen aus kaml 0.104.0. */
    private fun shape(message: String): String {
        val (expected, actual) = SHAPE.matchEntire(message)?.destructured ?: return UNKNOWN_SHAPE
        val wanted = EXPECTED[expected] ?: return UNKNOWN_SHAPE
        val found = FOUND[actual] ?: return UNKNOWN_SHAPE
        return "Hier gehört $wanted hin, $found."
    }

    private val SHAPE = Regex("Expected (.+), but got (.+)")

    private const val UNKNOWN_SHAPE = "Die Angabe hat hier nicht die erwartete Form."

    private val EXPECTED =
        mapOf(
            "a string" to "ein einzelner Wert",
            "a list" to "eine Liste",
            "a map" to "ein Abschnitt mit eigenen Schlüsseln",
            "an object" to "ein Abschnitt mit eigenen Schlüsseln",
        )

    private val FOUND =
        mapOf(
            "a scalar value" to "kein einzelner Wert",
            "a list" to "keine Liste",
            "a map" to "kein Abschnitt mit eigenen Schlüsseln",
        )
}
