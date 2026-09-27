package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.values.Currency

/**
 * Die Währung (#409): Eine Rechnung in Fremdwährung weist keine Umsatzsteuer
 * aus.
 *
 * Die geschuldete Steuer muss in Euro dastehen (Art. 230 MwStSystRL),
 * umgerechnet nach §16 Abs. 6 UStG – mit einem Kurs, der beim Erzeugen oft
 * noch nicht feststeht, etwa bei einer Abschlagsrechnung oder wer nach
 * vereinnahmten Entgelten versteuert. Den Kurs kennt der Generator nicht,
 * also lehnt er ab, statt einen Steuerbetrag ohne Euro zu setzen.
 *
 * Gefragt wird nach der Kategorie S und nicht nach dem Steuerbetrag: Sie ist
 * die einzige mit einem Satz über null (REGELSATZ_MIT_NULL), und ein Betrag
 * von null – etwa eine Position über 0,00 – machte die Rechnung sonst nur
 * zufällig zulässig.
 */
internal object CurrencyRules {
    fun check(calculated: CalculatedInvoice): List<Finding> {
        val invoice = calculated.invoice
        if (invoice.currency == Currency.EUR) return emptyList()

        val taxed =
            invoice.items
                .withIndex()
                .filter { (_, item) -> item.taxCategory == TaxCategory.STANDARD }
                .map { (index, _) -> index + 1 }
        if (taxed.isEmpty()) return emptyList()

        // Ein Befund für die Rechnung und nicht je Position: Der Grund ist
        // die Währung, und bei einer versehentlichen Inlandsrechnung in USD
        // stünde derselbe Satz sonst für jede Position da.
        val positions = if (taxed.size == 1) "Position ${taxed.single()}" else "den Positionen ${taxed.joinToString()}"
        return listOf(
            Finding(
                Rule.WAEHRUNG_MIT_UMSATZSTEUER,
                Subject.WholeInvoice,
                "Eine Rechnung in ${invoice.currency} weist keine Umsatzsteuer aus, Kategorie S steht aber " +
                    "in $positions. Die Steuer müsste umgerechnet in Euro dastehen, und das kann der " +
                    "Generator nicht. Bitte in EUR abrechnen oder die Kategorie prüfen.",
            ),
        )
    }
}
