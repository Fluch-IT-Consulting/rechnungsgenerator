package de.fluchitconsulting.rechnungsgenerator

import de.fluchitconsulting.rechnungsgenerator.generate.FakePdfViewer
import de.fluchitconsulting.rechnungsgenerator.generate.ViewerResult
import de.fluchitconsulting.rechnungsgenerator.generate.typesetting.FakeTypesetter
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.Enabled
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.slf4j.spi.SLF4JServiceProvider
import java.nio.file.Path
import java.util.ServiceLoader
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val VERSION_PROPERTY = "projectVersion"

// Übersprungen, nicht bestanden: Nur Gradle setzt die Eigenschaft, der Editor nicht.
private val withVersionFromBuild =
    if (System.getProperty(VERSION_PROPERTY) != null) {
        Enabled.enabled
    } else {
        Enabled.disabled(
            "Die Fassung aus dem Build steht nur unter Gradle zur Verfügung (./gradlew test). " +
                "Dass --version überhaupt etwas ausgibt, prüft der Test davor trotzdem.",
        )
    }

class MainTest : FunSpec({

    val inputDir = createTempDirectory("main-test")
    val invoice = "src/test/resources/rechnung.yaml"
    val sellerConfig = "src/test/resources/stammdaten.yaml"

    test("ein Fehler beim Erzeugen steht auf stderr, stdout bleibt leer") {
        val broken = inputDir.resolve("kaputt.yaml").also { it.writeText("nummer: R26-001\ngibtsnicht: 1\n") }

        val result = runCli("erzeuge", broken.toString(), "-s", sellerConfig)

        result.exitCode shouldBe 1
        result.out shouldBe ""
        result.err shouldContain broken.toString()
        result.err shouldContain "gibtsnicht"
    }

    test("bei zwei Eingabedateien nennt die Meldung die schuldige") {
        val brokenSellerConfig =
            inputDir.resolve("stammdaten-kaputt.yaml").also { it.writeText("firma: Muster\ngibtsnicht: 1\n") }

        val result = runCli("erzeuge", invoice, "-s", brokenSellerConfig.toString())

        result.exitCode shouldBe 1
        result.out shouldBe ""
        result.err shouldContain brokenSellerConfig.toString()
        result.err shouldNotContain invoice
    }

    test("eine unlesbare Datei spricht vom Lesen, nicht vom Schreiben") {
        // Schlägt unter root fehl: Dort ist auch chmod 000 lesbar.
        val unreadable =
            inputDir.resolve("unlesbar.yaml").also {
                it.writeText("nummer: R26-001\n")
                it.toFile().setReadable(false)
            }

        val result = runCli("erzeuge", unreadable.toString(), "-s", sellerConfig)

        result.exitCode shouldBe 1
        result.out shouldBe ""
        result.err shouldContain unreadable.toString()
        result.err shouldContain "gelesen"
        result.err shouldNotContain "geschrieben"
    }

    // Die Fachfehler aller Kommandos stehen hier; deren Tests prüfen nur, dass sie geworfen werden (#119).

    test("eine abgelehnte Rechnung steht auf stderr, mit allen Abbrüchen") {
        val rejected =
            inputDir.resolve("abgelehnt.yaml").also {
                it.writeText(
                    Path
                        .of(invoice)
                        .readText()
                        .replace("nummer: R26-042", "nummer: \"///\"")
                        .replace("    einzelpreis: 95.00", "    einzelpreis: -95.00"),
                )
            }

        val result = runCli("erzeuge", rejected.toString(), "-s", sellerConfig)

        result.exitCode shouldBe 1
        result.out shouldBe ""
        result.err shouldContain "bleibt kein Dateiname übrig"
        result.err shouldContain "Position 1"
    }

    test("ein ungültiges PDF/A steht auf stderr") {
        val target = inputDir.resolve("ungueltig.pdf")

        val result = runCli("erzeuge", invoice, "-s", sellerConfig, "-o", target.toString(), typesetter = FakeTypesetter(valid = false))

        result.exitCode shouldBe 1
        result.out shouldBe ""
        result.err shouldContain "kein gültiges PDF/A-1b"
    }

    test("das Ergebnis steht auf stdout, stderr bleibt leer") {
        val target = inputDir.resolve("ergebnis.pdf")

        val result = runCli("erzeuge", invoice, "-s", sellerConfig, "-o", target.toString())

        result.exitCode shouldBe 0
        result.out shouldContain "PDF/A-1b: gültig"
        result.err shouldBe ""
    }

    // Nicht an stderr prüfbar: SLF4J warnt an Clikt vorbei und je JVM nur einmal.
    test("SLF4J findet eine Anbindung und warnt deshalb nicht auf stderr") {
        ServiceLoader.load(SLF4JServiceProvider::class.java).toList().shouldNotBeEmpty()
    }

    test("scheitert das Öffnen, bleibt der Aufruf ein Erfolg, und der Hinweis steht auf stdout") {
        // #144. Den Kanal sieht nur dieser Test, GenerateCommandTest die Reihenfolge.
        val target = inputDir.resolve("viewer-fehlt.pdf")

        val result =
            runCli(
                "erzeuge",
                invoice,
                "-s",
                sellerConfig,
                "-o",
                target.toString(),
                "--oeffnen",
                viewer = FakePdfViewer(result = ViewerResult.Failed("open endete mit Exitcode 1.")),
            )

        result.exitCode shouldBe 0
        result.out shouldContain "Hinweis: Das PDF konnte nicht geöffnet werden: open endete mit Exitcode 1."
        result.err shouldBe ""
    }

    test("ein unbekanntes Unterkommando steht auf stderr") {
        val result = runCli("gibtsnicht")

        result.exitCode shouldBe 1
        result.out shouldBe ""
        result.err shouldContain "no such subcommand"
    }

    test("ohne Unterkommando steht die Hilfe auf stderr") {
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
        // Ohne die eager Option fiele der Aufruf in "kein Unterkommando".
        val result = runCli("--version")

        result.exitCode shouldBe 0
        result.out shouldContain "rechnungsgenerator "
        result.err shouldBe ""
    }

    test("die genannte Fassung ist die aus dem Build")
        .config(enabledOrReasonIf = { withVersionFromBuild }) {
            // Die Ressource entsteht beim Bauen aus `version` in build.gradle.kts.
            val result = runCli("--version")

            result.out.trim() shouldBe "rechnungsgenerator ${System.getProperty(VERSION_PROPERTY)}"
        }
})
