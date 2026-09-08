package de.fluchitconsulting.rechnung.values

/**
 * Die Mitgliedstaaten der EU, als ISO-3166-1-alpha-2-Codes.
 *
 * Wozu: Ob die USt-IdNr. eines Kunden überhaupt geprüft werden *kann*, hängt
 * daran. Das VIES-System der EU und die Bestätigungsabfrage beim BZSt decken
 * nur Mitgliedstaaten ab – für einen Schweizer oder britischen Kunden gibt es
 * dort schlicht nichts abzufragen, und ein Programm, das trotzdem einen
 * VIES-Nachweis verlangt, wäre nicht streng, sondern falsch.
 *
 * Stand: August 2026, 27 Mitgliedstaaten. Diese Liste ändert sich selten, aber
 * sie ändert sich – zuletzt 2020 durch den Austritt des Vereinigten Königreichs.
 * Wer sie anfasst, sollte das Datum hier mitpflegen.
 *
 * Griechenland steht doppelt: ISO 3166 kennt es als GR, die Umsatzsteuer-
 * Systeme der EU verwenden EL. Beide gelten hier als EU.
 *
 * Nordirland (XI) fehlt bewusst: Der Sonderstatus betrifft den Warenverkehr,
 * nicht die sonstigen Leistungen, um die es auf diesen Rechnungen geht.
 */
object EuMemberStates {
    private val CODES =
        setOf(
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
        )

    fun contains(countryCode: String): Boolean = CODES.contains(countryCode.trim().uppercase())
}
