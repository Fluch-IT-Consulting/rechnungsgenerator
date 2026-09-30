package de.fluchitconsulting.rechnungsgenerator.core.values

import de.fluchitconsulting.rechnungsgenerator.core.serialization.enumSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * ISO 4217, als geschlossene Menge: Eine Währung gibt es erst, wenn jede Sprachdatei ihr Zeichen kennt (ADR-0026).
 * Sie gehört zur Rechnung, nicht zum Betrag.
 */
@Serializable(with = CurrencySerializer::class)
enum class Currency {
    EUR,
    USD,
    GBP,
    PLN,
    CZK,
    RON,
    SEK,
    NOK,
    CHF,
}

object CurrencySerializer :
    KSerializer<Currency> by enumSerializer(
        name = "Currency",
        values = Currency.entries,
        unknown = "Unbekannte Währung",
        // Die Norm schreibt die Codes groß, getippt wird auch klein.
        normalize = { it.trim().uppercase() },
        keyOf = Currency::name,
    )
