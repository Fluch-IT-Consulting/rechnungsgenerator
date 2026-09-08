package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.toolchain.LatexCompiler
import de.fluchitconsulting.rechnung.toolchain.PdfAValidator
import de.fluchitconsulting.rechnung.toolchain.ToolchainException
import de.fluchitconsulting.rechnung.values.InvoiceType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.fileSize

/**
 * Der Integrationstest, der die Zusage des Projekts einlöst: Ohne gültiges
 * PDF/A gibt es keinen grünen Build. Er ruft LuaLaTeX und veraPDF wirklich auf –
 * fehlen sie, schlägt er fehl, statt sich stillschweigend zu überspringen.
 */
class ToolchainIntegrationTest : FunSpec({

    val workDir = createTempDirectory("rechnungsgenerator-test")

    fun resource(name: String) = File("src/test/resources/$name").readText()

    test("die benötigten Werkzeuge sind vorhanden") {
        InvoiceGenerator.checkTools()
    }

    test("erzeugt aus den Beispieldaten ein gültiges PDF/A-1b") {
        val config = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))
        val invoice = YamlLoader.parseInvoice(resource("rechnung.yaml"))

        val result = InvoiceGenerator.generate(invoice, config, workDir)

        result.pdf.exists() shouldBe true
        result.pdf.fileSize() shouldBe result.pdf.fileSize().coerceAtLeast(1000)
        // withClue hängt die veraPDF-Meldung an die Fehlermeldung – sonst stünde
        // im Testbericht nur "expected true but was false".
        withClue(result.validation.describe()) {
            result.validation.valid shouldBe true
        }
    }

    test("erzeugt aus einer Abschlagsrechnung ein gültiges PDF/A-1b") {
        val config = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))
        val invoice = YamlLoader.parseInvoice(resource("abschlagsrechnung.yaml"))

        val result = InvoiceGenerator.generate(invoice, config, workDir)

        withClue(result.validation.describe()) {
            result.validation.valid shouldBe true
        }
    }

    test("der Dateiname nennt Rechnungsart und -nummer") {
        InvoiceGenerator.jobNameFor(testInvoice()) shouldBe "Rechnung-R26-001"
        InvoiceGenerator.jobNameFor(testInvoice(type = InvoiceType.PREPAYMENT)) shouldBe "Abschlagsrechnung-R26-001"
    }

    test("ein nicht konformes PDF wird als solches gemeldet, nicht als Fehler") {
        // Ein ganz gewöhnliches PDF ohne pdfx – gültiges PDF, aber kein PDF/A.
        val plain =
            LatexCompiler.compile(
                workingDir = workDir,
                jobName = "ohne-pdfa",
                texSource = "\\documentclass{article}\\begin{document}Kein PDF/A.\\end{document}",
                xmpMetadata = "",
            )

        val validation = PdfAValidator.validate(plain)

        validation.valid shouldBe false
        validation.violations.shouldNotBeEmpty()
        validation.describe() shouldContain "ISO 19005-1"
    }

    test("ein LaTeX-Fehler nennt die Fundstelle statt tausend Zeilen Log") {
        val fehler =
            shouldThrow<ToolchainException> {
                LatexCompiler.compile(
                    workingDir = workDir,
                    jobName = "kaputt",
                    texSource = "\\documentclass{article}\\begin{document}\\gibtesnicht\\end{document}",
                    xmpMetadata = "",
                )
            }

        fehler.message!! shouldContain "gibtesnicht"
        fehler.message!!.lines().size shouldBe
            fehler.message!!
                .lines()
                .size
                .coerceAtMost(20)
    }
})
