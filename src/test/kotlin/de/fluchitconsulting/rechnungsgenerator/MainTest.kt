package de.fluchitconsulting.rechnungsgenerator

import de.fluchitconsulting.rechnungsgenerator.generate.FakePdfViewer
import de.fluchitconsulting.rechnungsgenerator.generate.ViewerResult
import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.FakeTypesetter
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.Enabled
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Die Eigenschaft, über die der Build seine Version an die Test-JVM reicht. */
private const val VERSION_PROPERTY = "projectVersion"

/**
 * Nur Gradle setzt [VERSION_PROPERTY]. Wer die Tests im Editor startet, hat sie
 * nicht – dann fehlt der Vergleichswert, und der Test überspringt sich, statt
 * grün oder rot zu melden. Übersprungen und nicht bestanden, wie beim
 * Altersnachweis der EU-Liste: Ein Netz, das fehlt, soll nicht aussehen wie
 * eines, das hält.
 */
private val mitVersionAusDemBuild =
    if (System.getProperty(VERSION_PROPERTY) != null) {
        Enabled.enabled
    } else {
        Enabled.disabled(
            "Die Fassung aus dem Build steht nur unter Gradle zur Verfügung (./gradlew test). " +
                "Dass --version überhaupt etwas ausgibt, prüft der Test davor trotzdem.",
        )
    }

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

    // Die drei Fachfehler übersetzt execute() einmal für alle Kommandos (#119).
    // Deshalb stehen sie hier und nicht in den Tests der Kommandos: Die prüfen
    // nur noch, dass die Ausnahme geworfen wird.

    test("eine abgelehnte Rechnung steht auf stderr, mit allen Abbrüchen") {
        // Zwei Verstöße in einer Datei: Beide sollen dastehen, nicht nur der erste.
        val abgelehnt =
            eingaben.resolve("abgelehnt.yaml").also {
                it.writeText(
                    Path
                        .of(rechnung)
                        .readText()
                        .replace("nummer: R26-042", "nummer: \"///\"")
                        .replace("    einzelpreis: 95.00", "    einzelpreis: -95.00"),
                )
            }

        val result = runCli("erzeuge", abgelehnt.toString(), "-s", stammdaten)

        result.exitCode shouldBe 1
        result.out shouldBe ""
        result.err shouldContain "bleibt kein Dateiname übrig"
        result.err shouldContain "Position 1"
    }

    test("ein ungültiges PDF/A steht auf stderr") {
        val ziel = eingaben.resolve("ungueltig.pdf")

        val result = runCli("erzeuge", rechnung, "-s", stammdaten, "-o", ziel.toString(), typesetter = FakeTypesetter(valid = false))

        result.exitCode shouldBe 1
        result.out shouldBe ""
        result.err shouldContain "kein gültiges PDF/A-1b"
    }

    test("das Ergebnis steht auf stdout, stderr bleibt leer") {
        val ziel = eingaben.resolve("ergebnis.pdf")

        val result = runCli("erzeuge", rechnung, "-s", stammdaten, "-o", ziel.toString())

        result.exitCode shouldBe 0
        result.out shouldContain "PDF/A-1b: gültig"
        result.err shouldBe ""
    }

    test("scheitert das Öffnen, bleibt der Aufruf ein Erfolg, und der Hinweis steht auf stdout") {
        // Ein Viewer, der nicht aufgeht, ist kein Grund, die Rechnung für
        // gescheitert zu erklären: Das PDF liegt am Ziel (#144). Also Exitcode 0
        // – und weil der Kanal dem Exitcode folgt, gehört der Hinweis auf stdout.
        // Gelesen wird beides hier; GenerateCommandTest sieht nur die Reihenfolge.
        val ziel = eingaben.resolve("viewer-fehlt.pdf")

        val result =
            runCli(
                "erzeuge",
                rechnung,
                "-s",
                stammdaten,
                "-o",
                ziel.toString(),
                "--oeffnen",
                viewer = FakePdfViewer(result = ViewerResult.Failed("open endete mit Exitcode 1.")),
            )

        result.exitCode shouldBe 0
        result.out shouldContain "Hinweis: Das PDF konnte nicht geöffnet werden: open endete mit Exitcode 1."
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

    test("--version ist kein Fehler und steht auf stdout") {
        // Dieselbe Regel wie bei --help: Wer die Fassung erfragt, hat keinen
        // Fehler gemacht. Ohne die eager Option fiele der Aufruf in "kein
        // Unterkommando" und endete mit 1 auf stderr.
        val result = runCli("--version")

        result.exitCode shouldBe 0
        result.out shouldContain "rechnungsgenerator "
        result.err shouldBe ""
    }

    test("die genannte Fassung ist die aus dem Build")
        .config(enabledOrReasonIf = { mitVersionAusDemBuild }) {
            // Schließt den Kreis: Die Ressource entsteht beim Bauen aus
            // `version` in build.gradle.kts, und hier steht, dass genau die
            // Zahl beim Benutzer ankommt. Ohne diesen Test liefe eine
            // veraltete Ressource still mit.
            val result = runCli("--version")

            result.out.trim() shouldBe "rechnungsgenerator ${System.getProperty(VERSION_PROPERTY)}"
        }
})
