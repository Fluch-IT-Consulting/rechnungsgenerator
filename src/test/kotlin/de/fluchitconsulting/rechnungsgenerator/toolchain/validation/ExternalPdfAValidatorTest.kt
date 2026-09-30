package de.fluchitconsulting.rechnungsgenerator.toolchain.validation

import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import de.fluchitconsulting.rechnungsgenerator.toolchain.process.FakeProcessRunner
import de.fluchitconsulting.rechnungsgenerator.toolchain.process.VeraPdf
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
 * `verapdf-nicht-konform.txt` ist ein echter Mitschnitt, mit einem Bericht,
 * der auch die bestandenen Regeln führt. Das Protokoll von `verapdf` prüft
 * `VeraPdfTest`.
 */
class ExternalPdfAValidatorTest : FunSpec({

    fun transcript(name: String) = File("src/test/resources/werkzeugausgaben/$name").readText()

    val pdf = createTempDirectory("pdfavalidator-test").resolve("rechnung.pdf")

    fun validator(exitCode: Int, output: String) =
        ExternalPdfAValidator(
            VeraPdf(FakeProcessRunner(FakeProcessRunner.Run(exitCode, output))),
        )

    fun validate(exitCode: Int, output: String) = validator(exitCode, output).validate(pdf, PdfAProfile.PDFA_1B)

    // Der einzige erfundene Bericht; echt kommt er im Integrationstest vor.
    val compliant =
        """<?xml version="1.0" encoding="utf-8"?>
        |<report><jobs><job><validationReport><details>
        |<rule status="passed" clause="6.1.2" testNumber="1">
        |<description>Datei beginnt mit einem Header</description></rule>
        |</details></validationReport></job></jobs></report>
        """.trimMargin()

    test("konform heißt gültig, und die bestandenen Regeln sind keine Verstöße") {
        val result = validate(exitCode = 0, output = compliant)

        result.valid shouldBe true
        result.violations.shouldBeEmpty()
        result.describe() shouldContain "gültig"
    }

    test("nicht konform liefert die Verstöße aus dem Bericht") {
        val result = validate(exitCode = 1, output = transcript("verapdf-nicht-konform.txt"))

        result.valid shouldBe false
        result.violations shouldHaveSize 7
        result.violations.first().clause shouldContain "6."
        result.violations
            .first()
            .description
            .isNotBlank() shouldBe true
    }

    test("ein Verstoß beschreibt sich selbst, mit dem Teil der Norm aus dem Bericht") {
        val violation = validate(exitCode = 1, output = transcript("verapdf-nicht-konform.txt")).violations.first()

        violation.specification shouldBe "ISO 19005-1:2005"
        violation.toString() shouldContain "ISO 19005-1:2005"
    }

    test("das Profil geht als -f an verapdf, und das Urteil nennt es") {
        val runner = FakeProcessRunner(FakeProcessRunner.Run(exitCode = 0, output = compliant))

        val result = ExternalPdfAValidator(VeraPdf(runner)).validate(pdf, PdfAProfile.PDFA_3U)

        runner.commands.single().windowed(2) shouldContain listOf("-f", "3u")
        result.describe() shouldBe "PDF/A-3u: gültig"
    }

    test("checkAvailable fragt nach verapdf") {
        val validator = ExternalPdfAValidator(VeraPdf(FakeProcessRunner(missing = setOf("verapdf"))))

        shouldThrow<ToolchainException> { validator.checkAvailable() }.message!! shouldContain "verapdf"
    }
})
