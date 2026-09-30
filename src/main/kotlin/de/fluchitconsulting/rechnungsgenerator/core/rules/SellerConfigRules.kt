package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice

/** Die Angaben des Rechnungsstellers, die nur zusammen einen Sinn ergeben. */
internal object SellerConfigRules {
    fun check(calculated: CalculatedInvoice): List<Finding> = listOfNotNull(registerDetails(calculated))

    /**
     * §35a GmbHG verlangt Gericht und Nummer. Die Vorlagen setzen die Nummer nur hinter dem Gericht, BT-30 trüge sie
     * trotzdem: Blatt und XML sagten Verschiedenes (#505).
     */
    private fun registerDetails(calculated: CalculatedInvoice): Finding? {
        val config = calculated.config
        val court = config.registerCourt
        val number = config.registerNumber
        val message =
            when {
                court == null && number != null -> {
                    "Die Stammdaten nennen die Handelsregisternummer '$number', aber kein Registergericht. " +
                        "'registergericht' ergänzen; steht beides in einer Zeile, gehört das Gericht dorthin."
                }
                court != null && number == null -> {
                    "Die Stammdaten nennen das Registergericht '$court', aber keine Handelsregisternummer. " +
                        "'handelsregister-nummer' ergänzen; steht beides in einer Zeile, gehört die Nummer dorthin."
                }
                else -> {
                    return null
                }
            }
        return Finding(Rule.REGISTERANGABEN_UNVOLLSTAENDIG, Subject.WholeInvoice, "$message §35a GmbHG verlangt beides.")
    }
}
