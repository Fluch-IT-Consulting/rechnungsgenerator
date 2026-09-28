package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.calculation.EuMemberStates
import de.fluchitconsulting.rechnungsgenerator.core.model.CheckMethod
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.model.VatIdConfirmation
import java.time.temporal.ChronoUnit

/**
 * Die Bestätigungsabfrage der USt-IdNr.: der Nachweis, der vor einer Rechnung
 * ohne Umsatzsteuer vorliegen muss, und die Hinweise dazu.
 *
 * **Welche** Kategorie was verlangt, steht am Enum: [VatIdConfirmation]
 * und die Werte in [TaxCategory]. Dort und nicht hier, weil es eine
 * Eigenschaft der Kategorie ist und keine dieser Regeln: Stünde die
 * Fallunterscheidung hier, müsste ganz lesen, wer wissen will, was für "G"
 * gilt.
 *
 * Hier steht nur noch, wie daraus eine Pflicht wird: Der Nachweis muss
 * vorliegen, er muss vor dem Rechnungsdatum erbracht sein, und fehlt er,
 * bricht der Lauf ab. Ob der Kunde im Ausland und ob er in der EU sitzt,
 * fragen die Regeln die gerechnete Rechnung ([CalculatedInvoice.isDomestic],
 * [CalculatedInvoice.customerInEu]) – der Renderer fragt dasselbe (#120).
 *
 * Das ist Steuerrecht und keine Rechtsberatung – wer die Regeln ändert,
 * sollte sie mit dem Steuerberater abgleichen.
 */
internal object VatIdCheckRules {
    fun check(calculated: CalculatedInvoice): List<Finding> = confirmation(calculated) + hints(calculated)

    private fun confirmation(calculated: CalculatedInvoice): List<Finding> =
        buildList {
            val invoice = calculated.invoice
            // Ausgewählt wird über den Nachweis und nicht über
            // requiresCustomerVatId: Das eine ist eine Angabe auf dem Papier,
            // das andere diese Pflicht. Stünde hier das Papier, bliebe eine
            // Kategorie mit Nachweis und ohne Angabe still ungeprüft – heute
            // ohne Folgen, weil beide bei AE und K anschlagen (#148).
            val categories = calculated.taxCategories.filter { it.vatIdConfirmation != VatIdConfirmation.NEVER }
            if (categories.isEmpty()) return@buildList
            val customer = invoice.customer
            val required =
                categories.any {
                    when (it.vatIdConfirmation) {
                        VatIdConfirmation.ALWAYS -> true
                        VatIdConfirmation.IN_EU -> !calculated.isDomestic && calculated.customerInEu
                        VatIdConfirmation.NEVER -> false
                    }
                }
            if (!required) return@buildList

            // Ohne Nachweis entfällt auch die Frage nach seinem Datum – deshalb
            // hier das frühe Weiterspringen.
            val vatIdCheck = customer.vatIdCheck
            if (vatIdCheck == null) {
                add(
                    Finding(
                        Rule.BESTAETIGUNGSABFRAGE_FEHLT,
                        Subject.WholeInvoice,
                        "Ohne dokumentierte Bestätigungsabfrage keine Rechnung ohne Umsatzsteuer. " +
                            "Bitte die USt-IdNr. ${customer.vatId} beim BZSt oder über VIES prüfen und das " +
                            "Ergebnis unter 'kunde: ust-id-geprueft' eintragen (am, verfahren, nachweis).",
                    ),
                )
                return@buildList
            }

            // Eine Abfrage nach dem Rechnungsdatum belegt für diese Rechnung nichts.
            if (vatIdCheck.date.isAfter(invoice.issueDate)) {
                add(
                    Finding(
                        Rule.BESTAETIGUNGSABFRAGE_NACH_RECHNUNGSDATUM,
                        Subject.WholeInvoice,
                        "Die Bestätigungsabfrage vom ${vatIdCheck.date} liegt nach dem Rechnungsdatum " +
                            "${invoice.issueDate} – für diese Rechnung belegt sie nichts.",
                    ),
                )
            }
        }

