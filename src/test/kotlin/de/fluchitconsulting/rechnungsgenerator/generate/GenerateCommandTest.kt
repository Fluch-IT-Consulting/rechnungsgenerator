package de.fluchitconsulting.rechnungsgenerator.generate

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import de.fluchitconsulting.rechnungsgenerator.RechnungsgeneratorCommand
import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.FakeTypesetter
import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.ToolchainException
import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.Typesetter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Prüft, was zwischen Einlesen und Ergebnis passiert: kopieren, ausgeben,
 * aufräumen, Exitcode setzen.
 *
 * Das war bis hierher die einzige größere Stelle ohne Test, weil jeder Lauf
 * LuaLaTeX und veraPDF vorausgesetzt hätte. Hinter der Naht steht jetzt ein
 * Adapter, der ein vorbereitetes PDF und ein vorbereitetes Prüfergebnis
 * liefert – der Weg davor bleibt echt, nur die beiden fremden Programme sind
 * ersetzt.
 *
 * Aufgerufen wird über das Elternkommando, so wie ein Mensch es tut:
 * `rechnungsgenerator erzeuge …`. Die Ausgabe lässt sich trotzdem dort
 * abgreifen, denn ein Unterkommando erbt `echoMessage` von seinem Elternteil.
 */
class GenerateCommandTest : FunSpec({

    val eingaben = createTempDirectory("kommando-test")
    val rechnung = eingaben.resolve("rechnung.yaml").also { it.writeText(quelle("rechnung.yaml")) }
    val stammdaten = eingaben.resolve("stammdaten.yaml").also { it.writeText(quelle("stammdaten.yaml")) }

    /**
     * Führt das Kommando aus und sammelt, was es ausgegeben hätte.
     *
     * Der Viewer ist immer ein Fake, auch wo ein Test ihn nicht nennt: Ein
     * `--oeffnen` soll im Build nie ein Fenster aufmachen. [ausgabe] lässt sich
     * hineinreichen, damit ein Test sie schon während des Laufs sieht.
     */
    fun lauf(
        typesetter: Typesetter,
        vararg args: String,
        viewer: PdfViewer = FakePdfViewer(),
        ausgabe: MutableList<String> = mutableListOf(),
    ): List<String> {
        val command = RechnungsgeneratorCommand(typesetter, viewer)
        command.context { echoMessage = { _, message, _, _ -> ausgabe += message.toString() } }
        command.parse(arrayOf("erzeuge", rechnung.toString(), "-s", stammdaten.toString()) + args)
        return ausgabe
    }

    test("das PDF landet am gewünschten Ort, und das Ergebnis steht in der Ausgabe") {
        val ziel = eingaben.resolve("ergebnis.pdf")

        val ausgabe = lauf(FakeTypesetter(), "-o", ziel.toString())

        ziel.exists() shouldBe true
        ziel.readText() shouldContain "%PDF"
        ausgabe shouldContainInOrder listOf(ziel.toString(), "PDF/A-1b: gültig")
    }

    test("das Arbeitsverzeichnis wird aufgeräumt") {
        val fake = FakeTypesetter()

        lauf(fake, "-o", eingaben.resolve("weg.pdf").toString())

        // Der Adapter hat sich gemerkt, wo gearbeitet wurde – sonst wüsste der
        // Test es nicht, denn das Kommando nennt das Verzeichnis nur mit
        // --arbeitsdateien-behalten.
        fake.workingDir!!.exists() shouldBe false
    }

    test("mit --arbeitsdateien-behalten bleibt es stehen und wird genannt") {
        val fake = FakeTypesetter()

        val ausgabe = lauf(fake, "-o", eingaben.resolve("bleibt.pdf").toString(), "--arbeitsdateien-behalten")

        fake.workingDir!!.exists() shouldBe true
        ausgabe.last() shouldContain "Arbeitsdateien:"
    }

    test("ein ungültiges PDF/A ist ein Fehler der Werkzeuge") {
        // Den Exitcode und den Kanal prüft MainTest: Übersetzt wird die
        // Ausnahme in execute(), einmal für alle Kommandos (#119).
        val fehler =
            shouldThrow<ToolchainException> {
                lauf(FakeTypesetter(valid = false), "-o", eingaben.resolve("ungueltig.pdf").toString())
            }

        fehler.message!! shouldContain "kein gültiges PDF/A-1b"
    }

    test("auch ein ungültiges PDF/A lässt kein Arbeitsverzeichnis zurück") {
        val fake = FakeTypesetter(valid = false)

        shouldThrow<ToolchainException> { lauf(fake, "-o", eingaben.resolve("ungueltig2.pdf").toString()) }

        // Das Aufräumen steht in einem finally – hier wird es geprüft.
        fake.workingDir!!.exists() shouldBe false
    }

    test("ein ungültiges PDF/A legt keine Datei an und sagt, wie man es ansieht") {
        val ziel = eingaben.resolve("nie-da.pdf")

        val fehler = shouldThrow<ToolchainException> { lauf(FakeTypesetter(valid = false), "-o", ziel.toString()) }

        ziel.exists() shouldBe false
        fehler.message!! shouldContain "--arbeitsdateien-behalten"
    }

    test("ein ungültiges PDF/A ersetzt keine vorhandene Datei, auch nicht mit --ueberschreiben") {
        val ziel = eingaben.resolve("verschickt.pdf").also { it.writeText("das verschickte Original") }

        val fehler =
            shouldThrow<ToolchainException> {
                lauf(FakeTypesetter(valid = false), "-o", ziel.toString(), "--ueberschreiben")
            }

        ziel.readText() shouldBe "das verschickte Original"
        // Die Verstöße gehören zum Fehlschlag und stehen deshalb in der
        // Meldung, also auf stderr – der Kanal folgt dem Exitcode.
        fehler.message!! shouldContain "6.1.2"
    }

    test("eine vorhandene Zieldatei bleibt ohne --ueberschreiben unangetastet") {
        val ziel = eingaben.resolve("vorhanden.pdf").also { it.writeText("das verschickte Original") }
        val fake = FakeTypesetter()

        val fehler = shouldThrow<CliktError> { lauf(fake, "-o", ziel.toString()) }

        fehler.statusCode shouldBe 1
        fehler.message!! shouldContain "--ueberschreiben"
        ziel.readText() shouldBe "das verschickte Original"
        // Geprüft wird vor dem Satz: Ein LuaLaTeX-Lauf, dessen Ergebnis
        // nirgends hin darf, wäre vergeudet.
        fake.workingDir shouldBe null
    }

    test("mit --ueberschreiben wird eine vorhandene Zieldatei ersetzt") {
        val ziel = eingaben.resolve("entwurf.pdf").also { it.writeText("der erste Entwurf") }

        lauf(FakeTypesetter(), "-o", ziel.toString(), "--ueberschreiben")

        ziel.readText() shouldContain "%PDF"
    }

    test("der Jobname kommt aus Rechnungsart und -nummer") {
        val fake = FakeTypesetter()

        lauf(fake, "-o", eingaben.resolve("jobname.pdf").toString())

        // Ohne -o landete die Datei im Arbeitsverzeichnis des Prozesses, also
        // im Projektverzeichnis – dafür legt dieser Test keine Datei an.
        fake.jobName shouldBe "Rechnung-R26-042"
    }

    test("das PDF trägt das Rechnungsdatum als Erstellungsdatum") {
        val fake = FakeTypesetter()

        lauf(fake, "-o", eingaben.resolve("datum.pdf").toString())

        fake.creationDate shouldBe LocalDate.of(2026, 8, 14)
    }

    context("--oeffnen") {
        test("öffnet genau die Zieldatei, und zwar nach der Ausgabe") {
            val ziel = eingaben.resolve("oeffnen.pdf")
            val ausgabe = mutableListOf<String>()
            var ausgabeBeimOeffnen: List<String>? = null
            val viewer = FakePdfViewer(onOpen = { ausgabeBeimOeffnen = ausgabe.toList() })

            lauf(FakeTypesetter(), "-o", ziel.toString(), "--oeffnen", viewer = viewer, ausgabe = ausgabe)

            viewer.opened shouldBe listOf(ziel)
            ausgabe shouldContainInOrder listOf(ziel.toString(), "PDF/A-1b: gültig")
            ausgabeBeimOeffnen shouldBe ausgabe
        }

        test("ohne den Schalter öffnet sich nichts") {
            val viewer = FakePdfViewer()

            lauf(FakeTypesetter(), "-o", eingaben.resolve("nicht-oeffnen.pdf").toString(), viewer = viewer)

            viewer.opened shouldBe emptyList()
        }

        test("ein ungültiges PDF/A öffnet sich nicht, auch nicht mit --arbeitsdateien-behalten") {
            val viewer = FakePdfViewer()

            shouldThrow<ToolchainException> {
                lauf(
                    FakeTypesetter(valid = false),
                    "-o",
                    eingaben.resolve("ungueltig-oeffnen.pdf").toString(),
                    "--oeffnen",
                    "--arbeitsdateien-behalten",
                    viewer = viewer,
                )
            }

            viewer.opened shouldBe emptyList()
        }

        test("bricht erzeuge an einer vorhandenen Zieldatei ab, öffnet sich auch die nicht") {
            // Die vorhandene Datei kann die verschickte Rechnung sein – nicht
            // die, die gerade aus der Rechnungsdatei entstehen sollte.
            val ziel = eingaben.resolve("vorhanden-oeffnen.pdf").also { it.writeText("das verschickte Original") }
            val viewer = FakePdfViewer()

            shouldThrow<CliktError> { lauf(FakeTypesetter(), "-o", ziel.toString(), "--oeffnen", viewer = viewer) }

            viewer.opened shouldBe emptyList()
        }

        test("scheitert das Öffnen, bleibt es ein Erfolg – mit einem Hinweis als letzter Zeile") {
            val ziel = eingaben.resolve("viewer-fehlt.pdf")
            val viewer = FakePdfViewer(result = ViewerResult.Failed("open endete mit Exitcode 1."))

            // Keine Ausnahme heißt Exitcode 0: Übersetzt wird erst in execute().
            val ausgabe = lauf(FakeTypesetter(), "-o", ziel.toString(), "--oeffnen", viewer = viewer)

            ziel.exists() shouldBe true
            ausgabe.last() shouldBe "Hinweis: Das PDF konnte nicht geöffnet werden: open endete mit Exitcode 1."
        }
    }

    test("die Hinweise für die eigene Akte stehen nach dem Ergebnis, nicht davor") {
        // Eine Rechnung ohne Umsatzsteuer an einen Kunden in Österreich, deren
        // Bestätigungsabfrage nur einfach war: Der Calculator gibt dazu einen
        // Hinweis aus – er betrifft die eigene Ablage, nicht den Kunden, und
        // soll deshalb nicht wie ein Fehler oben stehen.
        val oesterreich =
            eingaben.resolve("oesterreich.yaml").also {
                it.writeText(
                    quelle("rechnung.yaml")
                        .replace("ort: Nürnberg", "ort: Wien\n    land: AT")
                        .replace(
                            "  referenz: BST-2026-0815",
                            """
                            |  umsatzsteuer-id: ATU12345678
                            |  ust-id-geprueft:
                            |    am: 2026-08-01
                            |    verfahren: einfach
                            |    nachweis: BZSt-Protokoll 4711
                            """.trimMargin(),
                        ).replace(
                            "    einzelpreis: 95.00",
                            "    einzelpreis: 95.00\n    ust-satz: 0\n    steuerkategorie: AE",
                        ).replace(
                            "    einzelpreis: 120.00",
                            "    einzelpreis: 120.00\n    ust-satz: 0\n    steuerkategorie: AE",
                        ),
                )
            }

        val ausgabe = mutableListOf<String>()
        val command = RechnungsgeneratorCommand(FakeTypesetter())
        command.context { echoMessage = { _, message, _, _ -> ausgabe += message.toString() } }
        command.parse(
            arrayOf(
                "erzeuge",
                oesterreich.toString(),
                "-s",
                stammdaten.toString(),
                "-o",
                eingaben.resolve("at.pdf").toString(),
            ),
        )

        val ergebnis = ausgabe.indexOfFirst { it.contains("PDF/A-1b") }
        val hinweis = ausgabe.indexOfFirst { it.startsWith("Hinweis:") }
        (hinweis > ergebnis) shouldBe true
        ausgabe[hinweis] shouldContain "nicht qualifiziert"
    }
})

private fun quelle(name: String) = Path.of("src/test/resources/$name").readText()
