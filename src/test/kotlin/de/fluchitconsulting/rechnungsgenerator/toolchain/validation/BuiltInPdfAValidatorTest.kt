package de.fluchitconsulting.rechnungsgenerator.toolchain.validation

import de.fluchitconsulting.rechnungsgenerator.generate.typesetting.EInvoiceEmbedder
import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readBytes

/**
 * Geprüft wird die Abbildung der Verstöße: Fände `getRuleByRuleId` die Regel
 * nach einem Versionssprung nicht, bliebe die Meldung still leer (#238).
 *
 * `nicht-konform.pdf` ist von Hand geschrieben. Seine `/Length` steht auf 52 bei
 * 51 Bytes, ein Verzähler, der als sechster Verstoß bleibt (#238); in
 * `nicht-konform-mit-warnung.pdf` steht 54, damit veraPDF protokolliert (#227).
 */
class BuiltInPdfAValidatorTest : FunSpec({

    val nonCompliant = Path.of("src/test/resources/nicht-konform.pdf")

    test("ein nicht konformes PDF wird als solches gemeldet") {
        val result = BuiltInPdfAValidator.validate(nonCompliant, PdfAProfile.PDFA_1B)

        result.valid shouldBe false
        result.describe() shouldContain "PDF/A-1b verletzt"
    }

    test("gegen PDF/A-3u geprüft, nennt das Urteil das Profil und den Teil der Norm") {
        val result = BuiltInPdfAValidator.validate(nonCompliant, PdfAProfile.PDFA_3U)

        result.valid shouldBe false
        result.describe() shouldContain "PDF/A-3u verletzt"
        result.violations.map { it.specification }.toSet() shouldBe setOf("ISO 19005-3:2012")
    }

    // Hält fest, dass 3a und 3b eigene Profile laden und nicht still das von 3u.
    // 3a verlangt eine logische Struktur, die LuaLaTeX nicht setzt.
    test("die E-Rechnung aus erzeuge genügt PDF/A-3b, aber nicht PDF/A-3a") {
        val einvoice = createTempDirectory("builtin-validator-test").resolve("e-rechnung.pdf")
        EInvoiceEmbedder.embed(
            Path.of("src/test/resources/rechnung-a1b.pdf"),
            Path.of("src/test/resources/rechnung-erwartet.xml").readBytes(),
            einvoice,
        )

        val asB = BuiltInPdfAValidator.validate(einvoice, PdfAProfile.PDFA_3B)
        val asA = BuiltInPdfAValidator.validate(einvoice, PdfAProfile.PDFA_3A)

        withClue(asB.describe()) { asB.valid shouldBe true }
        asA.valid shouldBe false
        asA.describe() shouldContain "PDF/A-3a verletzt"
    }

    test("die verletzten Regeln stehen genau so im Ergebnis") {
        val result = BuiltInPdfAValidator.validate(nonCompliant, PdfAProfile.PDFA_1B)

        result.violations.map { "${it.clause}-${it.testNumber}" }.sorted() shouldBe
            listOf("6.1.2-2", "6.1.3-1", "6.1.7-1", "6.2.3.3-3", "6.3.4-1", "6.7.2-1")
    }

    // Zugesagt ist, dass sie da sind, nicht ihr Wortlaut.
    test("jeder Verstoß trägt Beschreibung und Meldung aus dem Profil") {
        val result = BuiltInPdfAValidator.validate(nonCompliant, PdfAProfile.PDFA_1B)

        result.violations.forEach { violation ->
            withClue("${violation.clause}-${violation.testNumber}") {
                violation.description.isNotBlank() shouldBe true
                violation.errorMessage.isNotBlank() shouldBe true
            }
        }
    }

    // #294. Verglichen wird mit der Ursache selbst, nicht mit dem Wortlaut von veraPDF.
    test("über ein unlesbares PDF urteilt er nicht, sondern wirft ToolchainException") {
        val noPdf = Path.of("src/test/resources/kein-pdf.pdf")

        val error = shouldThrow<ToolchainException> { BuiltInPdfAValidator.validate(noPdf, PdfAProfile.PDFA_1B) }

        error.message!! shouldContain "kein-pdf.pdf"
        error.message!! shouldContain generateSequence(error.cause) { it.cause }.last().message!!
    }
})
