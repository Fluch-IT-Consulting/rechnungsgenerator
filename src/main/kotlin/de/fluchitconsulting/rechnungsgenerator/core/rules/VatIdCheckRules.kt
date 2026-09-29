package de.fluchitconsulting.rechnungsgenerator.core.rules

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.calculation.EuMemberStates
import de.fluchitconsulting.rechnungsgenerator.core.model.CheckMethod
import de.fluchitconsulting.rechnungsgenerator.core.model.VatIdConfirmation
import java.time.temporal.ChronoUnit

/**
 * Wie aus [VatIdConfirmation] eine Pflicht wird; welche Kategorie was verlangt, steht am Enum. Das ist
 * Steuerrecht und keine Rechtsberatung: Wer die Regeln ändert, gleicht sie mit dem Steuerberater ab.
 */
internal object VatIdCheckRules {
    fun check(calculated: CalculatedInvoice): List<Finding> = confirmation(calculated) + hints(calculated)

    private fun confirmation(calculated: CalculatedInvoice): List<Finding> =
        buildList {
            val invoice = calculated.invoice
            // Über den Nachweis und nicht über requiresCustomerVatId: Das Papier ist eine andere Pflicht (#148).
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

    private fun hints(calculated: CalculatedInvoice): List<Finding> =
        buildList {
            val invoice = calculated.invoice
            val customer = invoice.customer
            val categories = calculated.taxCategories

            // Nur im Ausland, denn nur dort wird die Liste befragt; ein Hinweis bei jeder Rechnung überläse man bald.
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

            // Außerhalb der EU wird nichts verlangt, aber die Akte braucht trotzdem etwas.
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
                // Nicht beim Storno: Die Abfrage musste zur Ursprungsrechnung passen.
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
