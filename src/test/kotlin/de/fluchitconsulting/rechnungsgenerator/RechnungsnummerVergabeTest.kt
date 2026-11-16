package de.fluchitconsulting.rechnungsgenerator

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val template = File(".claude/skills/rechnung/rechnungsnummer-vergabe")

/** Die Vorlage mit ihren zwei Zeilen ausgefüllt, so wie der Skill sie in die Ablage legt. */
fun writeFilledTemplate(
    target: Path,
    series: String,
    format: String,
): Path {
    val source = template.readText()
    val empty = listOf("NUMMERNKREIS = r\"\"", "FORMAT = \"\"")
    empty.forEach { check(it in source) { "Die Vorlage hat die leere Zeile $it nicht mehr." } }
    target.writeText(
        source
            .replace(empty[0], "NUMMERNKREIS = r\"$series\"")
            .replace(empty[1], "FORMAT = \"$format\""),
    )
    check(target.toFile().setExecutable(true))
    return target
}

/** #564; die Vorlage des Vergabeskripts, aufgerufen wie von `naechste-nummer.py`. */
class RechnungsnummerVergabeTest : FunSpec({

    data class ScriptRun(val exitCode: Int, val stdout: String, val stderr: String)

    fun run(
        script: Path,
        invoiceDate: String,
        vararg taken: String,
    ): ScriptRun {
        val dir = script.parent
        val stdin = dir.resolve("stdin.txt").also { it.writeText(taken.joinToString("") { "$it\n" }) }
        val stdout = dir.resolve("stdout.txt")
        val stderr = dir.resolve("stderr.txt")
        val process =
            ProcessBuilder(script.toString(), invoiceDate)
                .redirectInput(stdin.toFile())
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start()
        check(process.waitFor(30, TimeUnit.SECONDS)) { "Die Vorlage hat nach 30 s nicht geantwortet." }
        return ScriptRun(process.exitValue(), stdout.readText().trim(), stderr.readText().trim())
    }

    fun filled(
        series: String,
        format: String,
    ): Path = writeFilledTemplate(createTempDirectory("vergabe-test").resolve("rechnungsnummer-vergabe"), series, format)

    val yearAndCounter = """R%y-(\d{3})""" to "R%y-{:03d}"

    data class Case(val example: String, val series: String, val format: String, val taken: List<String>, val expected: String)

    // Die Beispiele aus dem Kopf der Vorlage.
    listOf(
        Case("Jahr und Zähler", """R%y-(\d{3})""", "R%y-{:03d}", listOf("R26-011", "R25-099"), "R26-012"),
        Case("Jahr und vierstelliger Zähler", """%Y-(\d{4})""", "%Y-{:04d}", listOf("2026-0041", "2025-0099"), "2026-0042"),
        Case("Jahr, Monat und Zähler", """%Y-%m-(\d{3})""", "%Y-%m-{:03d}", listOf("2026-11-002", "2026-10-009"), "2026-11-003"),
        Case("durchlaufend", """RE-(\d{4})""", "RE-{:04d}", listOf("RE-1041", "R26-099"), "RE-1042"),
        Case("Zähler vor dem Jahr", """(\d{4})/%Y""", "{:04d}/%Y", listOf("0007/2026", "0009/2025"), "0008/2026"),
    ).forEach { (example, series, format, taken, expected) ->
        test("$example: die höchste Nummer des Nummernkreises plus eins") {
            val result = run(filled(series, format), "2026-11-16", *taken.toTypedArray())

            result.exitCode shouldBe 0
            result.stdout shouldBe expected
        }
    }

    test("ohne belegte Nummern die erste des Nummernkreises") {
        val (series, format) = yearAndCounter

        run(filled(series, format), "2027-01-11").stdout shouldBe "R27-001"
    }

    test("der Nummernkreis kommt aus dem Rechnungsdatum, nicht aus dem Kalender") {
        val (series, format) = yearAndCounter

        run(filled(series, format), "2025-12-31", "R25-003", "R26-001").stdout shouldBe "R25-004"
    }

    test("ein neuer Monat ist ein neuer Nummernkreis") {
        run(filled("""%Y-%m-(\d{3})""", "%Y-%m-{:03d}"), "2026-12-01", "2026-11-002").stdout shouldBe "2026-12-001"
    }

    test("Lücken werden nicht aufgefüllt") {
        val (series, format) = yearAndCounter

        run(filled(series, format), "2026-11-16", "R26-001", "R26-005").stdout shouldBe "R26-006"
    }

    test("Nummern aus der Zeit vor einem Wechsel des Formats werden übergangen") {
        val result = run(filled("""RE-(\d{4})""", "RE-{:04d}"), "2026-11-16", "R26-011", "RE-0003", "2026/17", "")

        result.exitCode shouldBe 0
        result.stdout shouldBe "RE-0004"
    }

    test("so wie sie im Paket liegt, bricht die Vorlage ab") {
        val shipped = createTempDirectory("vergabe-test").resolve("rechnungsnummer-vergabe")
        check(template.copyTo(shipped.toFile()).setExecutable(true))

        val result = run(shipped, "2026-11-16")

        result.exitCode shouldBe 1
        result.stdout shouldBe ""
        result.stderr shouldContain "NUMMERNKREIS und FORMAT"
    }

    test("ein erschöpfter Nummernkreis bricht ab, statt die Nummer zu verbreitern") {
        val (series, format) = yearAndCounter

        val result = run(filled(series, format), "2026-11-16", "R26-999")

        result.exitCode shouldBe 1
        result.stdout shouldBe ""
        result.stderr shouldContain "erschöpft"
        result.stderr shouldContain "R26-1000"
    }

    test("widersprechen sich die zwei Zeilen, bricht die Vorlage ab") {
        val result = run(filled("""R%y-(\d{3})""", "R%y-{:04d}"), "2026-11-16")

        result.exitCode shouldBe 1
        result.stdout shouldBe ""
        result.stderr shouldContain "widersprechen sich"
    }

    test("NUMMERNKREIS ohne Klammer bricht ab") {
        val result = run(filled("""R%y-\d{3}""", "R%y-{:03d}"), "2026-11-16")

        result.exitCode shouldBe 1
        result.stderr shouldContain "Klammer"
    }
})
