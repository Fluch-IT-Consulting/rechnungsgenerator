package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
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
 *
 * Dazu die Meldung über fehlende Programme. Sie startet keinen Prozess, liest
 * aber denselben PATH – und ein Name, den es sicher nicht gibt, fehlt auf jedem
 * Rechner gleich zuverlässig (#238).
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

    /**
     * Steht für alles, woran der Lauf scheitert, bevor das Programm etwas tut:
     * ein Temp-Verzeichnis, das voll oder schreibgeschützt ist, ein Start, den
     * das Betriebssystem verweigert. Das ist ein Werkzeugfehler und keiner beim
     * Schreiben der Rechnung – `erzeuge` meldete ihn sonst als solchen (#300).
     */
    test("scheitert der Lauf an einer IOException, wird sie zur ToolchainException mit dem Programm") {
        val gibtEsNicht = arbeitsverzeichnis.resolve("gibt-es-nicht")

        val fehler = shouldThrow<ToolchainException> { ExternalTool.run(listOf("true"), gibtEsNicht) }

        fehler.message!! shouldContain "true"
        fehler.message!! shouldContain gibtEsNicht.toString()
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
        val result =
            ExternalTool.run(
                listOf("printenv", "RECHNUNGSGENERATOR_TEST"),
                arbeitsverzeichnis,
                environment = mapOf("RECHNUNGSGENERATOR_TEST" to "angekommen"),
            )

        result.output shouldContain "angekommen"
    }

    /**
     * Der Grund für die Trennung steht bei [ToolResult]: `verapdf` schreibt
     * seinen Bericht auf stdout und sein Protokoll auf stderr, und
     * zusammengeführt landeten Log-Zeilen mitten im XML (#227).
     *
     * Geprüft an `sh`, nicht an veraPDF: Was hier zur Debatte steht, ist die
     * Hülle um ProcessBuilder, nicht das Werkzeug.
     */
    test("stdout und stderr kommen getrennt zurück") {
        val result =
            ExternalTool.run(
                listOf("sh", "-c", "echo nach-stdout; echo nach-stderr >&2"),
                arbeitsverzeichnis,
            )

        result.standardOutput.trim() shouldBe "nach-stdout"
        result.errorOutput.trim() shouldBe "nach-stderr"
        // output gibt es weiter, für Meldungen, die alles wollen.
        result.output shouldContain "nach-stdout"
        result.output shouldContain "nach-stderr"
    }

    /**
     * `requireAll` wird beim Start aufgerufen: Ein Abbruch mitten im Satz wäre
     * schwerer zu deuten. Die Meldung muss deshalb sagen, was zu tun ist – der
     * Name allein hilft niemandem weiter, der das Programm nicht kennt.
     */
    test("ein fehlendes Programm steht mit seinem Installationshinweis in der Meldung") {
        val fehler =
            shouldThrow<ToolchainException> {
                ExternalTool.requireAll("gibt-es-nicht-verapdf" to "brew install gibt-es-nicht")
            }

        fehler.message!! shouldContain "Diese Programme fehlen im PATH:"
        fehler.message!! shouldContain "gibt-es-nicht-verapdf"
        fehler.message!! shouldContain "brew install gibt-es-nicht"
    }

    /**
     * Alle fehlenden auf einmal, aus demselben Grund wie die Befunde einer
     * Rechnung (ADR-0004): Zwei fehlende Programme sollen nicht zwei Läufe
     * brauchen. Ein vorhandenes ist mit dabei – sonst wäre der Test auch dann
     * grün, wenn `requireAll` gar nicht erst nachsähe.
     */
    test("mehrere fehlende Programme stehen alle in der Meldung, ein vorhandenes nicht") {
        val fehler =
            shouldThrow<ToolchainException> {
                ExternalTool.requireAll(
                    "gibt-es-nicht-lualatex" to "brew install lualatex-attrappe",
                    "sh" to "liegt auf jedem Rechner",
                    "gibt-es-nicht-verapdf" to "brew install verapdf-attrappe",
                )
            }

        fehler.message!! shouldContain "gibt-es-nicht-lualatex"
        fehler.message!! shouldContain "gibt-es-nicht-verapdf"
        fehler.message!! shouldNotContain "liegt auf jedem Rechner"
    }

    test("sind alle Programme da, geschieht nichts") {
        shouldNotThrowAny { ExternalTool.requireAll("sh" to "liegt auf jedem Rechner") }
    }
})
