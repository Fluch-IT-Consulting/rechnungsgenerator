package de.fluchitconsulting.rechnung

import de.fluchitconsulting.rechnung.toolchain.LatexCompiler
import de.fluchitconsulting.rechnung.toolchain.LuaLatexTypesetter
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
        LuaLatexTypesetter.checkAvailable()
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

    test("erzeugt aus einer Schlussrechnung ein gültiges PDF/A-1b") {
        val config = YamlLoader.parseSellerConfig(resource("stammdaten.yaml"))
        val invoice = YamlLoader.parseInvoice(resource("schlussrechnung.yaml"))

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
        // veraPDF unterscheidet über den Exitcode: 1 heißt "nicht konform" und
        // ist ein Prüfergebnis, kein Fehler unsererseits. Genau das prüft dieser
        // Test – wie der Bericht danach gelesen wird, steht im
        // WerkzeugausgabenTest und braucht kein Werkzeug.
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
    }

    test("ein LaTeX-Fehler kommt als ToolchainException an") {
        // Nur der Weg wird hier geprüft: dass ein gescheiterter Lauf zur
        // Ausnahme wird und die Meldung des Übersetzers mitbringt. Wie aus
        // tausend Zeilen Protokoll eine Handvoll wird, prüft der
        // WerkzeugausgabenTest an einem echten, gespeicherten Log.
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
    }
})
