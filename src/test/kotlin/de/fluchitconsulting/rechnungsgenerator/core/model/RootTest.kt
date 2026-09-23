package de.fluchitconsulting.rechnungsgenerator.core.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

/**
 * Die Suche nach der Wurzel (ADR-0015), an echten Verzeichnissen geprüft –
 * sie rechnet Pfade und fragt `exists`, und dafür braucht es keine Naht.
 *
 * Jeder Test legt sich seine Ablage selbst an: Eine geteilte hätte für jeden
 * Fall eine andere Form, und die halb eingerichtete Wurzel des einen wäre die
 * Falle des nächsten.
 */
class RootTest : FunSpec({

    /** Eine Ablage mit Wurzel: `.rechnungsgenerator/stammdaten.yaml` und ein Jahresordner. */
    fun ablage(): Path =
        createTempDirectory("wurzel-test").also {
            it
                .resolve(Root.FOLDER)
                .createDirectories()
                .resolve(Root.SELLER_CONFIG)
                .writeText("firma: Muster\n")
        }

    /** Eine Rechnungsdatei unter [verzeichnis]; die Zwischenordner entstehen mit. */
    fun rechnung(verzeichnis: Path, pfad: String): Path =
        verzeichnis.resolve(pfad).also {
            it.parent.createDirectories()
            it.writeText("nummer: R26-009\n")
        }

    test("die Stammdaten liegen im Ordner der Wurzel") {
        val ablage = ablage()

        Root.of(rechnung(ablage, "R26-009.yaml")).sellerConfig shouldBe
            ablage.resolve(Root.FOLDER).resolve(Root.SELLER_CONFIG)
    }

    test("gesucht wird den Pfad hinauf, über beliebig viele Ebenen") {
        val ablage = ablage()

        Root.of(rechnung(ablage, "2026/Q3/R26-009.yaml")).directory shouldBe ablage
    }

    test("gesucht wird ab der Rechnungsdatei, nicht ab dem Arbeitsverzeichnis") {
        // Ein relativer Pfad wird zuerst absolut gemacht. Das Arbeitsverzeichnis
        // des Testlaufs ist das Projekt und hat gar keine Wurzel – fände die
        // Suche von dort aus statt, käme hier ein Abbruch heraus.
        val ablage = ablage()
        val rechnung = rechnung(ablage, "2026/R26-009.yaml")
        val relativ = Path.of("").toAbsolutePath().relativize(rechnung)

        Root.of(relativ).directory shouldBe ablage
    }

    test("die nächste Wurzel gewinnt") {
        // Ein zweiter Rechnungssteller ist eine zweite Wurzel, auch verschachtelt.
        val aussen = ablage()
        val innen = aussen.resolve("zweitfirma").createDirectories()
        innen
            .resolve(Root.FOLDER)
            .createDirectories()
            .resolve(Root.SELLER_CONFIG)
            .writeText("firma: Zweit\n")

        Root.of(rechnung(innen, "2026/R26-009.yaml")).directory shouldBe innen
    }

    test("ohne Wurzel nennt die Meldung den Ausgangspunkt, den Ordner und beide Auswege") {
        val ohneWurzel = createTempDirectory("wurzel-test")
        val rechnung = rechnung(ohneWurzel, "2026/R26-009.yaml")

        val failure = shouldThrow<InputFileException> { Root.of(rechnung) }

        failure.message!! shouldContain rechnung.parent.toString()
        failure.message!! shouldContain Root.FOLDER
        failure.message!! shouldContain Root.SELLER_CONFIG
        failure.message!! shouldContain "einrichten"
        failure.message!! shouldContain "-s"
    }

    test("eine lose stammdaten.yaml ist keine Wurzel") {
        // Sie kann als Kopie oder Sicherung irgendwo liegen; ein Ordner mit dem
        // Namen des Programms nicht.
        val ohneWurzel = createTempDirectory("wurzel-test")
        ohneWurzel.resolve(Root.SELLER_CONFIG).writeText("firma: Muster\n")

        shouldThrow<InputFileException> { Root.of(rechnung(ohneWurzel, "R26-009.yaml")) }
    }

    context("eine halb eingerichtete Wurzel bricht ab, statt weiter oben zu suchen") {
        // Sonst nähme sie still die Stammdaten der äußeren Wurzel, und auf der
        // Rechnung stünde der falsche Aussteller.
        test("ein Ordner ohne stammdaten.yaml") {
            val aussen = ablage()
            val innen = aussen.resolve("zweitfirma").also { it.resolve(Root.FOLDER).createDirectories() }

            val failure = shouldThrow<InputFileException> { Root.of(rechnung(innen, "R26-009.yaml")) }

            failure.message!! shouldContain innen.resolve(Root.FOLDER).toString()
            failure.message!! shouldContain Root.SELLER_CONFIG
        }

        test("eine Datei dieses Namens") {
            val aussen = ablage()
            val innen = aussen.resolve("zweitfirma").createDirectories()
            innen.resolve(Root.FOLDER).writeText("kein Ordner\n")

            val failure = shouldThrow<InputFileException> { Root.of(rechnung(innen, "R26-009.yaml")) }

            failure.message!! shouldContain innen.resolve(Root.FOLDER).toString()
            failure.message!! shouldContain "kein Ordner"
        }
    }

    context("mehrere Rechnungen") {
        test("dieselbe Wurzel aus verschiedenen Unterordnern") {
            val ablage = ablage()

            Root.of(listOf(rechnung(ablage, "2025/R25-100.yaml"), rechnung(ablage, "2026/R26-009.yaml"))).directory shouldBe ablage
        }

        test("verschiedene Wurzeln brechen ab und nennen beide") {
            val eine = ablage()
            val andere = ablage()
            val hier = rechnung(eine, "R26-009.yaml")
            val dort = rechnung(andere, "R26-011.yaml")

            val failure = shouldThrow<InputFileException> { Root.of(listOf(hier, dort)) }

            failure.message!! shouldContain eine.toString()
            failure.message!! shouldContain andere.toString()
            failure.message!! shouldContain "R26-009.yaml"
            failure.message!! shouldContain "R26-011.yaml"
        }
    }
})
