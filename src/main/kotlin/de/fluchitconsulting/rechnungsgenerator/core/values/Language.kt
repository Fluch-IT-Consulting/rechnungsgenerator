package de.fluchitconsulting.rechnungsgenerator.core.values

import de.fluchitconsulting.rechnungsgenerator.core.serialization.enumSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * BCP 47, immer mit Variante: Kommt eine zweite Variante derselben Sprache dazu, bleibt jede Datei gültig.
 * Eine geschlossene Menge, weil jeder Satz des Programms einen Wortlaut in ihr braucht (ADR-0025).
 */
@Serializable(with = LanguageSerializer::class)
enum class Language(
    val tag: String,
) {
    GERMAN("de-DE"),
    BRITISH_ENGLISH("en-GB"),
    AMERICAN_ENGLISH("en-US"),
    ;

    val locale: Locale = Locale.forLanguageTag(tag)

    override fun toString(): String = tag
}

object LanguageSerializer :
    KSerializer<Language> by enumSerializer(
        name = "Language",
        values = Language.entries,
        unknown = "Unbekannte Sprache",
        // Ohne Rücksicht auf die Schreibweise, wie BCP 47 selbst.
        normalize = { it.trim().lowercase() },
        keyOf = Language::tag,
    )
