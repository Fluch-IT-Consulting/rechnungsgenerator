package de.fluchitconsulting.rechnungsgenerator.generate.einvoice

import de.fluchitconsulting.rechnungsgenerator.core.text.TextTemplate
import de.fluchitconsulting.rechnungsgenerator.core.text.TextValue
import de.fluchitconsulting.rechnungsgenerator.core.text.VariableResolver
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import java.time.format.DateTimeFormatter

// Wie auf dem Blatt, damit Notiz und PDF denselben Satz lesen lassen, aber
// mit gewöhnlichem Leerzeichen: Im XML setzt niemand, und ein geschütztes
// Leerzeichen wäre nur ein Zeichen, über das ein Empfänger stolpert.
//
// Ein eigenes Muster und nicht das DateFormat des Blatts: Das liegt in
// generate.render und liefert LaTeX. Ohne `else`, wie dort.
private fun datePattern(language: Language): DateTimeFormatter =
    when (language) {
        Language.GERMAN -> DateTimeFormatter.ofPattern("d. MMMM yyyy", language.locale)
        Language.BRITISH_ENGLISH -> DateTimeFormatter.ofPattern("d MMMM yyyy", language.locale)
        Language.AMERICAN_ENGLISH -> DateTimeFormatter.ofPattern("MMMM d, yyyy", language.locale)
    }

/**
 * Ein Text mit Platzhaltern als schlichter Text, für eine Notiz der
 * E-Rechnung – das Format neben `toLatex` (ADR-0019), ein Datum in
 * [language], der Sprache des Wortlauts. Nichts wird maskiert: Das tut der
 * XML-Writer.
 */
internal fun <V> TextTemplate<V>.toPlainText(
    language: Language,
    resolver: VariableResolver<V>,
): String =
    map(
        text = { it },
        variable = { variable ->
            when (val value = resolver.resolve(variable)) {
                is TextValue.Text -> value.text
                is TextValue.Date -> datePattern(language).format(value.date)
            }
        },
    ).joinToString("")
