package de.fluchitconsulting.rechnungsgenerator.core.values

import de.fluchitconsulting.rechnungsgenerator.core.serialization.enumSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Die Währung einer Rechnung, als Code nach ISO 4217 (#409).
 *
 * Eine geschlossene Menge wie [Language]: Eine Währung gibt es erst, wenn jede
 * Sprachdatei jedes Designs ihr Zeichen kennt. Ein beliebiger Code ergäbe
 * sonst einen Betrag, vor oder hinter dem nichts steht.
 *
 * Die Währung gehört zur Rechnung und nicht zum Betrag: Jeder Betrag einer
 * Rechnung ist in ihrer Währung, deshalb tragen [Money] und [Price] keine.
 */
@Serializable(with = CurrencySerializer::class)
enum class Currency {
    EUR,
    USD,
    GBP,
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
