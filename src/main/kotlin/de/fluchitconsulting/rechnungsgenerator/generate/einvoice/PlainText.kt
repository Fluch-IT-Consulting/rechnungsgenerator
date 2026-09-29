package de.fluchitconsulting.rechnungsgenerator.generate.einvoice

import de.fluchitconsulting.rechnungsgenerator.core.text.TextTemplate
import de.fluchitconsulting.rechnungsgenerator.core.text.TextValue
import de.fluchitconsulting.rechnungsgenerator.core.text.VariableResolver
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import java.time.format.DateTimeFormatter

// Mit gewöhnlichem Leerzeichen, im XML setzt niemand. Ein eigenes Muster, weil DateFormat LaTeX liefert.
private fun datePattern(language: Language): DateTimeFormatter =
    when (language) {
        Language.GERMAN -> DateTimeFormatter.ofPattern("d. MMMM yyyy", language.locale)
        Language.BRITISH_ENGLISH -> DateTimeFormatter.ofPattern("d MMMM yyyy", language.locale)
        Language.AMERICAN_ENGLISH -> DateTimeFormatter.ofPattern("MMMM d, yyyy", language.locale)
    }

/** Das Format neben `toLatex` (ADR-0019). Nichts wird maskiert, das tut der XML-Writer. */
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
