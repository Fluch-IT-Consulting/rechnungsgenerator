package de.fluchitconsulting.rechnungsgenerator.skill

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.createTempDirectory

/** #550 */
class SkillPathsTest : FunSpec({

    val home = Path.of("/heim/beispiel")
    val bundled = Path.of("/paket/skill/rechnung")

    fun paths(vararg env: Pair<String, String>) = SkillPaths.of(env = mapOf(*env)::get, home = home, bundled = { bundled })

    test("ohne Variablen: der Skill neben lib/ und der Link unter ~/.claude") {
        val paths = paths()

        paths.source shouldBe bundled
        paths.link shouldBe home.resolve(".claude/skills/rechnung")
    }

    test("CLAUDE_CONFIG_DIR ersetzt ~/.claude") {
        paths("CLAUDE_CONFIG_DIR" to "/anderswo/claude").link shouldBe Path.of("/anderswo/claude/skills/rechnung")
    }

    test("RECHNUNGSGENERATOR_SKILL ersetzt den Skill neben lib/") {
        paths("RECHNUNGSGENERATOR_SKILL" to "/opt/rechnungsgenerator/skill/rechnung").source shouldBe
            Path.of("/opt/rechnungsgenerator/skill/rechnung")
    }

    test("eine leere Variable gilt als nicht gesetzt") {
        val paths = paths("CLAUDE_CONFIG_DIR" to "", "RECHNUNGSGENERATOR_SKILL" to " ")

        paths.source shouldBe bundled
        paths.link shouldBe home.resolve(".claude/skills/rechnung")
    }

    test("der hereingereichte Pfad bleibt ein Link und wird nicht aufgelöst") {
        // Wie opt/<name> bei Homebrew: ein Link auf die Fassung in Cellar/.
        val dir = createTempDirectory("skill-pfade-test")
        val version =
            dir
                .resolve("Cellar")
                .resolve("0.8.0")
                .resolve("skill")
                .resolve("rechnung")
                .createDirectories()
        val opt = dir.resolve("opt").createSymbolicLinkPointingTo(version.parent.parent)

        paths("RECHNUNGSGENERATOR_SKILL" to "$opt/skill/rechnung").source shouldBe opt.resolve("skill").resolve("rechnung")
    }
})
