package de.fluchitconsulting.rechnung.toolchain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

/**
 * Prüft das Auswerten der Werkzeugausgaben – ohne LuaLaTeX, ohne veraPDF.
 *
 * Die Eingaben sind echte Ausgaben, mitgeschnitten von einem Lauf auf diesem
 * Rechner und in `src/test/resources/werkzeugausgaben/` abgelegt. Erfundene
 * Logs würden gerade die Eigenheiten verfehlen, um die es hier geht: das
 * Paketgeplauder vor den Fehlerzeilen, die JVM-Warnungen vor dem XML.
 *
 * Verändert wurde an ihnen nur eines: Pfade mit dem Benutzernamen des Autors
 * sind durch neutrale ersetzt – dieses Repository kann öffentlich werden.
 */
class WerkzeugausgabenTest : FunSpec({

    fun ausgabe(name: String) = File("src/test/resources/werkzeugausgaben/$name").readText()

    context("LatexLog: verlangt LaTeX einen weiteren Lauf?") {
        test("ja, wenn es das schreibt") {
            // "LaTeX Warning: Label(s) may have changed. Rerun to get
            // cross-references right." – aus einem Lauf über ein Dokument mit
            // Querverweisen.
            LatexLog.needsAnotherPass(ausgabe("lualatex-verlangt-zweiten-lauf.log")) shouldBe true
        }

        test("nein, wenn nichts dergleichen dasteht") {
            LatexLog.needsAnotherPass(ausgabe("lualatex-abbruch.log")) shouldBe false
        }
    }

    context("LatexLog: der Fehlerbericht") {
        test("nennt die Fundstelle statt tausend Zeilen Paketgeplauder") {
            val bericht = LatexLog.errorReport("rechnung", ausgabe("lualatex-abbruch.log"))

            bericht shouldContain "Undefined control sequence"
            bericht shouldContain "gibtesnicht"
            // Die Fundstelle kommt mit – dafür stehen die zwei Folgezeilen drin.
            bericht shouldContain "l.3"
            // Das Geplauder bleibt draußen: ein Paketname, der im Log steht,
            // aber nicht zu einer Fehlerzeile gehört.
            bericht shouldNotContain "geometry"
            // Aus 98 Zeilen Protokoll werden eine Handvoll. Die genaue Zahl ist
            // nicht der Punkt, die Größenordnung schon.
            bericht.lines().size shouldBeLessThan 15
        }

        test("bei zwei dicht beieinanderliegenden Fehlern steht keine Zeile doppelt") {
            // Der Fall, den distinct() abfängt: Die Dreierblöcke zweier Fehler
            // überlappen sich, wenn sie nah beieinander stehen.
            val bericht = LatexLog.errorReport("rechnung", ausgabe("lualatex-zwei-fehler.log"))

            val zeilen = bericht.lines().filter { it.isNotBlank() }
            zeilen.distinct() shouldHaveSize zeilen.size
            bericht shouldContain "gibtesnicht"
            bericht shouldContain "auchnicht"
        }

        test("ohne Fehlerzeile bleibt der Schluss des Protokolls übrig") {
            // Besser als eine leere Meldung: Irgendetwas ist ja schiefgegangen.
            val bericht = LatexLog.errorReport("rechnung", "Zeile eins\nZeile zwei\n")

            bericht shouldContain "Zeile zwei"
        }
    }

    context("VeraPdfReport") {
        test("schneidet die JVM-Warnungen vor dem XML weg") {
            val roh = ausgabe("verapdf-nicht-konform.txt")
            roh shouldContain "WARNING"

            val xml = VeraPdfReport.extractXml(roh)

            xml.startsWith("<?xml") shouldBe true
        }

        test("ohne XML im Text bricht es mit einer Meldung ab, die den Anfang zeigt") {
            val fehler = shouldThrow<ToolchainException> { VeraPdfReport.extractXml("verapdf: command not found") }

            fehler.message!! shouldContain "command not found"
        }

        test("liest die verletzten Regeln, nicht die bestandenen") {
            val bericht = ausgabe("verapdf-nicht-konform.txt")

            val verstoesse = VeraPdfReport.parseViolations(VeraPdfReport.extractXml(bericht))

            // Der Bericht führt bestandene und verletzte Regeln; nur die
            // verletzten interessieren.
            verstoesse shouldHaveSize 7
            verstoesse.first().clause shouldContain "6."
            verstoesse.first().description.isNotBlank() shouldBe true
        }

        test("ein Verstoß beschreibt sich selbst") {
            val verstoss = VeraPdfReport.parseViolations(VeraPdfReport.extractXml(ausgabe("verapdf-nicht-konform.txt"))).first()

            verstoss.toString() shouldContain "ISO 19005-1"
        }
    }
})
