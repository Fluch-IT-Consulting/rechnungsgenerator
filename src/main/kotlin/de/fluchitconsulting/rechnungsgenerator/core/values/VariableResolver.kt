package de.fluchitconsulting.rechnungsgenerator.core.values

/**
 * Liefert zu jeder Variablen eines Vokabulars [V] ihren Wert – woher er kommt,
 * nicht, wie er aussieht.
 *
 * Das Ergebnis ist nie `null`. Hat eine Variable in diesem Zusammenhang keinen
 * Wert, wirft die Implementierung, denn nur sie kann sagen, warum.
 */
fun interface VariableResolver<in V> {
    fun resolve(variable: V): TextValue
}
