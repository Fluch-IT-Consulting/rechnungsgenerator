package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Wie der Bericht des Programms `verapdf` gelesen wird – ohne `verapdf`.
 *
 * Betrifft allein den externen Prüfer: Der eingebaute liefert seine Verstöße
 * als Objekte und hat keinen Bericht zu lesen (ADR-0014).
 *
 * `verapdf-nicht-konform.txt` ist ein echter Mitschnitt und bringt zwei
 * Eigenheiten mit, die man sich nicht ausdenkt: die JVM-Warnungen, die vor dem
 * XML stehen, und einen Bericht, der bestandene *und* verletzte Regeln führt.
 */
class ExternalPdfAValidatorTest : FunSpec({

    fun transcript(name: String) = File("src/test/resources/werkzeugausgaben/$name").readText()

    val pdf = createTempDirectory("pdfavalidator-test").resolve("rechnung.pdf")

    fun validate(exitCode: Int, output: String, errorOutput: String = "") =
        ExternalPdfAValidator(
            FakeProcessRunner(FakeProcessRunner.Run(exitCode, output, errorOutput)),
        ).validate(pdf, PdfAProfile.PDFA_1B)

    // Der einzige erfundene Bericht hier: Für ein konformes PDF gibt es keinen
    // Mitschnitt, weil dieser Fall im Integrationstest ohnehin echt vorkommt.
    val compliant =
        """<?xml version="1.0" encoding="utf-8"?>
        |<report><jobs><job><validationReport><details>
        |<rule status="passed" clause="6.1.2" testNumber="1">
        |<description>Datei beginnt mit einem Header</description></rule>
        |</details></validationReport></job></jobs></report>
        """.trimMargin()

    /**
     * Der Regressionstest zu #227: veraPDF schreibt sein Protokoll auf stderr,
     * und zusammengeführt landete es mitten im Bericht – ein `<jobs>` gefolgt
     * von einem Zeitstempel, und der Parser brach ab. Gelesen wird deshalb
     * allein stdout, und was auf stderr steht, darf beliebig sein.
     */
    test("das Protokoll auf stderr stört den Bericht nicht") {
        val log =
            """
            |Sept 20, 2026 3:51:08 PM org.verapdf.parser.SeekableCOSParser checkStreamLength
            |WARNING: Couldn't find expected endstream keyword(object key = 4 0 obj, offset = 327)
            """.trimMargin()

        val result = validate(exitCode = 0, output = compliant, errorOutput = log)

        result.valid shouldBe true
    }

    test("Exitcode 0 heißt konform") {
        val result = validate(exitCode = 0, output = compliant)

        result.valid shouldBe true
        result.violations.shouldBeEmpty()
        result.describe() shouldContain "gültig"
    }

    test("Exitcode 1 ist ein Prüfergebnis, kein Fehler") {
        // veraPDF unterscheidet über den Exitcode: 1 heißt "nicht konform".
        // Das ist eine Antwort auf unsere Frage und kein Absturz.
        val result = validate(exitCode = 1, output = transcript("verapdf-nicht-konform.txt"))

        result.valid shouldBe false
        // Der Bericht führt bestandene und verletzte Regeln; nur die verletzten
        // interessieren.
        result.violations shouldHaveSize 7
        result.violations.first().clause shouldContain "6."
        result.violations
            .first()
            .description
            .isNotBlank() shouldBe true
    }

    test("die JVM-Warnungen vor dem XML stören die Auswertung nicht") {
        // veraPDF ist selbst eine Java-Anwendung und schreibt Warnungen auf
        // stderr; der Aufruf führt stderr und stdout zusammen, also stehen sie
        // vor dem Bericht. Ein Parser, der von vorne liest, bricht daran ab.
        val raw = transcript("verapdf-nicht-konform.txt")
        raw.substringBefore("<?xml") shouldContain "WARNING"

        validate(exitCode = 1, output = raw).violations shouldHaveSize 7
    }

    test("ohne XML in der Ausgabe sagt die Meldung, was stattdessen kam") {
        val error = shouldThrow<ToolchainException> { validate(exitCode = 1, output = "verapdf: command not found") }

        error.message!! shouldContain "command not found"
    }

    test("jeder andere Exitcode ist ein Abbruch des Werkzeugs") {
        // Alles außer 0 und 1 heißt: veraPDF selbst kam nicht klar, etwa weil
        // die Datei nicht lesbar war. Dann gibt es kein Prüfergebnis.
        val error = shouldThrow<ToolchainException> { validate(exitCode = 3, output = "Can't read file") }

        error.message!! shouldContain "Exitcode 3"
        error.message!! shouldContain "Can't read file"
    }

    test("ein Verstoß beschreibt sich selbst, mit dem Teil der Norm aus dem Bericht") {
        val violation = validate(exitCode = 1, output = transcript("verapdf-nicht-konform.txt")).violations.first()

        violation.specification shouldBe "ISO 19005-1:2005"
        violation.toString() shouldContain "ISO 19005-1:2005"
    }

    test("das Profil geht als -f an verapdf, und das Urteil nennt es") {
        val runner = FakeProcessRunner(FakeProcessRunner.Run(exitCode = 0, output = compliant))

        val result = ExternalPdfAValidator(runner).validate(pdf, PdfAProfile.PDFA_3U)

        runner.commands.single().windowed(2) shouldContain listOf("-f", "3u")
        result.describe() shouldBe "PDF/A-3u: gültig"
    }

    /**
     * Beide Zweige von `checkAvailable`, und keiner davon fragt die Maschine,
     * auf der der Test läuft (#245): Die Suche geht seit dem Umbau über
     * dieselbe Naht wie der Aufruf, und der Fake beantwortet sie.
     *
     * Damit fällt auch der Installationshinweis unter Test. Er steht in einer
     * Meldung, die beim Start kommt – wer sie liest, hat veraPDF gerade nicht
     * und kennt es womöglich nicht einmal.
     */
    test("fehlt verapdf, nennt die Meldung das Programm und seinen Installationshinweis") {
        val validator = ExternalPdfAValidator(FakeProcessRunner(missing = setOf("verapdf")))

        val error = shouldThrow<ToolchainException> { validator.checkAvailable() }

        error.message!! shouldContain "verapdf"
        error.message!! shouldContain "brew install verapdf"
    }

    test("ist verapdf da, meldet checkAvailable nichts") {
        shouldNotThrowAny { ExternalPdfAValidator(FakeProcessRunner()).checkAvailable() }
    }
})
