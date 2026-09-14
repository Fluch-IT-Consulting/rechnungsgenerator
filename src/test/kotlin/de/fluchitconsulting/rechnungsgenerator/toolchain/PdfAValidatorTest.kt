package de.fluchitconsulting.rechnungsgenerator.toolchain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Wie das Prüfergebnis von veraPDF gelesen wird – ohne veraPDF.
 *
 * `verapdf-nicht-konform.txt` ist ein echter Mitschnitt und bringt zwei
 * Eigenheiten mit, die man sich nicht ausdenkt: die JVM-Warnungen, die vor dem
 * XML stehen, und einen Bericht, der bestandene *und* verletzte Regeln führt.
 */
class PdfAValidatorTest : FunSpec({

    fun mitschnitt(name: String) = File("src/test/resources/werkzeugausgaben/$name").readText()

    val pdf = createTempDirectory("pdfavalidator-test").resolve("rechnung.pdf")

    fun pruefen(exitCode: Int, output: String) =
        PdfAValidator(FakeProcessRunner(FakeProcessRunner.Run(exitCode, output))).validate(pdf)

    // Der einzige erfundene Bericht hier: Für ein konformes PDF gibt es keinen
    // Mitschnitt, weil dieser Fall im Integrationstest ohnehin echt vorkommt.
    val konform =
        """<?xml version="1.0" encoding="utf-8"?>
        |<report><jobs><job><validationReport><details>
        |<rule status="passed" clause="6.1.2" testNumber="1">
        |<description>Datei beginnt mit einem Header</description></rule>
        |</details></validationReport></job></jobs></report>
        """.trimMargin()

    test("Exitcode 0 heißt konform") {
        val ergebnis = pruefen(exitCode = 0, output = konform)

        ergebnis.valid shouldBe true
        ergebnis.violations.shouldBeEmpty()
        ergebnis.describe() shouldContain "gültig"
    }

    test("Exitcode 1 ist ein Prüfergebnis, kein Fehler") {
        // veraPDF unterscheidet über den Exitcode: 1 heißt "nicht konform".
        // Das ist eine Antwort auf unsere Frage und kein Absturz.
        val ergebnis = pruefen(exitCode = 1, output = mitschnitt("verapdf-nicht-konform.txt"))

        ergebnis.valid shouldBe false
        // Der Bericht führt bestandene und verletzte Regeln; nur die verletzten
        // interessieren.
        ergebnis.violations shouldHaveSize 7
        ergebnis.violations.first().clause shouldContain "6."
        ergebnis.violations
            .first()
            .description
            .isNotBlank() shouldBe true
    }

    test("die JVM-Warnungen vor dem XML stören die Auswertung nicht") {
        // veraPDF ist selbst eine Java-Anwendung und schreibt Warnungen auf
        // stderr; der Aufruf führt stderr und stdout zusammen, also stehen sie
        // vor dem Bericht. Ein Parser, der von vorne liest, bricht daran ab.
        val roh = mitschnitt("verapdf-nicht-konform.txt")
        roh.substringBefore("<?xml") shouldContain "WARNING"

        pruefen(exitCode = 1, output = roh).violations shouldHaveSize 7
    }

    test("ohne XML in der Ausgabe sagt die Meldung, was stattdessen kam") {
        val fehler = shouldThrow<ToolchainException> { pruefen(exitCode = 1, output = "verapdf: command not found") }

        fehler.message!! shouldContain "command not found"
    }

    test("jeder andere Exitcode ist ein Abbruch des Werkzeugs") {
        // Alles außer 0 und 1 heißt: veraPDF selbst kam nicht klar, etwa weil
        // die Datei nicht lesbar war. Dann gibt es kein Prüfergebnis.
        val fehler = shouldThrow<ToolchainException> { pruefen(exitCode = 3, output = "Can't read file") }

        fehler.message!! shouldContain "Exitcode 3"
        fehler.message!! shouldContain "Can't read file"
    }

    test("ein Verstoß beschreibt sich selbst") {
        val verstoss = pruefen(exitCode = 1, output = mitschnitt("verapdf-nicht-konform.txt")).violations.first()

        verstoss.toString() shouldContain "ISO 19005-1"
    }
})
