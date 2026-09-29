package de.fluchitconsulting.rechnungsgenerator

import de.fluchitconsulting.rechnungsgenerator.core.model.Root
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * #61, #66. Nur das Skript sieht, ob eine Rechnung storniert wurde: Das steht
 * in einer anderen Datei, und der Generator liest nur eine (ADR-0001).
 */
class OffeneAbschlaegeSkriptTest : FunSpec({

    val script = File(".claude/skills/rechnung/scripts/offene-abschlaege.py").absolutePath
    val customer = "Beispiel Software GmbH"

    data class ScriptRun(val exitCode: Int, val stdout: String, val stderr: String)

    // Aus einem Beispiel des Generators, damit eine Umbenennung hier ankommt (#409, #139).
    val inDollar =
        Path
            .of("src/test/resources/amerikanische-rechnung.yaml")
            .readLines()
            .single { it.startsWith("waehrung:") }
            .also { check(it.endsWith("USD")) { "Das Beispiel steht nicht mehr in US-Dollar: $it" } }

    fun currencyLine(dollar: Boolean) = if (dollar) "$inDollar\n" else ""

    fun prepaymentInvoice(number: String, date: String, dollar: Boolean = false) =
        currencyLine(dollar) +
            """
            nummer: $number
            art: abschlagsrechnung
            datum: $date
            kunde:
              name: $customer
            """.trimIndent()

    fun finalInvoice(number: String, vararg deducted: String, dollar: Boolean = false) =
        currencyLine(dollar) +
            """
            nummer: $number
            art: schlussrechnung
            datum: 2026-11-05
            kunde:
              name: $customer
            abschlaege:
            """.trimIndent() +
            deducted.joinToString("") {
                "\n  - rechnungsnummer: $it"
            }

    fun cancellation(number: String, cancelled: String, dollar: Boolean = false) =
        currencyLine(dollar) +
            """
            nummer: $number
            art: stornorechnung
            datum: 2026-11-10
            storniert:
              rechnungsnummer: $cancelled
              rechnungsdatum: 2026-06-15
            kunde:
              name: $customer
            """.trimIndent()

    // Der Dateiname bestimmt die Reihenfolge, in der das Skript liest.
    fun archive(vararg files: Pair<String, String>): Path {
        val dir = createTempDirectory("offene-abschlaege-test")
        files.forEach { (name, content) -> dir.resolve(name).writeText(content) }
        return dir
    }

    fun runScript(dir: Path): ScriptRun {
        val stdout = dir.resolve("stdout.txt")
        val stderr = dir.resolve("stderr.txt")
        val process =
            ProcessBuilder("python3", script, dir.toString(), customer)
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start()
        check(process.waitFor(30, TimeUnit.SECONDS)) { "Das Skript hat nach 30 s nicht geantwortet." }
        return ScriptRun(process.exitValue(), stdout.readText().trim(), stderr.readText().trim())
    }

    test("offen ist, was keine Schlussrechnung absetzt") {
        val dir =
            archive(
                "r009.yaml" to prepaymentInvoice("R26-009", "2026-06-15"),
                "r011.yaml" to prepaymentInvoice("R26-011", "2026-07-15"),
                "r014.yaml" to finalInvoice("R26-014", "R26-009"),
            )

        val result = runScript(dir)

        result.exitCode shouldBe 0
        result.stdout shouldContain "R26-011\t2026-07-15\t"
        result.stdout shouldNotContain "R26-009"
    }

    test("eine stornierte Abschlagsrechnung gilt nicht als offen") {
        // Fall 1 aus #66
        val dir =
            archive(
                "r009.yaml" to prepaymentInvoice("R26-009", "2026-06-15"),
                "r010.yaml" to cancellation("R26-010", "R26-009"),
            )

        val result = runScript(dir)

        result.stdout shouldBe ""
        result.stderr shouldContain "R26-009 ist durch R26-010 storniert"
        result.stderr shouldContain "abgesetzt oder storniert"
    }

    test("eine stornierte Schlussrechnung gibt ihre Abschläge wieder frei") {
        // Fall 2 aus #66. Das Storno liegt in der Lesereihenfolge hinter seiner Schlussrechnung.
        val dir =
            archive(
                "r009.yaml" to prepaymentInvoice("R26-009", "2026-06-15"),
                "r014.yaml" to finalInvoice("R26-014", "R26-009"),
                "r015.yaml" to cancellation("R26-015", "R26-014"),
            )

        val result = runScript(dir)

        result.stdout shouldContain "R26-009\t2026-06-15\t"
        result.stderr shouldContain "R26-009 war in der stornierten Schlussrechnung R26-014 abgesetzt"
    }

    test("stand der Abschlag in zwei stornierten Schlussrechnungen, nennt der Vermerk beide") {
        val dir =
            archive(
                "r009.yaml" to prepaymentInvoice("R26-009", "2026-06-15"),
                "r014.yaml" to finalInvoice("R26-014", "R26-009"),
                "r015.yaml" to cancellation("R26-015", "R26-014"),
                "r016.yaml" to finalInvoice("R26-016", "R26-009"),
                "r017.yaml" to cancellation("R26-017", "R26-016"),
            )

        val result = runScript(dir)

        result.stdout shouldContain "R26-009\t2026-06-15\t"
        result.stderr shouldContain "in den stornierten Schlussrechnungen R26-014, R26-016 abgesetzt"
    }

    test("eine gültige Schlussrechnung genügt, damit der Abschlag abgesetzt bleibt") {
        val dir =
            archive(
                "r009.yaml" to prepaymentInvoice("R26-009", "2026-06-15"),
                "r014.yaml" to finalInvoice("R26-014", "R26-009"),
                "r015.yaml" to cancellation("R26-015", "R26-014"),
                "r016.yaml" to finalInvoice("R26-016", "R26-009"),
            )

        val result = runScript(dir)

        result.stdout shouldBe ""
        result.stderr shouldNotContain "Achtung"
    }

    test("zwei gültige Schlussrechnungen über denselben Abschlag werden gemeldet") {
        // #67: Der Generator sieht das nur innerhalb einer Datei.
        val dir =
            archive(
                "r009.yaml" to prepaymentInvoice("R26-009", "2026-06-15"),
                "r014.yaml" to finalInvoice("R26-014", "R26-009"),
                "r018.yaml" to finalInvoice("R26-018", "R26-009"),
            )

        val result = runScript(dir)

        result.stdout shouldBe ""
        result.stderr shouldContain
            "Achtung: R26-009 wird mehrfach abgesetzt – in den Schlussrechnungen R26-014, R26-018."
    }

    test("ein stornierter Abschlag in einer gültigen Schlussrechnung wird gemeldet") {
        val dir =
            archive(
                "r009.yaml" to prepaymentInvoice("R26-009", "2026-06-15"),
                "r010.yaml" to cancellation("R26-010", "R26-009"),
                "r014.yaml" to finalInvoice("R26-014", "R26-009"),
            )

        val result = runScript(dir)

        result.stdout shouldBe ""
        result.stderr shouldContain "Achtung: R26-009 ist durch R26-010 storniert, wird aber in der Schlussrechnung R26-014 abgesetzt."
    }

    context("Währung (#409)") {
        // ADR-0001

        test("eine Schlussrechnung, die einen Abschlag anderer Währung absetzt, wird gemeldet") {
            val dir =
                archive(
                    "r009.yaml" to prepaymentInvoice("R26-009", "2026-06-15", dollar = true),
                    "r014.yaml" to finalInvoice("R26-014", "R26-009"),
                )

            val result = runScript(dir)

            result.stdout shouldBe ""
            result.stderr shouldContain "Achtung: R26-009 steht in USD, wird aber in der Schlussrechnung R26-014 in EUR abgesetzt."
        }

        test("ein Storno in anderer Währung als seine Abschlagsrechnung wird gemeldet") {
            val dir =
                archive(
                    "r009.yaml" to prepaymentInvoice("R26-009", "2026-06-15", dollar = true),
                    "r010.yaml" to cancellation("R26-010", "R26-009"),
                )

            val result = runScript(dir)

            result.stderr shouldContain "Achtung: Das Storno R26-010 steht in EUR, die stornierte Rechnung R26-009 in USD."
        }

        test("ein Storno in anderer Währung als seine Schlussrechnung wird gemeldet, einmal") {
            val dir =
                archive(
                    "r009.yaml" to prepaymentInvoice("R26-009", "2026-06-15", dollar = true),
                    "r011.yaml" to prepaymentInvoice("R26-011", "2026-07-15", dollar = true),
                    "r014.yaml" to finalInvoice("R26-014", "R26-009", "R26-011", dollar = true),
                    "r015.yaml" to cancellation("R26-015", "R26-014"),
                )

            val result = runScript(dir)

            result.stderr.split("Das Storno R26-015 steht in EUR, die stornierte Rechnung R26-014 in USD.").size shouldBe 2
        }

        test("in derselben Währung meldet das Skript nichts, auch klein geschrieben") {
            val dir =
                archive(
                    "r009.yaml" to prepaymentInvoice("R26-009", "2026-06-15", dollar = true),
                    "r010.yaml" to cancellation("R26-010", "R26-009").replace("nummer:", "waehrung: usd\nnummer:"),
                    "r011.yaml" to prepaymentInvoice("R26-011", "2026-07-15", dollar = true),
                    "r014.yaml" to finalInvoice("R26-014", "R26-011", dollar = true),
                )

            runScript(dir).stderr shouldNotContain "Achtung"
        }
    }

    test("ohne Storno bleibt die Schlussmeldung, wie sie war") {
        val dir =
            archive(
                "r009.yaml" to prepaymentInvoice("R26-009", "2026-06-15"),
                "r014.yaml" to finalInvoice("R26-014", "R26-009"),
            )

        val result = runScript(dir)

        result.stdout shouldBe ""
        result.stderr shouldContain "Alle 1 Abschlagsrechnungen an '$customer' sind schon abgesetzt."
    }

    // Die Beispielrechnungen des Generators: Benennt er einen Schlüssel um, blieben
    // die selbstgeschriebenen YAMLs darüber grün (#139).
    fun examples(vararg names: String): Path {
        val dir = createTempDirectory("offene-abschlaege-beispiele")
        names.forEach { Path.of("src/test/resources", it).copyTo(dir.resolve(it)) }
        return dir
    }

    test("in den Beispielrechnungen setzt die Schlussrechnung beide Abschläge ab") {
        // Eine Umbenennung von `storniert` fiele hier nicht auf.
        val dir =
            examples(
                "abschlagsrechnung.yaml",
                "abschlagsrechnung-2.yaml",
                "schlussrechnung.yaml",
                "stornorechnung.yaml",
            )

        val result = runScript(dir)

        result.exitCode shouldBe 0
        result.stdout shouldBe ""
        result.stderr shouldContain "Alle 2 Abschlagsrechnungen an '$customer' sind schon abgesetzt."
    }

    test("ohne Schlussrechnung ist die Abschlagsrechnung aus den Beispielen offen") {
        val dir = examples("abschlagsrechnung-2.yaml")

        val result = runScript(dir)

        result.exitCode shouldBe 0
        result.stdout shouldBe "R26-011\t2026-08-20\t${dir.resolve("abschlagsrechnung-2.yaml")}"
    }

    test("die Stammdaten im Ordner der Wurzel stören nicht") {
        // rglob steigt auch in den versteckten Ordner der Stammdaten (ADR-0015).
        val dir = archive("r011.yaml" to prepaymentInvoice("R26-011", "2026-07-15"))
        dir
            .resolve(Root.FOLDER)
            .createDirectories()
            .resolve(Root.SELLER_CONFIG)
            .writeText(File("src/test/resources/stammdaten.yaml").readText())

        val result = runScript(dir)

        result.exitCode shouldBe 0
        result.stdout shouldContain "R26-011"
        result.stderr shouldNotContain Root.SELLER_CONFIG
    }
})
