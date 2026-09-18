package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.model.SellerConfig

/**
 * Die Blöcke, deren Inhalt allein aus den Stammdaten kommt: Briefkopf,
 * Fußzeilen und die Bankverbindung. An der Rechnungsart hängt hier nichts –
 * nur *ob* die Bankverbindung dasteht, entscheidet die gerechnete Rechnung
 * ([CalculatedInvoice.requestsPayment]), und die Entscheidung fällt dort,
 * nicht hier.
 */
internal object SellerBlocks {
    /**
     * Der Kontaktblock im Briefkopf: Anschrift, Telefon, E-Mail. Die Webseite
     * steht bewusst nicht darin – sie wiederholte nur die Domain der E-Mail und
     * ließ den Block wie eine Dopplung aussehen. Die E-Mail selbst gehört
     * hierher: Rückfragen zu einer Rechnung kommen meist schriftlich.
     */
    fun senderContact(config: SellerConfig): Latex =
        buildList {
            add("${config.address.street} · ${config.address.postalCode} ${config.address.city}".escapeLatex())
            config.phone?.let { add("Tel. ".escapeLatex() + it.escapeLatex()) }
            add(config.email.escapeLatex())
        }.joinToLatex(Latex.macro(TemplateMacro.ZEILENWECHSEL))

    /**
     * Die Person unter dem Firmennamen im Briefkopf – aber nur dann, wenn sie
     * nicht ohnehin schon als Geschäftsführung neben dem Empfänger steht.
     * Bei einer UG sind Inhaber und Geschäftsführung meist dieselbe Person;
     * beides zu setzen nennt sie zweimal im selben Seitenviertel.
     *
     * Für Einzelunternehmen ohne Geschäftsführung bleibt der Name hier stehen:
     * §14 Abs. 4 Nr. 1 UStG verlangt den vollständigen Namen des leistenden
     * Unternehmers, und der ist dort die Person, nicht die Firmierung.
     */
    fun letterheadPerson(config: SellerConfig): Latex =
        if (config.managingDirector != null) {
            Latex.EMPTY
        } else {
            Latex.macro(TemplateMacro.KOPFPERSON, listOf(config.ownerName.escapeLatex()))
        }

    /**
     * Die Bankverbindung – nur auf einem Dokument, das zur Zahlung auffordert.
     *
     * Ob es das tut, sagt [CalculatedInvoice.requestsPayment], dieselbe Stelle,
     * aus der das Zahlungsziel folgt. Nicht auf der Stornorechnung: Sie fordert
     * kein Geld, sie kündigt eine Erstattung an, und ein Konto darunter läse
     * sich wie eine Zahlungsaufforderung. Und nicht auf einer Schlussrechnung,
     * deren Abschläge den Betrag decken (#94) – aus demselben Grund. Die IBAN in
     * der Fußzeile bleibt auf beiden stehen.
     *
     * Überschrift und Abstand stecken im Makro der Vorlage, damit hier das Ganze
     * wegfallen kann und nicht eine Überschrift ohne Inhalt stehen bleibt.
     */
    fun bankDetails(calculatedInvoice: CalculatedInvoice): Latex {
        if (!calculatedInvoice.requestsPayment) return Latex.EMPTY
        return Latex.macro(TemplateMacro.BANKBLOCK, listOf(bankLines(calculatedInvoice.config)))
    }

    private fun bankLines(config: SellerConfig): Latex =
        listOf(
            "Kontoinhaber: ".escapeLatex() + config.bank.accountHolder.escapeLatex(),
            "Institut: ".escapeLatex() + config.bank.bankName.escapeLatex(),
            "IBAN: ".escapeLatex() +
                config.bank.iban
                    .formatted()
                    .escapeLatex(),
            "BIC: ".escapeLatex() +
                config.bank.bic.value
                    .escapeLatex(),
        ).joinToLatex(Latex.macro(TemplateMacro.ZEILENWECHSEL))

    /**
     * Der Fuß: alle Werte, die ein Design dort zeigen könnte, als Argumente
     * von `\fuss` (#188). Welche davon es zeigt, wo und mit welchem Trenner,
     * steht in der Vorlage. Vorher lieferte diese Datei zwei fertige Zeilen mit
     * ` · ` dazwischen, und ein Design mit drei Spalten hätte sie nicht
     * auseinandernehmen können.
     *
     * Ein fehlender Wert kommt als leeres Argument; die Vorlage fragt mit
     * `\IfBlankF` danach. Das ist keine fachliche Entscheidung, die dorthin
     * wandert – nur die Frage, ob die Stammdaten den Wert führen.
     */
    fun footer(config: SellerConfig): Latex =
        Latex.macro(
            TemplateMacro.FUSS,
            listOf(
                config.companyName.escapeLatex(),
                "${config.address.street}, ${config.address.postalCode} ${config.address.city}".escapeLatex(),
                config.registerCourt?.escapeLatex() ?: Latex.EMPTY,
                config.registerNumber?.escapeLatex() ?: Latex.EMPTY,
                config.vatId.formatted().escapeLatex(),
                config.taxNumber?.value?.escapeLatex() ?: Latex.EMPTY,
                config.bank.bankName.escapeLatex(),
                config.bank.bic.value
                    .escapeLatex(),
                config.bank.iban
                    .formatted()
                    .escapeLatex(),
            ),
        )
}
