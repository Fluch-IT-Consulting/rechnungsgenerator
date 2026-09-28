package de.fluchitconsulting.rechnungsgenerator.core.calculation

import de.fluchitconsulting.rechnungsgenerator.core.values.CountryCode
import java.time.LocalDate

/**
 * Die Mitgliedstaaten der EU, als ISO-3166-1-alpha-2-Codes.
 *
 * Wozu: Ob die USt-IdNr. eines Kunden überhaupt geprüft werden *kann*, hängt
 * daran. Das VIES-System der EU und die Bestätigungsabfrage beim BZSt decken
 * nur Mitgliedstaaten ab – für einen Schweizer oder britischen Kunden gibt es
 * dort schlicht nichts abzufragen, und ein Programm, das trotzdem einen
 * VIES-Nachweis verlangt, wäre nicht streng, sondern falsch.
 *
 * 27 Mitgliedstaaten. Diese Liste ändert sich selten, aber sie ändert sich –
 * zuletzt 2020 durch den Austritt des Vereinigten Königreichs. Genau das macht
 * sie tückisch: Sie ist heute richtig und wird irgendwann still falsch, ohne
 * dass jemand etwas merkt. Deshalb trägt sie ihr Prüfdatum als Wert und nicht
 * bloß als Kommentar – [AS_OF] wird zur Laufzeit ausgewertet.
 *
 * Wer die Liste prüft oder ändert, setzt [AS_OF] mit.
 *
 * Griechenland steht doppelt: ISO 3166 kennt es als GR, die Umsatzsteuer-
 * Systeme der EU verwenden EL. Beide gelten hier als EU.
 *
 * Nordirland (XI) fehlt bewusst: Der Sonderstatus betrifft den Warenverkehr,
 * nicht die sonstigen Leistungen, um die es auf diesen Rechnungen geht.
 */
object EuMemberStates {
    /**
     * Wann diese Liste zuletzt gegen die amtliche Aufstellung abgeglichen wurde.
     *
     * Nicht das Datum der letzten Codezeile, sondern das der letzten Prüfung:
     * Eine unveränderte Liste kann trotzdem geprüft worden sein, und nur das
     * zählt für die Frage, ob man ihr noch trauen darf.
     */
    val AS_OF: LocalDate = LocalDate.of(2026, 8, 30)

    // Die Liste steht als Text da, wird aber sofort in Werte umgewandelt: So
    // bleibt sie beim Pflegen lesbar, und ein verrutschter Eintrag ("D", "DEE",
    // "AT ") lässt schon den Aufbau scheitern. Als Set<String> täte er das
    // nicht – der betroffene Staat gälte still als Drittland, und still würde
    // eine andere Steuerregel angewandt. Genau diese Sorte Fehler ist der
    // Grund, warum diese Liste ein Prüfdatum trägt.
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
