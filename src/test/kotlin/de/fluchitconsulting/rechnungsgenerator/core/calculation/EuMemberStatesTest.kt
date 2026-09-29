package de.fluchitconsulting.rechnungsgenerator.core.calculation

import de.fluchitconsulting.rechnungsgenerator.core.values.CountryCode
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
            // Vereinheitlicht wird in CountryCode, geprüft wird, dass es ankommt.
            EuMemberStates.contains(CountryCode("at")) shouldBe true
            EuMemberStates.contains(CountryCode("CH")) shouldBe false
            EuMemberStates.contains(CountryCode("GB")) shouldBe false
            EuMemberStates.contains(CountryCode("US")) shouldBe false
        }
    }

    test("Griechenland zählt unter beiden Codes") {
        assertSoftly {
            EuMemberStates.contains(CountryCode("GR")) shouldBe true
            EuMemberStates.contains(CountryCode("EL")) shouldBe true
        }
    }

    // Wird ohne Codeänderung rot und lässt sich deshalb abschalten, nur
    // ausdrücklich und als übersprungen (#79).
    test("die hinterlegte Liste ist höchstens sechs Monate alt")
        .config(enabled = System.getenv(AGE_CHECK_VARIABLE) != "aus") {
            val age = ChronoUnit.MONTHS.between(EuMemberStates.AS_OF, LocalDate.now())

            withClue(
                """

                Die Liste der EU-Mitgliedstaaten wurde zuletzt am ${EuMemberStates.AS_OF} geprüft
                und ist jetzt $age Monate alt.

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

                Nur wer bewusst einen älteren Stand baut (git bisect, Nachbau eines alten PDF),
                überspringt diesen Test mit $AGE_CHECK_VARIABLE=aus.

                """.trimIndent(),
            ) {
                age shouldBeLessThan 6L
            }
        }
})

private const val AGE_CHECK_VARIABLE = "EU_LISTE_ALTERSPRUEFUNG"
