package de.fluchitconsulting.rechnungsgenerator.skill

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** #550. Die Pfade sind gereicht statt gelesen, sonst schriebe der Testlauf ins Heimatverzeichnis. */
class SkillCommandTest : FunSpec({

    // Ein Paket mit Skill und ein Heimatverzeichnis, in dem es ~/.claude noch nicht gibt.
    fun paths(): SkillPaths {
        val dir = createTempDirectory("skill-test")
        val source =
            dir
                .resolve("paket")
                .resolve("skill")
                .resolve("rechnung")
                .createDirectories()
        source.resolve("SKILL.md").writeText("---\nname: rechnung\n---\n")
        return SkillPaths(
            source = source,
            link =
                dir
                    .resolve("heim")
                    .resolve(".claude")
                    .resolve("skills")
                    .resolve("rechnung"),
        )
    }

    fun run(
        paths: SkillPaths,
        vararg args: String,
    ): Pair<List<String>, List<String>> {
        val out = mutableListOf<String>()
        val err = mutableListOf<String>()
        val command = SkillCommand(paths)
        command.context { echoMessage = { _, message, _, toErr -> (if (toErr) err else out) += message.toString() } }
        command.parse(arrayOf(*args))
        return out to err
    }

    fun ownFolder(paths: SkillPaths): Path =
        paths.link
            .createDirectories()
            .resolve("SKILL.md")
            .also { it.writeText("eigener Skill\n") }

    context("installieren") {
        test("legt den Link an, samt den Ordnern auf dem Weg") {
            val paths = paths()

            val (out, err) = run(paths, "installieren")

            Files.readSymbolicLink(paths.link) shouldBe paths.source
            paths.link.resolve("SKILL.md").readText() shouldContain "name: rechnung"
            out shouldBe listOf("Eingehängt: ${paths.link} -> ${paths.source}")
            err.shouldBeEmpty()
        }

        test("ist wiederholbar: Der eigene Link bleibt, wie er ist") {
            val paths = paths()
            run(paths, "installieren")

            val (out, err) = run(paths, "installieren")

            Files.readSymbolicLink(paths.link) shouldBe paths.source
            out shouldBe listOf("Eingehängt: ${paths.link} -> ${paths.source}")
            err.shouldBeEmpty()
        }

        test("ein Link auf ein anderes Ziel bleibt stehen und wird samt entfernen genannt") {
            val paths = paths()
            val elsewhere = paths.source.resolveSibling("anderswo").createDirectories()
            paths.link.parent.createDirectories()
            paths.link.createSymbolicLinkPointingTo(elsewhere)

            val failure = shouldThrow<CliktError> { run(paths, "installieren") }

            failure.message!! shouldContain paths.link.toString()
            failure.message!! shouldContain elsewhere.toString()
            failure.message!! shouldContain "skill entfernen"
            Files.readSymbolicLink(paths.link) shouldBe elsewhere
        }

        test("ein echter Ordner bleibt stehen") {
            val paths = paths()
            val own = ownFolder(paths)

            val failure = shouldThrow<CliktError> { run(paths, "installieren") }

            failure.message!! shouldContain paths.link.toString()
            failure.message!! shouldContain "kein Link"
            own.readText() shouldBe "eigener Skill\n"
        }

        test("ohne Skill im Paket entsteht kein Link ins Leere") {
            val paths = paths()
            val missing = SkillPaths(source = paths.source.resolveSibling("fehlt"), link = paths.link)

            val failure = shouldThrow<CliktError> { run(missing, "installieren") }

            failure.message!! shouldContain missing.source.toString()
            failure.message!! shouldContain SkillPaths.SOURCE_VARIABLE
            paths.link.exists(LinkOption.NOFOLLOW_LINKS) shouldBe false
        }

        test("ein nicht beschreibbarer Ort nennt den Link und den Grund") {
            // Ein Ort unter einer Datei braucht keine Dateirechte, die ein Lauf als root überginge.
            val paths = paths()
            val blocked = paths.source.resolveSibling("blockiert").also { it.writeText("") }
            val below = SkillPaths(source = paths.source, link = blocked.resolve("skills").resolve("rechnung"))

            val failure = shouldThrow<CliktError> { run(below, "installieren") }

            failure.message!! shouldContain below.link.toString()
            failure.message!! shouldContain "konnte nicht angelegt werden"
        }
    }

    context("entfernen") {
        test("nimmt den eigenen Link weg und nennt, wohin er zeigte") {
            val paths = paths()
            run(paths, "installieren")

            val (out, err) = run(paths, "entfernen")

            paths.link.exists(LinkOption.NOFOLLOW_LINKS) shouldBe false
            paths.source.resolve("SKILL.md").exists() shouldBe true
            out shouldBe listOf("Entfernt: ${paths.link} (zeigte auf ${paths.source})")
            err.shouldBeEmpty()
        }

        test("nimmt auch einen Link ins Leere weg") {
            // Der Fall nach `brew uninstall`.
            val paths = paths()
            val gone = paths.source.resolveSibling("deinstalliert")
            paths.link.parent.createDirectories()
            paths.link.createSymbolicLinkPointingTo(gone)

            val (out, _) = run(paths, "entfernen")

            paths.link.exists(LinkOption.NOFOLLOW_LINKS) shouldBe false
            out shouldBe listOf("Entfernt: ${paths.link} (zeigte auf $gone)")
        }

        test("löscht einen echten Ordner nie") {
            val paths = paths()
            val own = ownFolder(paths)

            val failure = shouldThrow<CliktError> { run(paths, "entfernen") }

            failure.message!! shouldContain paths.link.toString()
            own.readText() shouldBe "eigener Skill\n"
        }

        test("liegt dort nichts, ist das kein Fehler") {
            val paths = paths()

            val (out, err) = run(paths, "entfernen")

            out shouldBe listOf("Nicht eingehängt: ${paths.link} gibt es nicht")
            err.shouldBeEmpty()
        }
    }

    context("status") {
        test("nennt den Link und sein Ziel") {
            val paths = paths()
            run(paths, "installieren")

            run(paths, "status").first shouldBe listOf("Eingehängt: ${paths.link} -> ${paths.source}")
        }

        test("nicht eingehängt ist ein Ergebnis und kein Fehler") {
            val paths = paths()

            val (out, err) = run(paths, "status")

            out shouldBe listOf("Nicht eingehängt: ${paths.link} gibt es nicht")
            err.shouldBeEmpty()
        }

        test("ein Link ins Leere wird als solcher genannt") {
            val paths = paths()
            val gone = paths.source.resolveSibling("deinstalliert")
            paths.link.parent.createDirectories()
            paths.link.createSymbolicLinkPointingTo(gone)

            run(paths, "status").first shouldBe listOf("Eingehängt: ${paths.link} -> $gone (das Ziel gibt es nicht)")
            paths.link.isSymbolicLink() shouldBe true
        }

        test("ein echter Ordner wird genannt, ohne Fehler") {
            val paths = paths()
            ownFolder(paths)

            run(paths, "status").first shouldBe listOf("Kein Link: ${paths.link} ist ein Ordner oder eine Datei")
        }

        test("braucht den Skill im Paket nicht") {
            val paths = paths()
            val missing = SkillPaths(source = paths.source.resolveSibling("fehlt"), link = paths.link)

            run(missing, "status").first shouldBe listOf("Nicht eingehängt: ${paths.link} gibt es nicht")
        }
    }
})
