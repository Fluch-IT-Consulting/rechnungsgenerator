package de.fluchitconsulting.rechnungsgenerator.core.text

/** Nie `null`: Hat eine Variable keinen Wert, wirft die Implementierung, denn nur sie kann sagen, warum. */
fun interface VariableResolver<in V> {
    fun resolve(variable: V): TextValue
}
