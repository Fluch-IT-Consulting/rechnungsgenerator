package de.fluchitconsulting.rechnungsgenerator.core.values

import de.fluchitconsulting.rechnungsgenerator.core.serialization.InvalidValueException
import de.fluchitconsulting.rechnungsgenerator.core.serialization.stringSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.util.regex.PatternSyntaxException

class InvalidInvoiceNumberPatternException(message: String) : InvalidValueException(message)

/**
 * Ein regulärer Ausdruck aus den Stammdaten (ADR-0005). Er muss die ganze Nummer überdecken ([Regex.matches]),
 * Anker sind also überflüssig. Die Nummer selbst prüft `InvoiceNumberRules`, hier nur, ob es ein Ausdruck ist.
 */
@Serializable(with = InvoiceNumberPatternSerializer::class)
class InvoiceNumberPattern(val value: String) {
    private val regex: Regex =
        try {
            Regex(value)
        } catch (e: PatternSyntaxException) {
            // Die Meldung zeigt mit `^` auf die schadhafte Stelle.
            throw InvalidInvoiceNumberPatternException("Kein gültiger regulärer Ausdruck für Rechnungsnummern: '$value'\n${e.message}")
        }

    fun matches(number: String): Boolean = regex.matches(number)

    // Regex hat kein equals – verglichen wird der Ausdruck selbst.
    override fun equals(other: Any?): Boolean = other is InvoiceNumberPattern && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}

object InvoiceNumberPatternSerializer :
    KSerializer<InvoiceNumberPattern> by stringSerializer("InvoiceNumberPattern", { it.value }, ::InvoiceNumberPattern)
