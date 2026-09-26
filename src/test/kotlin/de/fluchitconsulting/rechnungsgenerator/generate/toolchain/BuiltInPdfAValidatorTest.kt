package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path

/**
 * Was der eingebaute Prüfer über ein nicht konformes PDF sagt – ohne Werkzeug.
 *
 * Er ist seit ADR-0014 die Vorgabe: Jeder Aufruf ohne `--externe-pdfa-pruefung`
 * geht hier durch. Geprüft wird dabei nicht das Urteil allein – das kommt
 * direkt aus `result.isCompliant` –, sondern die Abbildung der Verstöße:
 * Beschreibung und Meldung holt sie über `getRuleByRuleId` aus dem Profil, weil
 * die Aussage selbst keine trägt, und fängt ein fehlendes Ergebnis mit
 * `orEmpty()` ab. Fände die Regel sich nach einem Versionssprung nicht mehr,
 * bliebe das Urteil richtig und die Meldung würde still leer: "PDF/A-1b
 * verletzt:" und darunter nichts (#238).
 *
 * `nicht-konform.pdf` ist von Hand geschrieben, 595 Bytes, und verletzt sechs
 * Regeln auf einmal – fehlender binärer Kommentar im Kopf, fehlende
 * Trailer-ID, eine falsche `/Length` im Inhaltsstrom, nicht eingebettete
 * Schrift, fehlender OutputIntent, fehlende XMP-Metadaten. Der eingebaute
 * Prüfer läuft in derselben JVM: kein Werkzeug, kein Tag, kein Fake.
 *
 * Die `/Length` steht auf 52, der Strom ist 51 Bytes lang. Gezählt wird, was
 * zwischen dem Zeilenvorschub nach `stream` und dem Zeilenende vor `endstream`
 * steht – dieses Zeilenende gehört nicht dazu, und beim Schreiben von Hand ist
 * es mitgezählt worden. Gezeigt werden sollten fünf Dinge, und der Verzähler
 * bleibt trotzdem stehen (#238): Er ist ein Verstoß wie die anderen, beide
 * Prüfer melden ihn, und ein Test sagt zu, was der Prüfer sagt – nicht, was
 * die Datei sagen wollte. Nicht zu verwechseln mit
 * `nicht-konform-mit-warnung.pdf`: Dort steht 54, und erst diese Abweichung
 * bringt veraPDF dazu, das fehlende `endstream` zu protokollieren (#227).
 *
 * Der gültige Fall steht hier nicht: Er läuft in jedem Build ohnehin, sobald
 * eine Rechnung entsteht.
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

    test("die verletzten Regeln stehen genau so im Ergebnis") {
        val result = BuiltInPdfAValidator.validate(nonCompliant, PdfAProfile.PDFA_1B)

        // Die Kennung als eine Zeichenkette, wie beim Vergleich der beiden
        // Prüfer: So steht im Fehlerfall "6.3.4-1" im Bericht und nicht ein Paar.
        result.violations.map { "${it.clause}-${it.testNumber}" }.sorted() shouldBe
            listOf("6.1.2-2", "6.1.3-1", "6.1.7-1", "6.2.3.3-3", "6.3.4-1", "6.7.2-1")
    }

    /**
     * Der eigentliche Punkt: Zugesagt wird, dass Beschreibung und Meldung da
     * sind – nicht ihr Wortlaut. Den setzt veraPDF aus seinem Profil zusammen
     * und darf ihn ändern; dass die Regel überhaupt gefunden wird, darf es
     * nicht.
     */
    test("jeder Verstoß trägt Beschreibung und Meldung aus dem Profil") {
        val result = BuiltInPdfAValidator.validate(nonCompliant, PdfAProfile.PDFA_1B)

        result.violations.forEach { violation ->
            withClue("${violation.clause}-${violation.testNumber}") {
                violation.description.isNotBlank() shouldBe true
                violation.errorMessage.isNotBlank() shouldBe true
            }
        }
    }

    /**
     * Über `kein-pdf.pdf` kann veraPDF gar nicht urteilen: eine Textdatei mit
     * falscher Endung. Das ist kein Urteil "ungültig", sondern ein Werkzeug,
     * das nicht klarkam – derselbe Vertrag, den der externe Prüfer über seinen
     * Exitcode hält (#294). Zugesagt wird, dass die Meldung die Datei nennt
     * und den innersten Grund, den veraPDF in die Ursache legt – nicht der
     * Wortlaut von veraPDF: Verglichen wird mit dem, was die Ursache selbst
     * sagt.
     */
    test("über ein unlesbares PDF urteilt er nicht, sondern wirft ToolchainException") {
        val noPdf = Path.of("src/test/resources/kein-pdf.pdf")

        val error = shouldThrow<ToolchainException> { BuiltInPdfAValidator.validate(noPdf, PdfAProfile.PDFA_1B) }

        error.message!! shouldContain "kein-pdf.pdf"
        error.message!! shouldContain generateSequence(error.cause) { it.cause }.last().message!!
    }
})
