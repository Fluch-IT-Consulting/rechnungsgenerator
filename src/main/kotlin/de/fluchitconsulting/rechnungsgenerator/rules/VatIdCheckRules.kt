package de.fluchitconsulting.rechnungsgenerator.rules

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.model.CheckMethod
import de.fluchitconsulting.rechnungsgenerator.model.Invoice
import de.fluchitconsulting.rechnungsgenerator.model.SellerConfig
import de.fluchitconsulting.rechnungsgenerator.values.EuMemberStates
import de.fluchitconsulting.rechnungsgenerator.values.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.values.VatIdConfirmation
import java.time.temporal.ChronoUnit

/**
 * Die Bestätigungsabfrage der USt-IdNr.: der Nachweis, der vor einer Rechnung
 * ohne Umsatzsteuer vorliegen muss, und die Hinweise dazu.
 *
 * **Welche** Kategorie was verlangt, steht am Enum: [VatIdConfirmation]
 * und die Werte in [TaxCategory]. Dort und nicht hier, weil es eine
 * Eigenschaft der Kategorie ist und keine dieser Regeln – vorher stand
 * die Fallunterscheidung als Konstantenvergleich im Calculator, und wer
 * wissen wollte, was für "G" gilt, musste ihn ganz lesen.
 *
 * Hier steht nur noch, wie daraus eine Pflicht wird: Der Nachweis muss
 * vorliegen, er muss vor dem Rechnungsdatum erbracht sein, und fehlt er,
 * bricht der Lauf ab.
 *
 * Das ist Steuerrecht und keine Rechtsberatung – wer die Regeln ändert,
 * sollte sie mit dem Steuerberater abgleichen.
 */
internal object VatIdCheckRules {
    fun check(calculated: CalculatedInvoice): List<Finding> =
        confirmation(calculated.invoice, calculated.config) + hints(calculated.invoice, calculated.config)

    private fun confirmation(invoice: Invoice, config: SellerConfig): List<Finding> =
        buildList {
            val kategorien =
                invoice.items
                    .map { it.taxCategory }
                    .filter { it.requiresCustomerVatId }
                    .toSet()
            if (kategorien.isEmpty()) return@buildList
            val kunde = invoice.customer
            val ausland = istAusland(invoice, config)
            val inEu = EuMemberStates.contains(kunde.address.countryCode)
            val pflicht =
                kategorien.any {
                    when (it.vatIdConfirmation) {
                        VatIdConfirmation.ALWAYS -> true
                        VatIdConfirmation.IN_EU -> ausland && inEu
                        VatIdConfirmation.NEVER -> false
                    }
                }
            if (!pflicht) return@buildList

            // Ohne Nachweis entfällt auch die Frage nach seinem Datum – deshalb
            // hier das frühe Weiterspringen.
            val pruefung = kunde.vatIdCheck
            if (pruefung == null) {
                add(
                    Finding(
                        Rule.BESTAETIGUNGSABFRAGE_FEHLT,
                        Subject.WholeInvoice,
                        "Ohne dokumentierte Bestätigungsabfrage keine Rechnung ohne Umsatzsteuer. " +
                            "Bitte die USt-IdNr. ${kunde.vatId} beim BZSt oder über VIES prüfen und das " +
                            "Ergebnis unter 'kunde: ust-id-geprueft' eintragen (am, verfahren, nachweis).",
                    ),
                )
                return@buildList
            }

            // Eine Abfrage nach dem Rechnungsdatum belegt für diese Rechnung nichts.
            if (pruefung.date.isAfter(invoice.issueDate)) {
                add(
                    Finding(
                        Rule.BESTAETIGUNGSABFRAGE_NACH_RECHNUNGSDATUM,
                        Subject.WholeInvoice,
                        "Die Bestätigungsabfrage vom ${pruefung.date} liegt nach dem Rechnungsdatum " +
                            "${invoice.issueDate} – für diese Rechnung belegt sie nichts.",
                    ),
                )
            }
        }

