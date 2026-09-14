package de.fluchitconsulting.rechnungsgenerator.toolchain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.string.shouldContain
import kotlin.io.path.createTempDirectory
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * Die Wege durch [ExternalTool], die sich nur an einem echten Prozess zeigen:
 * zwei, die schiefgehen, bevor ein Werkzeug überhaupt etwas tut, und die
 * Umgebung, die beim Programm ankommen muss.
 *
 * Sie brauchen weder LuaLaTeX noch veraPDF, sondern nur Programme, die auf
 * jedem Rechner liegen – und trotzdem echte Prozesse: Ein gefälschter bewiese
 * hier nichts. Die Zeitgrenze ist der Grund, warum die Ausgabe in eine Datei
 * umgeleitet wird statt in einen Stream gelesen (siehe das KDoc von
 * [ExternalTool]); ob diese Konstruktion trägt, zeigt sich nur an einem Prozess,
 * der wirklich hängt.
 */
class ExternalToolTest : FunSpec({

    val arbeitsverzeichnis = createTempDirectory("externaltool-test")

    test("ein Programm, das es nicht gibt, wird beim Namen genannt") {
        val fehler =
            shouldThrow<ToolchainException> {
                ExternalTool.run(listOf("gibt-es-nicht-lualatex"), arbeitsverzeichnis)
            }

        fehler.message!! shouldContain "Programm nicht gefunden: gibt-es-nicht-lualatex"
    }

    test("ein hängender Prozess wird nach der Zeitgrenze abgeräumt") {
        lateinit var fehler: ToolchainException

        val gebraucht =
            measureTime {
                fehler =
                    shouldThrow<ToolchainException> {
                        ExternalTool.run(listOf("sleep", "10"), arbeitsverzeichnis, timeout = 1.seconds)
                    }
            }

        fehler.message!! shouldContain "nicht geantwortet und wurde abgebrochen"
        // Der eigentliche Beweis: Es wurde nicht auf das Ende von sleep
        // gewartet. Ohne diese Zusicherung wäre der Test auch dann grün, wenn
        // die Zeitgrenze erst nach dem Prozessende griffe.
        withClue("Der Aufruf kehrte erst nach $gebraucht zurück") {
            gebraucht shouldBeLessThan 5.seconds
        }
    }

    test("die Umgebung kommt beim Programm an") {
        val ergebnis =
            ExternalTool.run(
                listOf("printenv", "RECHNUNGSGENERATOR_TEST"),
                arbeitsverzeichnis,
                environment = mapOf("RECHNUNGSGENERATOR_TEST" to "angekommen"),
            )

        ergebnis.output shouldContain "angekommen"
    }
})
