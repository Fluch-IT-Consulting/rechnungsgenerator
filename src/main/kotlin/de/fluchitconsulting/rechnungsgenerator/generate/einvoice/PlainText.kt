package de.fluchitconsulting.rechnungsgenerator.generate.einvoice

import de.fluchitconsulting.rechnungsgenerator.core.text.TextTemplate
import de.fluchitconsulting.rechnungsgenerator.core.text.TextValue
import de.fluchitconsulting.rechnungsgenerator.core.text.VariableResolver
import java.time.format.DateTimeFormatter
import java.util.Locale

// Wie auf dem Blatt, damit Notiz und PDF denselben Satz lesen lassen, aber
// mit gewöhnlichem Leerzeichen: Im XML setzt niemand, und ein geschütztes
// Leerzeichen wäre nur ein Zeichen, über das ein Empfänger stolpert.
private val DATE = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMANY)

/**
 * Ein Text mit Platzhaltern als schlichter Text, für eine Notiz der
 * E-Rechnung – das Format neben `toLatex` (ADR-0019). Nichts wird maskiert:
 * Das tut der XML-Writer.
 */
internal fun <V> TextTemplate<V>.toPlainText(resolver: VariableResolver<V>): String =
    map(
        text = { it },
        variable = { variable ->
            when (val value = resolver.resolve(variable)) {
                is TextValue.Text -> value.text
                is TextValue.Date -> DATE.format(value.date)
            }
        },
    ).joinToString("")
