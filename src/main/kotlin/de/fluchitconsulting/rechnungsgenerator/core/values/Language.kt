package de.fluchitconsulting.rechnungsgenerator.core.values

import de.fluchitconsulting.rechnungsgenerator.core.serialization.enumSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Die Sprache einer Rechnung, als Tag nach BCP 47 (#377).
 *
 * Jeder Wert nennt seine Variante, auch der deutsche: `de-DE` und nicht `de`.
 * An der Variante hängt die Schreibweise des Datums – `de-AT` schriebe
 * „Jänner“ –, und kommt eine zweite Variante derselben Sprache dazu, bleibt
 * jede vorhandene Datei gültig und bedeutet dasselbe wie zuvor. Ein `en`
 * allein müsste dann erst eine der beiden werden.
 *
 * Eine geschlossene Menge wie `Design`: Eine Sprache gibt es erst, wenn jeder
 * Satz des Programms und jede Beschriftung jedes Designs einen Wortlaut in ihr
 * hat. Ein beliebiger Tag mit `Locale` dahinter setzte die Rechnung sonst in
 * einer Sprache, die niemand geschrieben hat.
 *
 * [tag] ist die Schreibweise in der YAML und in den Metadaten des PDF, [locale]
 * das Gegenstück im JDK für Daten und Ländernamen.
 */
@Serializable(with = LanguageSerializer::class)
enum class Language(
    val tag: String,
) {
    GERMAN("de-DE"),
    BRITISH_ENGLISH("en-GB"),
    ;

    val locale: Locale = Locale.forLanguageTag(tag)

    override fun toString(): String = tag
}

object LanguageSerializer :
    KSerializer<Language> by enumSerializer(
        name = "Language",
        values = Language.entries,
        unknown = "Unbekannte Sprache",
        // Ohne Rücksicht auf die Schreibweise, wie BCP 47 selbst: `EN-GB` ist
        // derselbe Tag wie `en-GB`.
        normalize = { it.trim().lowercase() },
        keyOf = Language::tag,
    )
