package de.fluchitconsulting.rechnung

import kotlin.test.Test
import kotlin.test.assertEquals

// Platzhalter-Test: verifiziert nur, dass die Test-Infrastruktur (JUnit-Platform,
// kotlin-test) korrekt verdrahtet ist. Fliegt raus, sobald echte Tests da sind.
class BuildSmokeTest {
    @Test
    fun `test infrastructure works`() {
        // Kotlin erlaubt Funktionsnamen in Backticks mit Leerzeichen –
        // in Tests üblich, weil der Name so als lesbare Beschreibung dient.
        assertEquals(4, 2 + 2)
    }
}
