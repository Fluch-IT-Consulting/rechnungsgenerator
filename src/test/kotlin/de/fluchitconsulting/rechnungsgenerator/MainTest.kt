package de.fluchitconsulting.rechnungsgenerator

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

/**
 * Prüft den ganzen Weg von den Argumenten bis zum Exitcode – und dazu, auf
 * welchem Kanal etwas steht.
 *
 * Die Regel: Der Kanal folgt dem Exitcode. Was mit einem Exitcode ungleich 0
 * endet, steht auf stderr; stdout trägt nur das Ergebnis. Wer die Ausgabe
 * umleitet, soll darin nie eine Fehlermeldung finden.
 */
class MainTest : FunSpec({

    val eingaben = createTempDirectory("main-test")
    val rechnung = "src/test/resources/rechnung.yaml"
    val stammdaten = "src/test/resources/stammdaten.yaml"

    test("ein Fehler beim Erzeugen steht auf stderr, stdout bleibt leer") {
        val kaputt = eingaben.resolve("kaputt.yaml").also { it.writeText("nummer: R26-001\ngibtsnicht: 1\n") }

        val result = runCli("erzeuge", kaputt.toString(), "-s", stammdaten)

        result.exitCode shouldBe 1
        result.out shouldBe ""
        result.err shouldContain kaputt.toString()
        result.err shouldContain "gibtsnicht"
    }

    test("bei zwei Eingabedateien nennt die Meldung die schuldige") {
        // erzeuge liest Rechnung und Stammdaten. Vorher hieß es nur "Die
        // Eingabedateien konnten nicht gelesen werden", und man durfte raten.
        val kaputteStammdaten =
            eingaben.resolve("stammdaten-kaputt.yaml").also { it.writeText("firma: Muster\ngibtsnicht: 1\n") }

        val result = runCli("erzeuge", rechnung, "-s", kaputteStammdaten.toString())

        result.exitCode shouldBe 1
        result.out shouldBe ""
        result.err shouldContain kaputteStammdaten.toString()
        result.err shouldNotContain rechnung
    }

    test("eine unlesbare Datei spricht vom Lesen, nicht vom Schreiben") {
        // Der IOException-Zweig war für ein nicht beschreibbares Ziel gedacht
        // und fing den Lesefehler gleich mit. (Als root ist auch chmod 000
        // lesbar, dieser Test schlüge dort fehl.)
        val unlesbar =
            eingaben.resolve("unlesbar.yaml").also {
                it.writeText("nummer: R26-001\n")
                it.toFile().setReadable(false)
            }

        val result = runCli("erzeuge", unlesbar.toString(), "-s", stammdaten)

        result.exitCode shouldBe 1
        result.out shouldBe ""
        result.err shouldContain unlesbar.toString()
        result.err shouldContain "gelesen"
        result.err shouldNotContain "geschrieben"
    }

    test("das Ergebnis steht auf stdout, stderr bleibt leer") {
        val ziel = eingaben.resolve("ergebnis.pdf")

        val result = runCli("erzeuge", rechnung, "-s", stammdaten, "-o", ziel.toString())

        result.exitCode shouldBe 0
        result.out shouldContain "PDF/A-1b: gültig"
        result.err shouldBe ""
    }

    test("ein unbekanntes Unterkommando steht auf stderr") {
        // Der Fehler kommt hier von Clikt selbst, nicht aus unserem Code –
        // er muss denselben Weg nehmen.
        val result = runCli("gibtsnicht")

        result.exitCode shouldBe 1
        result.out shouldBe ""
        result.err shouldContain "no such subcommand"
    }

    test("ohne Unterkommando steht die Hilfe auf stderr") {
        // Wer "rechnungsgenerator >> schluss.yaml" tippt und das Unterkommando
        // vergisst, soll die Hilfe nicht an seine Schlussrechnung hängen.
        val result = runCli()

        result.exitCode shouldBe 1
        result.out shouldBe ""
        result.err shouldContain "Usage: rechnungsgenerator"
    }

    test("--help ist kein Fehler und steht auf stdout") {
        val result = runCli("--help")

        result.exitCode shouldBe 0
        result.out shouldContain "Usage: rechnungsgenerator"
        result.err shouldBe ""
    }
})
