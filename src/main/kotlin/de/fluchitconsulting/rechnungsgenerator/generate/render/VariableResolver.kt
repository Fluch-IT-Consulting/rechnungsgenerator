package de.fluchitconsulting.rechnungsgenerator.generate.render

/**
 * Löst die Platzhalter eines Textes auf: aus `<<NAME>>` wird ein fertiges
 * [Latex]-Fragment.
 *
 * Das Ergebnis ist nie `null`. Kann eine Implementierung einen Namen nicht
 * auflösen, wirft sie – und nur sie kann dabei sagen, warum: ob der Name
 * vertippt ist oder zu diesem Dokument einfach kein Wert gehört. Eine
 * Rechnungsdatei kann das nicht auslösen; die Texte mit Platzhaltern stehen im
 * Quelltext, also ist es ein Programmfehler.
 *
 * `fun interface`: ein Interface mit genau einer abstrakten Methode. Dafür
 * lässt sich auch ein Lambda übergeben – `text.escapeLatex { name -> ... }` –,
 * ohne eine Klasse zu schreiben ("SAM-Konvertierung").
 */
fun interface VariableResolver {
    fun resolve(variable: String): Latex
}
