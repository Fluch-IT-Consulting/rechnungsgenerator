package de.fluchitconsulting.rechnungsgenerator

import de.fluchitconsulting.rechnungsgenerator.core.model.Root
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Prüft `scripts/naechste-nummer.py` aus dem `rechnung`-Skill, indem es
 * wirklich läuft (#61).
 *
 * Warum ein Kotlin-Test für ein Python-Skript: Das Skript trägt seit #61 eine
 * Regel – es prüft die Nummer, die es vergeben will, gegen das
 * `rechnungsnummer-muster` der Stammdaten (ADR-0005). Eine Regel ohne Test ist
 * eine Zusage, die nur so lange gilt, wie sich jemand erinnert. Der Build
 * verlangt damit `python3` und PyYAML – dieselbe Art Voraussetzung wie
 * LuaLaTeX und veraPDF, und `offene-abschlaege.py` braucht PyYAML ohnehin.
 *
 * Die Ausgabe wird in Dateien umgeleitet statt aus Strömen gelesen, wie bei
 * jedem fremden Prozess in diesem Projekt: Wer liest, hängt mit, wenn der
 * Prozess hängt, und die Zeitgrenze käme nie zum Zug.
 */
class NaechsteNummerSkriptTest : FunSpec({

    val skript = File(".claude/skills/rechnung/scripts/naechste-nummer.py").absolutePath

    // Die Beispiel-Stammdaten des Projekts tragen das Muster R\d{2}-\d{3}.
    val stammdaten = File("src/test/resources/stammdaten.yaml").readText()

    data class Lauf(val exitCode: Int, val ausgabe: String, val fehler: String)

    fun ablage(vararg dateinamen: String): Path {
        val verzeichnis = createTempDirectory("nummern-test")
        val outgoing = verzeichnis.resolve("Outgoing").also { it.createDirectories() }
        dateinamen.forEach { outgoing.resolve(it).writeText("") }
        return verzeichnis
    }

    fun lauf(verzeichnis: Path, vararg args: String): Lauf {
        val ausgabe = verzeichnis.resolve("stdout.txt")
        val fehler = verzeichnis.resolve("stderr.txt")
        val prozess =
            ProcessBuilder(listOf("python3", skript, verzeichnis.resolve("Outgoing").toString()) + args)
                .redirectOutput(ausgabe.toFile())
                .redirectError(fehler.toFile())
                .start()
        check(prozess.waitFor(30, TimeUnit.SECONDS)) { "Das Skript hat nach 30 s nicht geantwortet." }
        return Lauf(prozess.exitValue(), ausgabe.readText().trim(), fehler.readText().trim())
    }

    test("die nächste Nummer zählt die belegte weiter") {
        val verzeichnis = ablage("Rechnung_R26-011-beispiel.pdf", "Rechnung_R26-009-beispiel.yaml")

        lauf(verzeichnis, "--rechnungsdatum", "2026-08-14").ausgabe shouldBe "R26-012"
    }

    test("eine anders benannte YAML-Datei zählt über ihre Nummer") {
        // #75: Wer ohne den Skill erzeugt, hält sich nicht an dessen
        // Namensregel. Die Nummer ist trotzdem vergeben. Eine YAML-Datei ohne
        // Nummer – hier eine Liste wie vereinnahmungen.yaml – zählt nicht.
        val verzeichnis = ablage("Rechnung_R26-011-beispiel.pdf")
        val outgoing = verzeichnis.resolve("Outgoing")
        outgoing.resolve("Abschlagsrechnung-R26-014.yaml").writeText("nummer: R26-014\nart: abschlagsrechnung\n")
        outgoing.resolve("vereinnahmungen.yaml").writeText("- R26-099\n")

        lauf(verzeichnis, "--rechnungsdatum", "2026-08-14").ausgabe shouldBe "R26-015"
    }

    test("eine unlesbare YAML-Datei wird genannt, nicht still übergangen") {
        val verzeichnis = ablage("Rechnung_R26-011-beispiel.pdf")
        verzeichnis.resolve("Outgoing").resolve("kaputt.yaml").writeText("nummer: [R26-020\n")

        val ergebnis = lauf(verzeichnis, "--rechnungsdatum", "2026-08-14")

        ergebnis.exitCode shouldBe 0
        ergebnis.ausgabe shouldBe "R26-012"
        ergebnis.fehler shouldContain "kaputt.yaml"
    }

    test("ein Jahr ohne Rechnung fängt bei 001 an") {
        // Nummern aus anderen Jahrgängen zählen nicht mit, auch wenn sie
        // danebenliegen.
        val verzeichnis = ablage("Rechnung_R25-137-beispiel.pdf")

        lauf(verzeichnis, "--rechnungsdatum", "2026-01-02").ausgabe shouldBe "R26-001"
    }

    test("das Jahr kommt aus dem Rechnungsdatum, nicht aus dem Kalender") {
        // Die Silvesterrechnung: datiert auf den 31.12., geschrieben am 2.1.
        // Sie gehört in den Nummernkreis des alten Jahres.
        val verzeichnis = ablage("Rechnung_R25-003-beispiel.pdf", "Rechnung_R26-001-beispiel.pdf")

        lauf(verzeichnis, "--rechnungsdatum", "2025-12-31").ausgabe shouldBe "R25-004"
    }

    test("ohne Rechnungsdatum gilt heute") {
        val jahr = "%02d".format(LocalDate.now().year % 100)

        lauf(ablage()).ausgabe shouldBe "R$jahr-001"
    }

    test("mit Stammdaten wird die Nummer gegen ihr Muster geprüft") {
        val verzeichnis = ablage("Rechnung_R26-011-beispiel.pdf")
        verzeichnis.resolve("stammdaten.yaml").writeText(stammdaten)

        val ergebnis =
            lauf(verzeichnis, "--rechnungsdatum", "2026-08-14", "--stammdaten", verzeichnis.resolve("stammdaten.yaml").toString())

        ergebnis.exitCode shouldBe 0
        ergebnis.ausgabe shouldBe "R26-012"
    }

    test("ein gewechseltes Muster hält die Vergabe an") {
        // Der Fall, für den die Prüfung da ist: Die Stammdaten tragen ein neues
        // Muster, das Skript vergibt noch im alten Schema. Gemeldet wird das
        // hier – nicht erst, wenn die Nummer schon in Dateinamen steht.
        val verzeichnis = ablage("Rechnung_R26-011-beispiel.pdf")
        verzeichnis
            .resolve("stammdaten.yaml")
            .writeText(stammdaten.replace("""rechnungsnummer-muster: 'R\d{2}-\d{3}'""", """rechnungsnummer-muster: 'RE-\d{4}'"""))

        val ergebnis = lauf(verzeichnis, "--stammdaten", verzeichnis.resolve("stammdaten.yaml").toString())

        ergebnis.exitCode shouldBe 1
        ergebnis.ausgabe shouldBe ""
        ergebnis.fehler shouldContain "RE-\\d{4}"
        ergebnis.fehler shouldContain "muss es hier mitwechseln"
    }

    test("Stammdaten ohne Muster prüfen nicht") {
        // Dieselbe Regel wie im Generator: ohne Eintrag keine Prüfung.
        val verzeichnis = ablage("Rechnung_R26-011-beispiel.pdf")
        verzeichnis
            .resolve("stammdaten.yaml")
            .writeText(stammdaten.lines().filterNot { it.startsWith("rechnungsnummer-muster") }.joinToString("\n"))

        val ergebnis =
            lauf(verzeichnis, "--rechnungsdatum", "2026-08-14", "--stammdaten", verzeichnis.resolve("stammdaten.yaml").toString())

        ergebnis.exitCode shouldBe 0
        ergebnis.ausgabe shouldBe "R26-012"
    }

    test("ein Datum, das keines ist, wird abgelehnt") {
        val ergebnis = lauf(ablage(), "--rechnungsdatum", "31.12.2025")

        ergebnis.exitCode shouldBe 2
        ergebnis.fehler shouldContain "--rechnungsdatum"
    }

    test("die Stammdaten im Ordner der Wurzel sind keine vergebene Nummer") {
        // rglob steigt auch in versteckte Ordner, und seit ADR-0015 liegen die
        // Stammdaten in einem. Sie haben kein Feld `nummer` und fallen deshalb
        // durch – still, denn eine Datei ohne Nummer ist keine Rechnung.
        val verzeichnis = ablage("Rechnung_R26-009-beispiel.yaml")
        verzeichnis
            .resolve("Outgoing")
            .resolve(Root.FOLDER)
            .createDirectories()
            .resolve(Root.SELLER_CONFIG)
            .writeText(stammdaten)

        val ergebnis = lauf(verzeichnis, "--rechnungsdatum", "2026-08-14")

        ergebnis.exitCode shouldBe 0
        ergebnis.ausgabe shouldBe "R26-010"
        ergebnis.fehler shouldBe ""
    }
})
