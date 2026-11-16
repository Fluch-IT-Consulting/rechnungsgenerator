package de.fluchitconsulting.rechnungsgenerator

import de.fluchitconsulting.rechnungsgenerator.core.model.Root
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * #61, #564; der Build verlangt damit `python3` ab 3.9 (#553) und PyYAML. Die Ausgabe geht in
 * Dateien, sonst hinge der Test mit einem hängenden Prozess und die Zeitgrenze griffe nie.
 * Wie die Vorlage des Vergabeskripts zählt, prüft [RechnungsnummerVergabeTest].
 */
class NaechsteNummerSkriptTest : FunSpec({

    val script = File(".claude/skills/rechnung/scripts/naechste-nummer.py").absolutePath

    // Mit dem Muster R\d{2}-\d{3}.
    val sellerConfig = File("src/test/resources/stammdaten.yaml").readText()

    data class ScriptRun(val exitCode: Int, val stdout: String, val stderr: String)

    fun folder(dir: Path): Path = dir.resolve("ablage")

    fun rootFolder(dir: Path): Path = folder(dir).resolve(Root.FOLDER).createDirectories()

    /** Eine Ablage mit je einer Rechnungsdatei zu diesen Nummern, ohne Vergabeskript. */
    fun bareArchive(vararg numbers: String): Path {
        val dir = createTempDirectory("nummern-test")
        folder(dir).createDirectories()
        numbers.forEach { folder(dir).resolve("Rechnung_$it-beispiel.yaml").writeText("nummer: $it\n") }
        return dir
    }

    /** Dazu ein Vergabeskript aus der Vorlage, im Schema R{JJ}-{NNN}. */
    fun archive(vararg numbers: String): Path =
        bareArchive(*numbers).also {
            writeFilledTemplate(rootFolder(it).resolve("rechnungsnummer-vergabe"), """R%y-(\d{3})""", "R%y-{:03d}")
        }

    /** Dazu ein selbst geschriebenes Vergabeskript in der Shell. */
    fun archiveWithShellScript(
        shellScript: String,
        vararg numbers: String,
    ): Path =
        bareArchive(*numbers).also {
            val allocator = rootFolder(it).resolve("rechnungsnummer-vergabe")
            allocator.writeText("#!/bin/sh\n${shellScript.trimIndent()}\n")
            check(allocator.toFile().setExecutable(true))
        }

    fun runWith(
        interpreter: List<String>,
        dir: Path,
        vararg args: String,
    ): ScriptRun {
        val stdout = dir.resolve("stdout.txt")
        val stderr = dir.resolve("stderr.txt")
        val process =
            ProcessBuilder(interpreter + listOf(script, folder(dir).toString()) + args)
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start()
        check(process.waitFor(30, TimeUnit.SECONDS)) { "Das Skript hat nach 30 s nicht geantwortet." }
        return ScriptRun(process.exitValue(), stdout.readText().trim(), stderr.readText().trim())
    }

    fun runScript(
        dir: Path,
        vararg args: String,
    ): ScriptRun = runWith(listOf("python3"), dir, *args)

    fun writeSellerConfig(
        dir: Path,
        content: String = sellerConfig,
    ): String = rootFolder(dir).resolve(Root.SELLER_CONFIG).also { it.writeText(content) }.toString()

    test("die nächste Nummer zählt die belegte weiter") {
        val result = runScript(archive("R26-011", "R26-009"), "--rechnungsdatum", "2026-08-14")

        result.exitCode shouldBe 0
        result.stdout shouldBe "R26-012"
    }

    test("eine anders benannte YAML-Datei zählt über ihre Nummer") {
        // #75. Eine YAML-Datei ohne Nummer, etwa vereinnahmungen.yaml, zählt nicht.
        val dir = archive("R26-011")
        folder(dir).resolve("Abschlagsrechnung-R26-014.yaml").writeText("nummer: R26-014\nart: abschlagsrechnung\n")
        folder(dir).resolve("vereinnahmungen.yaml").writeText("- R26-099\n")

        runScript(dir, "--rechnungsdatum", "2026-08-14").stdout shouldBe "R26-015"
    }

    test("ein Dateiname zählt nicht mit, nur das Feld nummer") {
        val dir = archive("R26-011")
        folder(dir).resolve("Rechnung_R26-020-beispiel.pdf").writeText("")

        runScript(dir, "--rechnungsdatum", "2026-08-14").stdout shouldBe "R26-012"
    }

    test("eine unlesbare YAML-Datei wird genannt, nicht still übergangen") {
        val dir = archive("R26-011")
        folder(dir).resolve("kaputt.yaml").writeText("nummer: [R26-020\n")

        val result = runScript(dir, "--rechnungsdatum", "2026-08-14")

        result.exitCode shouldBe 0
        result.stdout shouldBe "R26-012"
        result.stderr shouldContain "kaputt.yaml"
    }

    test("das Rechnungsdatum geht an das Vergabeskript, nicht der Kalender") {
        val result = runScript(archive("R25-003", "R26-001"), "--rechnungsdatum", "2025-12-31")

        result.stdout shouldBe "R25-004"
    }

    test("ohne Rechnungsdatum gilt heute") {
        val year = "%02d".format(LocalDate.now().year % 100)

        runScript(archive("R$year-005")).stdout shouldBe "R$year-006"
    }

    listOf(
        Triple("am Jahresanfang", "2027-01-11", arrayOf("R26-011", "R26-012")),
        Triple("bei einer rückdatierten Rechnung", "2024-12-30", arrayOf("R25-004", "R26-011")),
        Triple("bei der ersten Rechnung überhaupt", "2026-11-16", emptyArray<String>()),
    ).forEach { (case, invoiceDate, numbers) ->
        test("ohne YAML-Rechnung im Nummernkreis ist die Nummer ein Vorschlag, $case") {
            val result = runScript(archive(*numbers), "--rechnungsdatum", invoiceDate)

            result.exitCode shouldBe 3
            result.stdout shouldBe ""
            result.stderr shouldContain "Vorschlag: R${invoiceDate.substring(2, 4)}-001"
        }
    }

    test("bestätigt wird mit der genannten Nummer") {
        val result = runScript(archive("R26-012"), "--rechnungsdatum", "2027-01-11", "--rechnungsnummer", "R27-001")

        result.exitCode shouldBe 0
        result.stdout shouldBe "R27-001"
    }

    test("eine genannte Nummer, die schon belegt ist, wird abgelehnt") {
        val result = runScript(archive("R26-012"), "--rechnungsnummer", "R26-012")

        result.exitCode shouldBe 1
        result.stdout shouldBe ""
        result.stderr shouldContain "R26-012 ist schon belegt"
        result.stderr shouldContain "Rechnung_R26-012-beispiel.yaml"
    }

    test("eine vergebene Nummer, die schon belegt ist, wird abgelehnt") {
        // Belegte Nummern übersieht dieses Skript; ohne sie nennt es die erste.
        val dir =
            archiveWithShellScript(
                """
                if read -r line; then echo R26-011; else echo R26-001; fi
                """,
                "R26-011",
            )

        val result = runScript(dir, "--rechnungsdatum", "2026-08-14")

        result.exitCode shouldBe 1
        result.stdout shouldBe ""
        result.stderr shouldContain "R26-011 ist schon belegt"
    }

    test("ohne Vergabeskript bricht das Skript ab und nennt die Vorlage") {
        val result = runScript(bareArchive("R26-011"), "--rechnungsdatum", "2026-08-14")

        result.exitCode shouldBe 1
        result.stdout shouldBe ""
        result.stderr shouldContain "${Root.FOLDER}/rechnungsnummer-vergabe"
        result.stderr shouldContain "skills/rechnung/rechnungsnummer-vergabe"
    }

    test("ohne Vergabeskript wird auch eine genannte Nummer nicht ausgegeben") {
        val result = runScript(bareArchive(), "--rechnungsnummer", "R26-001")

        result.exitCode shouldBe 1
        result.stdout shouldBe ""
    }

    test("ein Vergabeskript in einer anderen Sprache, das nur die höchste plus eins rechnet") {
        val naive =
            """
            awk '/^RE-[0-9][0-9][0-9][0-9]$/ { n = substr($0, 4) + 0; if (n > max) max = n }
                 END { printf "RE-%04d\n", max + 1 }'
            """

        val next = runScript(archiveWithShellScript(naive, "RE-1041", "R26-003"))
        val first = runScript(archiveWithShellScript(naive, "R26-003"))

        next.exitCode shouldBe 0
        next.stdout shouldBe "RE-1042"
        first.exitCode shouldBe 3
        first.stdout shouldBe ""
        first.stderr shouldContain "Vorschlag: RE-0001"
    }

    test("ein Vergabeskript, das die belegten Nummern übergeht, liefert immer einen Vorschlag") {
        val result = runScript(archiveWithShellScript("echo EXT-7", "EXT-6"))

        result.exitCode shouldBe 3
        result.stdout shouldBe ""
        result.stderr shouldContain "Vorschlag: EXT-7"
    }

    test("bricht das Vergabeskript ab, endet das Skript mit 1 und nennt nur dessen Meldung") {
        // Mit 3, wie ein Vorschlag: Der Code darf nicht durchgereicht werden.
        val result = runScript(archiveWithShellScript("echo 'Kein Anschluss an die Buchhaltung.' >&2; exit 3"))

        result.exitCode shouldBe 1
        result.stdout shouldBe ""
        result.stderr shouldBe "Kein Anschluss an die Buchhaltung."
    }

    test("gibt das Vergabeskript mehr als eine Zeile aus, bricht das Skript ab") {
        val result = runScript(archiveWithShellScript("echo R26-001; echo R26-002"))

        result.exitCode shouldBe 1
        result.stdout shouldBe ""
        result.stderr shouldContain "genau eine Zeile"
    }

    test("mit Stammdaten wird die Nummer gegen ihr Muster geprüft") {
        val dir = archive("R26-011")

        val result = runScript(dir, "--rechnungsdatum", "2026-08-14", "--stammdaten", writeSellerConfig(dir))

        result.exitCode shouldBe 0
        result.stdout shouldBe "R26-012"
    }

    test("passt die vergebene Nummer nicht zum Muster, hält das die Vergabe an") {
        val dir = archive("R26-011")
        val changed = sellerConfig.replace("""rechnungsnummer-muster: 'R\d{2}-\d{3}'""", """rechnungsnummer-muster: 'RE-\d{4}'""")

        val result = runScript(dir, "--rechnungsdatum", "2026-08-14", "--stammdaten", writeSellerConfig(dir, changed))

        result.exitCode shouldBe 1
        result.stdout shouldBe ""
        result.stderr shouldContain "R26-012 passt nicht zum Muster 'RE-\\d{4}'"
    }

    test("eine genannte Nummer wird gegen das Muster geprüft") {
        val dir = archive()

        val result = runScript(dir, "--rechnungsnummer", "R26-12", "--stammdaten", writeSellerConfig(dir))

        result.exitCode shouldBe 1
        result.stdout shouldBe ""
        result.stderr shouldContain "R26-12 passt nicht zum Muster"
    }

    test("Stammdaten ohne Muster prüfen nicht") {
        val dir = archive("R26-011")
        val withoutPattern = sellerConfig.lines().filterNot { it.startsWith("rechnungsnummer-muster") }.joinToString("\n")

        val result = runScript(dir, "--rechnungsdatum", "2026-08-14", "--stammdaten", writeSellerConfig(dir, withoutPattern))

        result.exitCode shouldBe 0
        result.stdout shouldBe "R26-012"
    }

    test("ohne PyYAML nennt die Meldung einen Befehl für den laufenden Interpreter") {
        // #553. -S lässt site-packages weg, dort liegt PyYAML.
        val result = runWith(listOf("python3", "-S"), archive())

        result.exitCode shouldBe 1
        result.stdout shouldBe ""
        result.stderr shouldContain "PyYAML"
        result.stderr shouldContain " -m pip install"
        result.stderr shouldNotContain "pip3"
    }

    test("ein Datum, das keines ist, wird abgelehnt") {
        val result = runScript(archive(), "--rechnungsdatum", "31.12.2025")

        result.exitCode shouldBe 2
        result.stderr shouldContain "--rechnungsdatum"
    }

    test("die Stammdaten im Ordner der Wurzel sind keine vergebene Nummer") {
        // rglob steigt auch in den versteckten Ordner der Stammdaten (ADR-0015).
        val dir = archive("R26-009")
        writeSellerConfig(dir)

        val result = runScript(dir, "--rechnungsdatum", "2026-08-14")

        result.exitCode shouldBe 0
        result.stdout shouldBe "R26-010"
        result.stderr shouldBe ""
    }
})
