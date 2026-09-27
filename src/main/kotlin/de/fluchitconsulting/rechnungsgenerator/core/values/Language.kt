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

    /**
     * Die Sprachbereiche, unter denen ein Text für diese Sprache gilt, vom
     * genauesten an: `en-GB`, dann `en`. Der Lookup aus RFC 4647, aber ohne
     * seitwärts zu suchen – `en-US` fiele nie auf `en-GB`.
     *
     * Eine eigene Funktion über die geschlossene Menge und nicht
     * `Locale.lookupTag`: Die Kette ist zwei Glieder lang, und welche
     * Schlüssel es überhaupt gibt, soll [RANGES] sagen und nicht die Nachsicht
     * des JDK.
     */
    val ranges: List<String> get() = listOf(tag, tag.substringBefore('-'))

    override fun toString(): String = tag

    companion object {
        /**
         * Jeder Sprachbereich, unter dem ein Text stehen darf: jede Sprache und
         * ihre Hauptsprache. Ein anderer – `fr`, `en-US` – gälte für keine
         * Rechnung und wäre derselbe stille Fehler wie ein vertippter
         * Schlüssel.
         */
        val RANGES: List<String> = entries.flatMap { it.ranges }.distinct()
    }
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
