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
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Das Arbeitsverzeichnis ist gereicht statt gelesen, sonst schriebe der Testlauf ins Projekt. */
class SetupCommandTest : FunSpec({

    fun runSetup(dir: Path): Pair<List<String>, List<String>> {
        val out = mutableListOf<String>()
        val err = mutableListOf<String>()
        val command = SetupCommand(dir)
        command.context { echoMessage = { _, message, _, toErr -> (if (toErr) err else out) += message.toString() } }
        command.parse(emptyArray())
        return out to err
    }

    test("legt die Vorlage an und nennt allein ihren Pfad") {
        val dir = createTempDirectory("einrichten-test")

        val (out, err) = runSetup(dir)

        val created = dir.resolve(Root.FOLDER).resolve(Root.SELLER_CONFIG)
        created.readText() shouldBe SellerConfigTemplate.text
        out shouldBe listOf(created.toString())
        err.shouldBeEmpty()
    }

    test("die angelegte Datei wird von der Suche gefunden") {
        // ADR-0015
        val dir = createTempDirectory("einrichten-test")
        runSetup(dir)
        val invoice =
            dir
                .resolve("2026")
                .createDirectories()
                .resolve("R26-009.yaml")
                .also { it.writeText("") }

        Root.of(invoice).sellerConfig shouldBe dir.resolve(Root.FOLDER).resolve(Root.SELLER_CONFIG)
    }

    test("eine vorhandene Datei wird nie überschrieben") {
        val dir = createTempDirectory("einrichten-test")
        val existing = dir.resolve(Root.FOLDER).createDirectories().resolve(Root.SELLER_CONFIG)
        existing.writeText("firma: Schon da\n")

        val failure = shouldThrow<CliktError> { runSetup(dir) }

        failure.message!! shouldContain existing.toString()
        existing.readText() shouldBe "firma: Schon da\n"
    }

    // #501: Die Zusage hält der Schreibaufruf, nicht die Prüfung davor. Ein Link ins Leere bestünde exists().
    test("ein Link ins Leere an Stelle der Datei wird nicht verfolgt, sondern gilt als vorhanden") {
        val dir = createTempDirectory("einrichten-test")
        val elsewhere = dir.resolve("anderswo.yaml")
        val link =
            dir
                .resolve(Root.FOLDER)
                .createDirectories()
                .resolve(Root.SELLER_CONFIG)
                .createSymbolicLinkPointingTo(elsewhere)

        val failure = shouldThrow<CliktError> { runSetup(dir) }

        failure.message shouldBe "Die Datei $link gibt es schon; eingerichtet ist hier also bereits."
        elsewhere.exists() shouldBe false
    }

    test("ein nicht beschreibbarer Ort nennt die Zieldatei und den Grund") {
        // Ein Ort unter einer Datei braucht keine Dateirechte, die ein Lauf als root überginge.
        val blocked = createTempDirectory("einrichten-test").resolve("blockiert").also { it.writeText("") }
        val target = blocked.resolve(Root.FOLDER).resolve(Root.SELLER_CONFIG)

        val failure = shouldThrow<CliktError> { runSetup(blocked) }

        failure.message shouldBe "Die Datei $target konnte nicht geschrieben werden: Not a directory"
    }

    test("gibt es den Ordner ohne die Datei, entsteht die Datei und sonst nichts") {
        // #275
        val dir = createTempDirectory("einrichten-test")
        val folder = dir.resolve(Root.FOLDER).createDirectories()
        folder.resolve("logo.tex").writeText("\\fill (0,0) circle (1);")

        runSetup(dir)

        folder.resolve(Root.SELLER_CONFIG).exists() shouldBe true
        folder.resolve("logo.tex").readText() shouldContain "circle"
    }

    context("eine Wurzel oberhalb") {
        test("hält das Einrichten nicht auf, meldet sich aber auf stderr") {
            val upper = createTempDirectory("einrichten-test")
            upper
                .resolve(Root.FOLDER)
                .createDirectories()
                .resolve(Root.SELLER_CONFIG)
                .writeText("firma: Erste\n")
            val lower = upper.resolve("zweitfirma").createDirectories()

            val (out, err) = runSetup(lower)

            out shouldBe listOf(lower.resolve(Root.FOLDER).resolve(Root.SELLER_CONFIG).toString())
            err.single() shouldContain upper.toString()
            err.single() shouldContain "ab hier"
        }

        test("ohne eine darüber bleibt stderr leer") {
            runSetup(createTempDirectory("einrichten-test")).second.shouldBeEmpty()
        }
    }
})
