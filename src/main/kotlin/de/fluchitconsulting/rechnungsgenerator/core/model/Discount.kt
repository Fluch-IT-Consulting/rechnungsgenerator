package de.fluchitconsulting.rechnungsgenerator.core.model

import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent

/** BG-27, auf den Positionsbetrag und nicht den Einzelpreis; genau eines von Satz und Betrag (ADR-0013). */
sealed interface Discount {
    /** BT-138 */
    data class Rate(val rate: Percent) : Discount

    /** BT-136, positiv; die Richtung bestimmt die Basis der Position, im Storno also spiegelbildlich. */
    data class Amount(val amount: Money) : Discount
}
