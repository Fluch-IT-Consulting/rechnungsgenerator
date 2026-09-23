package de.fluchitconsulting.rechnungsgenerator.core.values

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

    // Der einzige Test, der ohne Codeänderung rot wird – und damit jeden älteren
    // Stand unbaubar macht, etwa bei git bisect oder beim Nachbauen eines alten
    // PDF (#79). Deshalb lässt er sich abschalten, aber nur ausdrücklich:
    // Vorgabe ist scharf. `config(enabled = …)` überspringt den Test, statt ihn
    // grün zu melden – im Bericht steht er dann als übersprungen.
    test("die hinterlegte Liste ist höchstens sechs Monate alt")
        .config(enabled = System.getenv(ALTERSPRUEFUNG) != "aus") {
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

                Nur wer bewusst einen älteren Stand baut (git bisect, Nachbau eines alten PDF),
                überspringt diesen Test mit $ALTERSPRUEFUNG=aus.

                """.trimIndent(),
            ) {
                alter shouldBeLessThan 6L
            }
        }
})

// `const val` auf oberster Ebene: eine Konstante zur Übersetzungszeit, damit der
// Name im Test und in seiner Meldung nicht zweimal getippt wird.
private const val ALTERSPRUEFUNG = "EU_LISTE_ALTERSPRUEFUNG"
