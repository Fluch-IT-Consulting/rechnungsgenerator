package de.fluchitconsulting.rechnungsgenerator.setup

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import de.fluchitconsulting.rechnungsgenerator.core.model.Root
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * `einrichten` von den Argumenten bis zur Datei.
 *
 * Das Arbeitsverzeichnis ist gereicht statt gelesen: Sonst schriebe der
 * Testlauf in sein eigenes Projektverzeichnis, und "legt nichts Vorhandenes
 * um" ließe sich nur mit angehaltenem Atem prüfen.
 */
class SetupCommandTest : FunSpec({

    /** Führt das Kommando aus und sammelt, was auf welchen Kanal ginge. */
    fun lauf(verzeichnis: Path): Pair<List<String>, List<String>> {
        val out = mutableListOf<String>()
        val err = mutableListOf<String>()
        val command = SetupCommand(verzeichnis)
        command.context { echoMessage = { _, message, _, toErr -> (if (toErr) err else out) += message.toString() } }
        command.parse(emptyArray())
        return out to err
    }

    test("legt die Vorlage an und nennt allein ihren Pfad") {
        val verzeichnis = createTempDirectory("einrichten-test")

        val (out, err) = lauf(verzeichnis)

        val angelegt = verzeichnis.resolve(Root.FOLDER).resolve(Root.SELLER_CONFIG)
        angelegt.readText() shouldBe SellerConfigTemplate.text
        out shouldBe listOf(angelegt.toString())
        err.shouldBeEmpty()
    }

    test("die angelegte Datei wird von der Suche gefunden") {
        // Der eigentliche Zweck: Das Verzeichnis ist danach eine Wurzel im
        // Sinne von ADR-0015, und zwar für jede Rechnung darunter.
        val verzeichnis = createTempDirectory("einrichten-test")
        lauf(verzeichnis)
        val rechnung =
            verzeichnis
                .resolve("2026")
                .createDirectories()
                .resolve("R26-009.yaml")
                .also { it.writeText("") }

        Root.of(rechnung).sellerConfig shouldBe verzeichnis.resolve(Root.FOLDER).resolve(Root.SELLER_CONFIG)
    }

    test("eine vorhandene Datei wird nie überschrieben") {
        // Darin steht, was jemand von Hand eingetragen hat. Kein --force.
        val verzeichnis = createTempDirectory("einrichten-test")
        val vorhanden = verzeichnis.resolve(Root.FOLDER).createDirectories().resolve(Root.SELLER_CONFIG)
        vorhanden.writeText("firma: Schon da\n")

        val failure = shouldThrow<CliktError> { lauf(verzeichnis) }

        failure.message!! shouldContain vorhanden.toString()
        vorhanden.readText() shouldBe "firma: Schon da\n"
    }

    test("gibt es den Ordner ohne die Datei, entsteht die Datei und sonst nichts") {
        // Der Ausweg aus dem Abbruch "Ordner ohne stammdaten.yaml" (#275).
        val verzeichnis = createTempDirectory("einrichten-test")
        val ordner = verzeichnis.resolve(Root.FOLDER).createDirectories()
        ordner.resolve("logo.tex").writeText("\\fill (0,0) circle (1);")

        lauf(verzeichnis)

        ordner.resolve(Root.SELLER_CONFIG).exists() shouldBe true
        ordner.resolve("logo.tex").readText() shouldContain "circle"
    }

    context("eine Wurzel oberhalb") {
        test("hält das Einrichten nicht auf, meldet sich aber auf stderr") {
            // Verschachteln ist der vorgesehene Weg für einen zweiten
            // Rechnungssteller – aus Versehen aber eine Falle. Also Hinweis,
            // kein Abbruch.
            val oben = createTempDirectory("einrichten-test")
            oben
                .resolve(Root.FOLDER)
                .createDirectories()
                .resolve(Root.SELLER_CONFIG)
                .writeText("firma: Erste\n")
            val unten = oben.resolve("zweitfirma").createDirectories()

            val (out, err) = lauf(unten)

            out shouldBe listOf(unten.resolve(Root.FOLDER).resolve(Root.SELLER_CONFIG).toString())
            err.single() shouldContain oben.toString()
            // Der Hinweis sagt auch, was jetzt gilt – sonst wüsste man nur,
            // dass etwas ist, nicht was.
            err.single() shouldContain "ab hier"
        }

        test("ohne eine darüber bleibt stderr leer") {
            lauf(createTempDirectory("einrichten-test")).second.shouldBeEmpty()
        }
    }
})
