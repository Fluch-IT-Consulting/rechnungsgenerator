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
    fun archive(): Path =
        createTempDirectory("wurzel-test").also {
            it
                .resolve(Root.FOLDER)
                .createDirectories()
                .resolve(Root.SELLER_CONFIG)
                .writeText("firma: Muster\n")
        }

    /** Eine Rechnungsdatei unter [dir]; die Zwischenordner entstehen mit. */
    fun invoice(dir: Path, path: String): Path =
        dir.resolve(path).also {
            it.parent.createDirectories()
            it.writeText("nummer: R26-009\n")
        }

    test("die Stammdaten liegen im Ordner der Wurzel") {
        val archive = archive()

        Root.of(invoice(archive, "R26-009.yaml")).sellerConfig shouldBe
            archive.resolve(Root.FOLDER).resolve(Root.SELLER_CONFIG)
    }

    test("gesucht wird den Pfad hinauf, über beliebig viele Ebenen") {
        val archive = archive()

        Root.of(invoice(archive, "2026/Q3/R26-009.yaml")).directory shouldBe archive
    }

    test("gesucht wird ab der Rechnungsdatei, nicht ab dem Arbeitsverzeichnis") {
        // Ein relativer Pfad wird zuerst absolut gemacht. Das Arbeitsverzeichnis
        // des Testlaufs ist das Projekt und hat gar keine Wurzel – fände die
        // Suche von dort aus statt, käme hier ein Abbruch heraus.
        val archive = archive()
        val invoice = invoice(archive, "2026/R26-009.yaml")
        val relative = Path.of("").toAbsolutePath().relativize(invoice)

        Root.of(relative).directory shouldBe archive
    }

    test("die nächste Wurzel gewinnt") {
        // Ein zweiter Rechnungssteller ist eine zweite Wurzel, auch verschachtelt.
        val outer = archive()
        val inner = outer.resolve("zweitfirma").createDirectories()
        inner
            .resolve(Root.FOLDER)
            .createDirectories()
            .resolve(Root.SELLER_CONFIG)
            .writeText("firma: Zweit\n")

        Root.of(invoice(inner, "2026/R26-009.yaml")).directory shouldBe inner
    }

    test("ohne Wurzel nennt die Meldung den Ausgangspunkt, den Ordner und beide Auswege") {
        val withoutRoot = createTempDirectory("wurzel-test")
        val invoice = invoice(withoutRoot, "2026/R26-009.yaml")

        val failure = shouldThrow<InputFileException> { Root.of(invoice) }

        failure.message!! shouldContain invoice.parent.toString()
        failure.message!! shouldContain Root.FOLDER
        failure.message!! shouldContain Root.SELLER_CONFIG
        failure.message!! shouldContain "einrichten"
        failure.message!! shouldContain "-s"
    }

    test("eine lose stammdaten.yaml ist keine Wurzel") {
        // Sie kann als Kopie oder Sicherung irgendwo liegen; ein Ordner mit dem
        // Namen des Programms nicht.
        val withoutRoot = createTempDirectory("wurzel-test")
        withoutRoot.resolve(Root.SELLER_CONFIG).writeText("firma: Muster\n")

        shouldThrow<InputFileException> { Root.of(invoice(withoutRoot, "R26-009.yaml")) }
    }

    context("eine halb eingerichtete Wurzel bricht ab, statt weiter oben zu suchen") {
        // Sonst nähme sie still die Stammdaten der äußeren Wurzel, und auf der
        // Rechnung stünde der falsche Aussteller.
        test("ein Ordner ohne stammdaten.yaml") {
            val outer = archive()
            val inner = outer.resolve("zweitfirma").also { it.resolve(Root.FOLDER).createDirectories() }

            val failure = shouldThrow<InputFileException> { Root.of(invoice(inner, "R26-009.yaml")) }

            failure.message!! shouldContain inner.resolve(Root.FOLDER).toString()
            failure.message!! shouldContain Root.SELLER_CONFIG
        }

        test("eine Datei dieses Namens") {
            val outer = archive()
            val inner = outer.resolve("zweitfirma").createDirectories()
            inner.resolve(Root.FOLDER).writeText("kein Ordner\n")

            val failure = shouldThrow<InputFileException> { Root.of(invoice(inner, "R26-009.yaml")) }

            failure.message!! shouldContain inner.resolve(Root.FOLDER).toString()
            failure.message!! shouldContain "kein Ordner"
        }
    }

    context("mehrere Rechnungen") {
        test("dieselbe Wurzel aus verschiedenen Unterordnern") {
            val archive = archive()

            Root.of(listOf(invoice(archive, "2025/R25-100.yaml"), invoice(archive, "2026/R26-009.yaml"))).directory shouldBe archive
        }

        test("verschiedene Wurzeln brechen ab und nennen beide") {
            val one = archive()
            val other = archive()
            val here = invoice(one, "R26-009.yaml")
            val there = invoice(other, "R26-011.yaml")

            val failure = shouldThrow<InputFileException> { Root.of(listOf(here, there)) }

            failure.message!! shouldContain one.toString()
            failure.message!! shouldContain other.toString()
            failure.message!! shouldContain "R26-009.yaml"
            failure.message!! shouldContain "R26-011.yaml"
        }
    }
})
