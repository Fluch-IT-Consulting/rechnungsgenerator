package de.fluchitconsulting.rechnung

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.text.Charsets.UTF_8

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

    /** Was ein Aufruf hinterlässt: den Exitcode und die beiden Kanäle. */
    data class Result(val exitCode: Int, val out: String, val err: String)

    fun call(vararg args: String): Result {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val exitCode =
            execute(arrayOf(*args), PrintStream(out, true, UTF_8), PrintStream(err, true, UTF_8), FakeTypesetter())
        return Result(exitCode, out.toString(UTF_8), err.toString(UTF_8))
    }

    test("ein Fehler beim Erzeugen steht auf stderr, stdout bleibt leer") {
        val kaputt = eingaben.resolve("kaputt.yaml").also { it.writeText("nummer: R26-001\ngibtsnicht: 1\n") }

        val result = call("erzeuge", kaputt.toString(), "-s", stammdaten)

        result.exitCode shouldBe 1
        result.out shouldBe ""
        result.err shouldContain "Die Eingabedateien konnten nicht gelesen werden"
    }

    test("das Ergebnis steht auf stdout, stderr bleibt leer") {
        val ziel = eingaben.resolve("ergebnis.pdf")

        val result = call("erzeuge", rechnung, "-s", stammdaten, "-o", ziel.toString())

        result.exitCode shouldBe 0
        result.out shouldContain "PDF/A-1b: gültig"
        result.err shouldBe ""
    }

    test("ein unbekanntes Unterkommando steht auf stderr") {
        // Der Fehler kommt hier von Clikt selbst, nicht aus unserem Code –
        // er muss denselben Weg nehmen.
        val result = call("gibtsnicht")

        result.exitCode shouldBe 1
        result.out shouldBe ""
        result.err shouldContain "no such subcommand"
    }

    test("ohne Unterkommando steht die Hilfe auf stderr") {
        // Wer "rechnungsgenerator >> schluss.yaml" tippt und das Unterkommando
        // vergisst, soll die Hilfe nicht an seine Schlussrechnung hängen.
        val result = call()

        result.exitCode shouldBe 1
        result.out shouldBe ""
        result.err shouldContain "Usage: rechnungsgenerator"
    }

    test("--help ist kein Fehler und steht auf stdout") {
        val result = call("--help")

        result.exitCode shouldBe 0
        result.out shouldContain "Usage: rechnungsgenerator"
        result.err shouldBe ""
    }
})