    /** Hinweise, die den Lauf nicht abbrechen, aber in die eigene Akte gehören. */
    private fun hints(invoice: Invoice, config: SellerConfig): List<Finding> =
        buildList {
            val kunde = invoice.customer
            val kategorien = invoice.items.map { it.taxCategory }.toSet()
            val ausland = istAusland(invoice, config)

            // Die Altersprüfung der EU-Liste hängt bewusst an `ausland` und läuft
            // nicht immer: Bei einer Inlandsrechnung wird die Liste gar nicht
            // befragt, ein Hinweis darauf wäre also Lärm. Und Hinweise, die bei
            // jeder Rechnung erscheinen, liest nach der dritten niemand mehr –
            // dann geht der eine unter, auf den es ankommt.
            if (ausland) {
                val listenAlter = ChronoUnit.MONTHS.between(EuMemberStates.AS_OF, invoice.issueDate)
                if (listenAlter >= 12) {
                    add(
                        Finding(
                            Rule.EU_LISTE_ALT,
                            Subject.WholeInvoice,
                            "Die hinterlegte Liste der EU-Mitgliedstaaten ist $listenAlter Monate alt " +
                                "(zuletzt geprüft am ${EuMemberStates.AS_OF}). Sie entscheidet mit, ob für " +
                                "${kunde.address.countryCode} ein Nachweis verlangt wird – bitte gegen die " +
                                "amtliche Aufstellung abgleichen und EuMemberStates.AS_OF nachziehen.",
                        ),
                    )
                }
            }

            // Eine Kategorie, deren Nachweis nur innerhalb der EU zu führen
            // ist, trifft hier auf einen Kunden ausserhalb: Verlangt wird dann
            // nichts (siehe confirmation), aber die Akte braucht trotzdem
            // etwas.
            val nurInEu = kategorien.any { it.vatIdConfirmation == VatIdConfirmation.IN_EU }
            if (nurInEu && ausland && !EuMemberStates.contains(kunde.address.countryCode)) {
                add(
                    Finding(
                        Rule.KEIN_VIES_MOEGLICH,
                        Subject.WholeInvoice,
                        "${kunde.address.countryCode} ist kein EU-Mitgliedstaat – eine VIES-Abfrage gibt es dafür " +
                            "nicht. Für die Akte braucht es einen anderen Nachweis der Unternehmereigenschaft, " +
                            "etwa eine Unternehmerbescheinigung.",
                    ),
                )
            }
            kunde.vatIdCheck?.let { pruefung ->
                if (pruefung.method == CheckMethod.SIMPLE) {
                    add(
                        Finding(
                            Rule.ABFRAGE_EINFACH,
                            Subject.WholeInvoice,
                            "Die Abfrage vom ${pruefung.date} war einfach, nicht qualifiziert. Nur die " +
                                "qualifizierte Abfrage beim BZSt gleicht Name und Anschrift ab und verschafft " +
                                "Vertrauensschutz.",
                        ),
                    )
                }
                if (pruefung.reference == null) {
                    add(
                        Finding(
                            Rule.ABFRAGE_OHNE_NACHWEIS,
                            Subject.WholeInvoice,
                            "Zur Abfrage vom ${pruefung.date} ist kein Nachweis vermerkt – " +
                                "ohne Protokoll nützt sie im Zweifel wenig.",
                        ),
                    )
                }
                // Nur, wo eine Leistung abgerechnet wird: Das Storno wiederholt die
                // Abfrage der Ursprungsrechnung, und zu der musste sie passen. Eine
                // frische Abfrage für ein Dokument zu verlangen, das den Umsatz
                // zurücknimmt, hieße Arbeit ohne Zweck.
                val alter = ChronoUnit.MONTHS.between(pruefung.date, invoice.issueDate)
                if (invoice.type.chargesForService && alter >= 12) {
                    add(
                        Finding(
                            Rule.ABFRAGE_ALT,
                            Subject.WholeInvoice,
                            "Die Bestätigungsabfrage ist $alter Monate alt. Bei laufender Geschäftsbeziehung " +
                                "wird üblicherweise regelmäßig erneut geprüft.",
                        ),
                    )
                }
            }
        }

    /**
     * Ausland heißt hier: Das Land des Kunden ist nicht das Land des
     * Rechnungsstellers – nicht "nicht Deutschland". Die beiden fallen nur
     * zusammen, solange der Rechnungssteller in Deutschland sitzt, und genau
     * diese Verwechslung stand vorher im Renderer.
     *
     * Beide Teile dieser Gruppe fragen danach. Der Vergleich selbst ist ein
     * schlichtes `!=`, weil `CountryCode` die Schreibweise vereinheitlicht.
     */
    private fun istAusland(invoice: Invoice, config: SellerConfig): Boolean =
        invoice.customer.address.countryCode != config.address.countryCode
}
