package de.fluchitconsulting.rechnungsgenerator.core.values

import de.fluchitconsulting.rechnungsgenerator.core.serialization.stringSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.util.regex.PatternSyntaxException

/**
 * Das Muster, dem die Rechnungsnummern eines Rechnungsstellers genügen – ein
 * regulärer Ausdruck aus den Stammdaten (ADR-0005).
 *
 * Warum das Muster nicht im Code steht: `R26-009` ist ein Format unter vielen.
 * UStAE 14.5 Abs. 10 erlaubt Zahlen- und Buchstabenreihen, Abs. 11 überlässt
 * dem Rechnungssteller, wie viele Nummernkreise er führt. Ein fest eingebautes
 * Schema schriebe das eigene allen vor.
 *
 * Geprüft wird gegen die **ganze** Nummer: [Regex.matches] verlangt, dass der
 * Ausdruck sie vollständig überdeckt. Anker (`^` und `$`) sind deshalb erlaubt,
 * aber überflüssig – `R\d{2}-\d{3}` lässt kein `XR26-009` durch.
 *
 * Was dieser Typ **nicht** prüft, ist die Nummer: Ob eine Rechnung zum Muster
 * ihrer Stammdaten passt, hängt an zwei Dateien, und der Serialisierer sieht
 * beim Einlesen immer nur eine. Das erledigt `InvoiceNumberRules`. Hier
 * steht nur die Frage, ob der eingetragene Ausdruck überhaupt einer ist.
 */
@Serializable(with = InvoiceNumberPatternSerializer::class)
class InvoiceNumberPattern(val value: String) {
    // Einmal übersetzt, nicht bei jeder Prüfung neu. `private val` mit
    // Initialisierer statt `init`-Block: Der Wert entsteht beim Bauen, und ein
    // ungültiger Ausdruck kommt damit gar nicht erst durch den Konstruktor.
    private val regex: Regex =
        try {
            Regex(value)
        } catch (e: PatternSyntaxException) {
            // Die Meldung der Ausnahme zeigt mit einem `^` auf die schadhafte
            // Stelle – brauchbarer als alles, was hier neu formuliert würde.
            throw InvalidIdentifierException("Kein gültiger regulärer Ausdruck für Rechnungsnummern: '$value'\n${e.message}")
        }

    /** Ob eine Rechnungsnummer dem Muster genügt. */
    fun matches(number: String): Boolean = regex.matches(number)

    // Regex hat kein equals – verglichen wird der Ausdruck selbst.
    override fun equals(other: Any?): Boolean = other is InvoiceNumberPattern && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}

object InvoiceNumberPatternSerializer :
    KSerializer<InvoiceNumberPattern> by stringSerializer(
        "InvoiceNumberPattern",
        { it.value },
        {
            try {
                InvoiceNumberPattern(it)
            } catch (e: InvalidIdentifierException) {
                throw SerializationException(e.message, e)
            }
        },
    )