    /** Hinweise, die den Lauf nicht abbrechen, aber in die eigene Akte gehören. */
    private fun hints(calculated: CalculatedInvoice): List<Finding> =
        buildList {
            val invoice = calculated.invoice
            val customer = invoice.customer
            val categories = calculated.taxCategories

            // Die Altersprüfung der EU-Liste hängt bewusst am Ausland und läuft
            // nicht immer: Bei einer Inlandsrechnung wird die Liste gar nicht
            // befragt, ein Hinweis darauf wäre also Lärm. Und Hinweise, die bei
            // jeder Rechnung erscheinen, liest nach der dritten niemand mehr –
            // dann geht der eine unter, auf den es ankommt.
            if (!calculated.isDomestic) {
                val listAge = ChronoUnit.MONTHS.between(EuMemberStates.AS_OF, invoice.issueDate)
                if (listAge >= 12) {
                    add(
                        Finding(
                            Rule.EU_LISTE_ALT,
                            Subject.WholeInvoice,
                            "Die hinterlegte Liste der EU-Mitgliedstaaten ist $listAge Monate alt " +
                                "(zuletzt geprüft am ${EuMemberStates.AS_OF}). Sie entscheidet mit, ob für " +
                                "${customer.address.countryCode} ein Nachweis verlangt wird – bitte gegen die " +
                                "amtliche Aufstellung abgleichen und EuMemberStates.AS_OF nachziehen.",
                        ),
                    )
                }
            }

            // Eine Kategorie, deren Nachweis nur innerhalb der EU zu führen
            // ist, trifft hier auf einen Kunden ausserhalb: Verlangt wird dann
            // nichts (siehe confirmation), aber die Akte braucht trotzdem
            // etwas.
            val onlyInEu = categories.any { it.vatIdConfirmation == VatIdConfirmation.IN_EU }
            if (onlyInEu && !calculated.isDomestic && !calculated.customerInEu) {
                add(
                    Finding(
                        Rule.KEIN_VIES_MOEGLICH,
                        Subject.WholeInvoice,
                        "${customer.address.countryCode} ist kein EU-Mitgliedstaat – eine VIES-Abfrage gibt es dafür " +
                            "nicht. Für die Akte braucht es einen anderen Nachweis der Unternehmereigenschaft, " +
                            "etwa eine Unternehmerbescheinigung.",
                    ),
                )
            }
            customer.vatIdCheck?.let { vatIdCheck ->
                if (vatIdCheck.method == CheckMethod.SIMPLE) {
                    add(
                        Finding(
                            Rule.ABFRAGE_EINFACH,
                            Subject.WholeInvoice,
                            "Die Abfrage vom ${vatIdCheck.date} war einfach, nicht qualifiziert. Nur die " +
                                "qualifizierte Abfrage beim BZSt gleicht Name und Anschrift ab und verschafft " +
                                "Vertrauensschutz.",
                        ),
                    )
                }
                if (vatIdCheck.reference == null) {
                    add(
                        Finding(
                            Rule.ABFRAGE_OHNE_NACHWEIS,
                            Subject.WholeInvoice,
                            "Zur Abfrage vom ${vatIdCheck.date} ist kein Nachweis vermerkt – " +
                                "ohne Protokoll nützt sie im Zweifel wenig.",
                        ),
                    )
                }
                // Nur, wo eine Leistung abgerechnet wird: Das Storno wiederholt die
                // Abfrage der Ursprungsrechnung, und zu der musste sie passen. Eine
                // frische Abfrage für ein Dokument zu verlangen, das den Umsatz
                // zurücknimmt, hieße Arbeit ohne Zweck.
                val checkAge = ChronoUnit.MONTHS.between(vatIdCheck.date, invoice.issueDate)
                if (invoice.type.chargesForService && checkAge >= 12) {
                    add(
                        Finding(
                            Rule.ABFRAGE_ALT,
                            Subject.WholeInvoice,
                            "Die Bestätigungsabfrage ist $checkAge Monate alt. Bei laufender Geschäftsbeziehung " +
                                "wird üblicherweise regelmäßig erneut geprüft.",
                        ),
                    )
                }
            }
        }
}
