package de.fluchitconsulting.rechnung.values

import kotlinx.serialization.SerializationException

/**
 * Schlägt eine Schreibweise aus der YAML in einem Enum nach und sagt bei einem
 * Fehlschlag, was zulässig gewesen wäre.
 *
 * Drei Enums taten das mit derselben Bauform – Map, Nachschlagen, Meldung –,
 * und beim dritten wich die Kopie schon beim Abschreiben ab: Die Map
 * schlüsselte roh, nachgeschlagen wurde kleingeschrieben. Folgenlos, solange
 * alle Schreibweisen klein sind, und von keinem Test zu bemerken. Genau diese
 * Art Abweichung verschwindet, wenn [normalize] nur einmal angewandt wird –
 * hier, auf beiden Seiten.
 *
 * Was die drei **unterschiedlich** machen, bleibt sichtbar und wird nicht
 * eingeebnet: [TaxCategory] schlägt über den Code in Großschreibung nach, weil
 * die Codeliste so geschrieben ist; [Unit] und [InvoiceType] über die
 * Schreibweise in Kleinbuchstaben, weil man sie so in die YAML tippt.
 *
 * @param unknown wie der Satz beginnt: "Unbekannte Einheit". Der Artikel steht
 *   mit dabei, statt aus einem Wort geraten zu werden – "Unbekanntes
 *   Prüfverfahren" wäre sonst nicht zu formulieren.
 * @param normalize wie Eingabe und Schlüssel vergleichbar gemacht werden.
 * @param keyOf welcher Teil des Eintrags der Schlüssel ist.
 * @param describe wie ein zulässiger Wert in der Meldung erscheint; ohne
 *   Angabe der Schlüssel selbst.
 */
class CodeLookup<T>(
    private val unknown: String,
    private val values: List<T>,
    private val normalize: (String) -> String,
    keyOf: (T) -> String,
    private val describe: (T) -> String = keyOf,
) {
    private val byKey = values.associateBy { normalize(keyOf(it)) }

    fun of(raw: String): T =
        byKey[normalize(raw)]
            ?: throw SerializationException(
                "$unknown '$raw'. Zulässig sind: ${values.joinToString { describe(it) }}",
            )
}
