package de.fluchitconsulting.rechnungsgenerator.core.calculation

import de.fluchitconsulting.rechnungsgenerator.core.values.CountryCode
import java.time.LocalDate

/**
 * Die Mitgliedstaaten der EU. Nur für sie lässt sich eine USt-IdNr. über VIES oder das BZSt bestätigen.
 *
 * GR und EL stehen beide da: ISO 3166 sagt GR, die Umsatzsteuer der EU EL.
 * Nordirland (XI) fehlt bewusst, sein Sonderstatus betrifft nur den Warenverkehr.
 */
object EuMemberStates {
    // Datum der letzten Prüfung gegen die amtliche Aufstellung, nicht der letzten Änderung – beim Prüfen
    // mitsetzen. Ein Test und ein Hinweis zur Laufzeit melden, wenn es veraltet.
    val AS_OF: LocalDate = LocalDate.of(2026, 8, 30)

    // Als CountryCode: Ein verformter Eintrag scheitert beim ersten Zugriff, statt einen Staat still zum Drittland zu machen.
    private val CODES =
        listOf(
            "AT",
            "BE",
            "BG",
            "CY",
            "CZ",
            "DE",
            "DK",
            "EE",
            "ES",
            "FI",
            "FR",
            "GR",
            "EL",
            "HR",
            "HU",
            "IE",
            "IT",
            "LT",
            "LU",
            "LV",
            "MT",
            "NL",
            "PL",
            "PT",
            "RO",
            "SE",
            "SI",
            "SK",
        ).map(::CountryCode).toSet()

    fun contains(country: CountryCode): Boolean = CODES.contains(country)
}
