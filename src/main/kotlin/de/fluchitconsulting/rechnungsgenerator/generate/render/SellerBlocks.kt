package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice

/**
 * Die Bankverbindung, der einzige Block aus den Stammdaten, über dessen
 * Erscheinen der Renderer entscheidet.
 *
 * Die übrigen Stammdaten kommen seit #188 einzeln in die Vorlage (siehe
 * [TemplateValue]), und wie Briefkopf und Fuß daraus werden, mit welchem
 * Trenner und welcher Beschriftung, entscheidet sie. Vorher standen hier
 * Satzbild-Entscheidungen eines Designs: der Fuß ohne Steuernummer, der Kopf
 * ohne Webseite, der Trenner ` · `.
 */
internal object SellerBlocks {
    /**
     * Die Bankverbindung – nur auf einem Dokument, das zur Zahlung auffordert.
     *
     * Ob es das tut, sagt [CalculatedInvoice.requestsPayment], dieselbe Stelle,
     * aus der das Zahlungsziel folgt. Nicht auf der Stornorechnung: Sie fordert
     * kein Geld, sie kündigt eine Erstattung an, und ein Konto darunter läse
     * sich wie eine Zahlungsaufforderung. Und nicht auf einer Schlussrechnung,
     * deren Abschläge den Betrag decken (#94) – aus demselben Grund. Die IBAN im
     * Fuß bleibt auf beiden stehen.
     *
     * Überschrift, Abstand und Inhalt stecken im Makro der Vorlage, damit hier
     * das Ganze wegfallen kann und nicht eine Überschrift ohne Inhalt stehen
     * bleibt.
     */
    fun bankDetails(calculatedInvoice: CalculatedInvoice): Latex {
        if (!calculatedInvoice.requestsPayment) return Latex.EMPTY
        return Latex.macro(TemplateMacro.BANKBLOCK)
    }
}
