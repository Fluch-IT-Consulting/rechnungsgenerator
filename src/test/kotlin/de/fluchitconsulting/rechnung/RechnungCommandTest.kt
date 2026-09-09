package de.fluchitconsulting.rechnung

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import de.fluchitconsulting.rechnung.toolchain.PdfAViolation
import de.fluchitconsulting.rechnung.toolchain.TypesetPdf
import de.fluchitconsulting.rechnung.toolchain.Typesetter
import de.fluchitconsulting.rechnung.toolchain.ValidationResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
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
 */
class RechnungCommandTest : FunSpec({

    /**
     * Der Adapter fürs Testen. Er merkt sich, womit er gerufen wurde – das
     * Arbeitsverzeichnis lässt sich sonst nicht prüfen, weil das Kommando es
     * selbst anlegt und niemandem nennt.
     */
    class FakeTypesetter(private val valid: Boolean = true) : Typesetter {
        var workingDir: Path? = null
        var jobName: String? = null
        var texSource: String? = null

        override fun checkAvailable() = Unit

        override fun typeset(workingDir: Path, jobName: String, texSource: String, xmpMetadata: String): TypesetPdf {
            this.workingDir = workingDir
            this.jobName = jobName
            this.texSource = texSource
            val pdf = workingDir.resolve("$jobName.pdf").also { it.writeText("%PDF-1.7 kein echtes PDF") }
            val violations =
                if (valid) {
                    emptyList()
                } else {
                    listOf(PdfAViolation("6.1.2", "1", "Datei ist keine PDF/A", "so nicht"))
                }
            return TypesetPdf(pdf, ValidationResult(valid, violations))
        }
    }

    val eingaben = createTempDirectory("kommando-test")
    val rechnung = eingaben.resolve("rechnung.yaml").also { it.writeText(quelle("rechnung.yaml")) }
    val stammdaten = eingaben.resolve("stammdaten.yaml").also { it.writeText(quelle("stammdaten.yaml")) }

    /** Führt das Kommando aus und sammelt, was es ausgegeben hätte. */
    fun lauf(typesetter: Typesetter, vararg args: String): List<String> {
        val ausgabe = mutableListOf<String>()
        val command = RechnungCommand(typesetter)
        command.context { echoMessage = { _, message, _, _ -> ausgabe += message.toString() } }
        command.parse(arrayOf(rechnung.toString(), "-s", stammdaten.toString()) + args)
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

    test("ein ungültiges PDF/A endet mit Exitcode 1") {
        val fehler =
            shouldThrow<CliktError> {
                lauf(FakeTypesetter(valid = false), "-o", eingaben.resolve("ungueltig.pdf").toString())
            }

        fehler.statusCode shouldBe 1
        fehler.message!! shouldContain "kein gültiges PDF/A-1b"
    }

    test("auch ein ungültiges PDF/A lässt kein Arbeitsverzeichnis zurück") {
        val fake = FakeTypesetter(valid = false)

        shouldThrow<CliktError> { lauf(fake, "-o", eingaben.resolve("ungueltig2.pdf").toString()) }

        // Das Aufräumen steht in einem finally – hier wird es geprüft.
        fake.workingDir!!.exists() shouldBe false
    }

    test("der Jobname kommt aus Rechnungsart und -nummer") {
        val fake = FakeTypesetter()

        lauf(fake, "-o", eingaben.resolve("jobname.pdf").toString())

        // Ohne -o landete die Datei im Arbeitsverzeichnis des Prozesses, also
        // im Projektverzeichnis – dafür legt dieser Test keine Datei an.
        fake.jobName shouldBe "Rechnung-R26-042"
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
        val command = RechnungCommand(FakeTypesetter())
        command.context { echoMessage = { _, message, _, _ -> ausgabe += message.toString() } }
        command.parse(
            arrayOf(oesterreich.toString(), "-s", stammdaten.toString(), "-o", eingaben.resolve("at.pdf").toString()),
        )

        val ergebnis = ausgabe.indexOfFirst { it.contains("PDF/A-1b") }
        val hinweis = ausgabe.indexOfFirst { it.startsWith("Hinweis:") }
        (hinweis > ergebnis) shouldBe true
        ausgabe[hinweis] shouldContain "nicht qualifiziert"
    }
})

private fun quelle(name: String) = Path.of("src/test/resources/$name").readText()
