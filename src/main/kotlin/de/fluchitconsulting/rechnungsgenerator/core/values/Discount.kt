package de.fluchitconsulting.rechnungsgenerator.core.values

/**
 * Der Rabatt einer Position – EN 16931 BG-27, ein Abschlag auf den
 * Positionsbetrag und nicht auf den Einzelpreis (ADR-0013).
 *
 * Versiegelt statt zweier Felder nebeneinander: Wer einen Rabatt auswertet,
 * entscheidet in einem `when` ohne `else`, und eine weitere Form hält der
 * Compiler an jeder dieser Stellen an – dasselbe Muster wie bei der
 * Rechnungsart (#121, #128).
 *
 * Gebaut wird er im Modell aus dem, was in der YAML steht; die Schlüssel dort
 * sind eine Form der Eingabe, der Rabatt ist die Sache.
 */
sealed interface Discount {
    /**
     * Der vereinbarte Satz (BT-138). Den Betrag rechnet die Position daraus –
     * er steht in der YAML nicht und wäre dort eine zweite Wahrheit.
     */
    data class Rate(val rate: Percent) : Discount

    /**
     * Der vereinbarte Betrag (BT-136). Er steht positiv da; in welche Richtung
     * er wirkt, sagt die Position – im Storno mit negativer Menge (ADR-0006)
     * ist die Basis negativ, und der Rabatt mindert sie spiegelbildlich.
     */
    data class Amount(val amount: Money) : Discount
}
