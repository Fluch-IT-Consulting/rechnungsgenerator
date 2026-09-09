package de.fluchitconsulting.rechnung.values

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class EuMemberStatesTest : FunSpec({

    test("erkennt Mitgliedstaaten und Drittländer") {
        assertSoftly {
            EuMemberStates.contains(CountryCode("DE")) shouldBe true
            EuMemberStates.contains(CountryCode("AT")) shouldBe true
            // "at" klein: Die Vereinheitlichung passiert jetzt in CountryCode,
            // nicht mehr hier – geprüft wird, dass sie ankommt.
            EuMemberStates.contains(CountryCode("at")) shouldBe true
            EuMemberStates.contains(CountryCode("CH")) shouldBe false
            EuMemberStates.contains(CountryCode("GB")) shouldBe false
            EuMemberStates.contains(CountryCode("US")) shouldBe false
        }
    }

    test("Griechenland zählt unter beiden Codes") {
        // ISO 3166 kennt GR, die Umsatzsteuer-Systeme der EU verwenden EL.
        assertSoftly {
            EuMemberStates.contains(CountryCode("GR")) shouldBe true
            EuMemberStates.contains(CountryCode("EL")) shouldBe true
        }
    }

    test("die hinterlegte Liste ist höchstens sechs Monate alt") {
        val alter = ChronoUnit.MONTHS.between(EuMemberStates.AS_OF, LocalDate.now())

        withClue(
            """

            Die Liste der EU-Mitgliedstaaten wurde zuletzt am ${EuMemberStates.AS_OF} geprüft
            und ist jetzt $alter Monate alt.

            Dieser Test wird absichtlich mit der Zeit rot, ohne dass jemand Code geändert hat.
            Das ist kein Defekt, sondern der Zweck: Die Liste entscheidet mit, ob eine Rechnung
            ohne Umsatzsteuer einen Nachweis der Bestätigungsabfrage verlangt. Sie ändert sich
            selten – zuletzt 2020 durch den Brexit –, und wenn sie sich ändert, geht das sonst
            unbemerkt vorbei, weil nichts kaputtgeht: Es wird nur leise die falsche Regel
            angewandt.

            Zu tun:
              1. Die Codes in EuMemberStates gegen die amtliche Aufstellung abgleichen.
              2. EuMemberStates.AS_OF auf das heutige Datum setzen – auch dann, wenn sich
                 nichts geändert hat. Das Datum sagt "geprüft", nicht "geändert".

            """.trimIndent(),
        ) {
            alter shouldBeLessThan 6L
        }
    }
})
