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
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Prüft `scripts/offene-abschlaege.py` aus dem `rechnung`-Skill, indem es
 * wirklich läuft – wie `NaechsteNummerSkriptTest` das Skript daneben (#61).
 *
 * Das Skript trägt seit #66 eine Regel, die sonst niemand tragen kann: Ob eine
 * Rechnung storniert wurde, steht in einer *anderen* Datei, und der Generator
 * sieht beim Erzeugen nur eine (ADR-0001). Beide Richtungen kosten Geld –
 * ein stornierter Abschlag, der abgesetzt wird, senkt den Rechnungsbetrag zu
 * Unrecht; ein Abschlag, der wegen einer stornierten Schlussrechnung als
 * abgesetzt gilt, lässt den Kunden zweimal zahlen.
 */
class OffeneAbschlaegeSkriptTest : FunSpec({

    val script = File(".claude/skills/rechnung/scripts/offene-abschlaege.py").absolutePath
    val customer = "Beispiel Software GmbH"

    data class ScriptRun(val exitCode: Int, val stdout: String, val stderr: String)

    fun prepaymentInvoice(number: String, date: String) =
        """
        nummer: $number
        art: abschlagsrechnung
        datum: $date
        kunde:
          name: $customer
        """.trimIndent()

    fun finalInvoice(number: String, vararg deducted: String) =
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

    fun cancellation(number: String, cancelled: String) =
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

    // Der Dateiname bestimmt die Reihenfolge, in der das Skript liest – deshalb
    // steht er in den Tests und wird nicht aus der Nummer abgeleitet.
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
        // Fall 1 aus #66: Das Geld ist zurück, abzusetzen gibt es nichts mehr.
        val dir =
            archive(
                "r009.yaml" to prepaymentInvoice("R26-009", "2026-06-15"),
                "r010.yaml" to cancellation("R26-010", "R26-009"),
            )

        val result = runScript(dir)

        result.stdout shouldBe ""
        result.stderr shouldContain "R26-009 ist durch R26-010 storniert"
        // Die Schlussmeldung nennt den Grund, den es hier wirklich gibt.
        result.stderr shouldContain "abgesetzt oder storniert"
    }

    test("eine stornierte Schlussrechnung gibt ihre Abschläge wieder frei") {
        // Fall 2 aus #66. Das Storno liegt hinter seiner Schlussrechnung –
        // deshalb entscheidet das Skript erst, wenn alle Dateien gelesen sind.
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
        // Sonst läse man aus der Meldung eine Geschichte, die zu kurz ist.
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
        // Nur eine der beiden ist storniert – die andere setzt weiter ab.
        val dir =
            archive(
                "r009.yaml" to prepaymentInvoice("R26-009", "2026-06-15"),
                "r014.yaml" to finalInvoice("R26-014", "R26-009"),
                "r015.yaml" to cancellation("R26-015", "R26-014"),
                "r016.yaml" to finalInvoice("R26-016", "R26-009"),
            )

        val result = runScript(dir)

        result.stdout shouldBe ""
        // Und es ist auch keine doppelte Absetzung: Die stornierte setzt nichts ab.
        result.stderr shouldNotContain "Achtung"
    }

    test("zwei gültige Schlussrechnungen über denselben Abschlag werden gemeldet") {
        // #67: Der Generator lehnt dieselbe Nummer zweimal ab, aber nur
        // innerhalb einer Datei. Hier zahlte der Kunde einmal zu wenig.
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
        // Weder offen noch in Ordnung: Die Schlussrechnung setzt Geld ab, das
        // der Kunde zurückbekommen hat.
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

    // Die Tests darüber schreiben ihr YAML selbst, und zwar nur die Schlüssel,
    // die das Skript liest. Benennt der Generator einen davon um, bleiben sie
    // grün, während das Skript echte Dateien still für "keine Rechnung" hält
    // (#139). Die beiden folgenden lesen deshalb die Beispielrechnungen, die
    // andere Tests mit dem Generator lesen: Eine Umbenennung muss dort
    // nachgezogen werden und kommt so auch hier an.
    fun examples(vararg names: String): Path {
        val dir = createTempDirectory("offene-abschlaege-beispiele")
        names.forEach { Path.of("src/test/resources", it).copyTo(dir.resolve(it)) }
        return dir
    }

    test("in den Beispielrechnungen setzt die Schlussrechnung beide Abschläge ab") {
        // Das Storno der Beispiele hebt eine gewöhnliche Rechnung auf und darf
        // am Ergebnis nichts ändern. Eine Umbenennung von `storniert` fiele
        // hier deshalb nicht auf – die decken die Beispiele nicht ab.
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
        // Seit ADR-0015 liegen sie in einem versteckten Ordner, in den rglob
        // hineinsteigt. Sie tragen keine `art` und fallen deshalb durch – ohne
        // Warnung, denn lesbar sind sie ja.
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
