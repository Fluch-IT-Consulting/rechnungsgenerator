package de.fluchitconsulting.rechnungsgenerator.toolchain.process

import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import java.io.File
import kotlin.io.path.createTempDirectory

/** `verapdf-nicht-konform.txt` ist ein echter Mitschnitt, mit JVM-Warnungen vor dem XML. */
class VeraPdfTest : FunSpec({

    fun transcript(name: String) = File("src/test/resources/werkzeugausgaben/$name").readText()

    val pdf = createTempDirectory("verapdf-test").resolve("rechnung.pdf")

    val report = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<report/>"

    fun validate(exitCode: Int, output: String, errorOutput: String = "") =
        VeraPdf(FakeProcessRunner(FakeProcessRunner.Run(exitCode, output, errorOutput))).validate(pdf, "1b")

    test("Exitcode 0 heißt konform") {
        validate(exitCode = 0, output = report) shouldBe VeraPdfReport(compliant = true, xml = report)
    }

    test("Exitcode 1 ist ein Prüfergebnis, kein Fehler") {
        validate(exitCode = 1, output = report).compliant shouldBe false
    }

    test("jeder andere Exitcode ist ein Abbruch des Werkzeugs") {
        val error = shouldThrow<ToolchainException> { validate(exitCode = 3, output = "Can't read file") }

        error.message!! shouldContain "Exitcode 3"
        error.message!! shouldContain "Can't read file"
    }

    // #227
    test("das Protokoll auf stderr gehört nicht zum Bericht") {
        val log = "WARNING: Couldn't find expected endstream keyword(object key = 4 0 obj, offset = 327)"

        validate(exitCode = 0, output = report, errorOutput = log).xml shouldBe report
    }

    test("die JVM-Warnungen vor dem XML schneidet der Adapter ab") {
        val raw = transcript("verapdf-nicht-konform.txt")
        raw.substringBefore("<?xml") shouldContain "WARNING"

        val xml = validate(exitCode = 1, output = raw).xml

        xml shouldStartWith "<?xml"
        xml shouldNotContain "WARNING: "
    }

    test("ohne XML in der Ausgabe sagt die Meldung, was stattdessen kam") {
        val error = shouldThrow<ToolchainException> { validate(exitCode = 1, output = "verapdf: command not found") }

        error.message!! shouldContain "command not found"
    }

    test("die Ausprägung geht als -f an verapdf, mit dem Bericht als XML") {
        val runner = FakeProcessRunner(FakeProcessRunner.Run(exitCode = 0, output = report))

        VeraPdf(runner).validate(pdf, "3u")

        runner.commands.single() shouldBe listOf("verapdf", "--format", "xml", "-f", "3u", pdf.toAbsolutePath().toString())
    }

    // Keiner der beiden Zweige fragt die Maschine, auf der der Test läuft (#245).
    test("fehlt verapdf, nennt die Meldung das Programm und seinen Installationshinweis") {
        val error = shouldThrow<ToolchainException> { VeraPdf(FakeProcessRunner(missing = setOf("verapdf"))).checkAvailable() }

        error.message!! shouldContain "verapdf"
        error.message!! shouldContain "brew install verapdf"
    }

    test("ist verapdf da, meldet checkAvailable nichts") {
        shouldNotThrowAny { VeraPdf(FakeProcessRunner()).checkAvailable() }
    }
})
