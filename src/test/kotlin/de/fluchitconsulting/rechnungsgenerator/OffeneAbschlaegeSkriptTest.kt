package de.fluchitconsulting.rechnungsgenerator

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Prüft `scripts/offene-abschlaege.py` aus dem `rechnung`-Skill, indem es
 * wirklich läuft – wie `NaechsteNummerSkriptTest` das Skript daneben (#61).
 *
 * Das Skript trägt seit #66 eine Regel, die sonst niemand tragen kann: Ob eine
 * Rechnung storniert wurde, steht in einer *anderen* Datei, und der Generator
 * sieht beim Erzeugen nur eine (ADR-0001). Beide Richtungen kosten Geld –
 * ein stornierter Abschlag, der abgesetzt wird, senkt den Rechnungsbetrag zu
 * Unrecht; ein Abschlag, der wegen einer stornierten Schlussrechnung als
 * abgesetzt gilt, lässt den Kunden zweimal zahlen.
 */
class OffeneAbschlaegeSkriptTest : FunSpec({

    val skript = File(".claude/skills/rechnung/scripts/offene-abschlaege.py").absolutePath
    val kunde = "Beispiel Software GmbH"

    data class Lauf(val exitCode: Int, val ausgabe: String, val fehler: String)

    fun abschlagsrechnung(nummer: String, datum: String) =
        """
        nummer: $nummer
        art: abschlagsrechnung
        datum: $datum
        kunde:
          name: $kunde
        """.trimIndent()

    fun schlussrechnung(nummer: String, vararg abgesetzt: String) =
        """
        nummer: $nummer
        art: schlussrechnung
        datum: 2026-11-05
        kunde:
          name: $kunde
        abschlaege:
        """.trimIndent() +
            abgesetzt.joinToString("") {
                "\n  - rechnungsnummer: $it"
            }

    fun stornorechnung(nummer: String, aufgehoben: String) =
        """
        nummer: $nummer
        art: stornorechnung
        datum: 2026-11-10
        storniert:
          rechnungsnummer: $aufgehoben
          rechnungsdatum: 2026-06-15
        kunde:
          name: $kunde
        """.trimIndent()

    // Der Dateiname bestimmt die Reihenfolge, in der das Skript liest – deshalb
    // steht er in den Tests und wird nicht aus der Nummer abgeleitet.
    fun ablage(vararg dateien: Pair<String, String>): Path {
        val verzeichnis = createTempDirectory("offene-abschlaege-test")
        dateien.forEach { (name, inhalt) -> verzeichnis.resolve(name).writeText(inhalt) }
        return verzeichnis
    }

    fun lauf(verzeichnis: Path): Lauf {
        val ausgabe = verzeichnis.resolve("stdout.txt")
        val fehler = verzeichnis.resolve("stderr.txt")
        val prozess =
            ProcessBuilder("python3", skript, verzeichnis.toString(), kunde)
                .redirectOutput(ausgabe.toFile())
                .redirectError(fehler.toFile())
                .start()
        check(prozess.waitFor(30, TimeUnit.SECONDS)) { "Das Skript hat nach 30 s nicht geantwortet." }
        return Lauf(prozess.exitValue(), ausgabe.readText().trim(), fehler.readText().trim())
    }

    test("offen ist, was keine Schlussrechnung absetzt") {
        val verzeichnis =
            ablage(
                "r009.yaml" to abschlagsrechnung("R26-009", "2026-06-15"),
                "r011.yaml" to abschlagsrechnung("R26-011", "2026-07-15"),
                "r014.yaml" to schlussrechnung("R26-014", "R26-009"),
            )

        val ergebnis = lauf(verzeichnis)

        ergebnis.exitCode shouldBe 0
        ergebnis.ausgabe shouldContain "R26-011\t2026-07-15\t"
        ergebnis.ausgabe shouldNotContain "R26-009"
    }

    test("eine stornierte Abschlagsrechnung gilt nicht als offen") {
        // Fall 1 aus #66: Das Geld ist zurück, abzusetzen gibt es nichts mehr.
        val verzeichnis =
            ablage(
                "r009.yaml" to abschlagsrechnung("R26-009", "2026-06-15"),
                "r010.yaml" to stornorechnung("R26-010", "R26-009"),
            )

        val ergebnis = lauf(verzeichnis)

        ergebnis.ausgabe shouldBe ""
        ergebnis.fehler shouldContain "R26-009 ist durch R26-010 storniert"
        // Die Schlussmeldung nennt den Grund, den es hier wirklich gibt.
        ergebnis.fehler shouldContain "abgesetzt oder storniert"
    }

    test("eine stornierte Schlussrechnung gibt ihre Abschläge wieder frei") {
        // Fall 2 aus #66. Das Storno liegt hinter seiner Schlussrechnung –
        // deshalb entscheidet das Skript erst, wenn alle Dateien gelesen sind.
        val verzeichnis =
            ablage(
                "r009.yaml" to abschlagsrechnung("R26-009", "2026-06-15"),
                "r014.yaml" to schlussrechnung("R26-014", "R26-009"),
                "r015.yaml" to stornorechnung("R26-015", "R26-014"),
            )

        val ergebnis = lauf(verzeichnis)

        ergebnis.ausgabe shouldContain "R26-009\t2026-06-15\t"
        ergebnis.fehler shouldContain "R26-009 war in der stornierten Schlussrechnung R26-014 abgesetzt"
    }

    test("stand der Abschlag in zwei stornierten Schlussrechnungen, nennt der Vermerk beide") {
        // Sonst läse man aus der Meldung eine Geschichte, die zu kurz ist.
        val verzeichnis =
            ablage(
                "r009.yaml" to abschlagsrechnung("R26-009", "2026-06-15"),
                "r014.yaml" to schlussrechnung("R26-014", "R26-009"),
                "r015.yaml" to stornorechnung("R26-015", "R26-014"),
                "r016.yaml" to schlussrechnung("R26-016", "R26-009"),
                "r017.yaml" to stornorechnung("R26-017", "R26-016"),
            )

        val ergebnis = lauf(verzeichnis)

        ergebnis.ausgabe shouldContain "R26-009\t2026-06-15\t"
        ergebnis.fehler shouldContain "in den stornierten Schlussrechnungen R26-014, R26-016 abgesetzt"
    }

    test("eine gültige Schlussrechnung genügt, damit der Abschlag abgesetzt bleibt") {
        // Nur eine der beiden ist storniert – die andere setzt weiter ab.
        val verzeichnis =
            ablage(
                "r009.yaml" to abschlagsrechnung("R26-009", "2026-06-15"),
                "r014.yaml" to schlussrechnung("R26-014", "R26-009"),
                "r015.yaml" to stornorechnung("R26-015", "R26-014"),
                "r016.yaml" to schlussrechnung("R26-016", "R26-009"),
            )

        val ergebnis = lauf(verzeichnis)

        ergebnis.ausgabe shouldBe ""
        // Und es ist auch keine doppelte Absetzung: Die stornierte setzt nichts ab.
        ergebnis.fehler shouldNotContain "Achtung"
    }

    test("zwei gültige Schlussrechnungen über denselben Abschlag werden gemeldet") {
        // #67: Der Generator lehnt dieselbe Nummer zweimal ab, aber nur
        // innerhalb einer Datei. Hier zahlte der Kunde einmal zu wenig.
        val verzeichnis =
            ablage(
                "r009.yaml" to abschlagsrechnung("R26-009", "2026-06-15"),
                "r014.yaml" to schlussrechnung("R26-014", "R26-009"),
                "r018.yaml" to schlussrechnung("R26-018", "R26-009"),
            )

        val ergebnis = lauf(verzeichnis)

        ergebnis.ausgabe shouldBe ""
        ergebnis.fehler shouldContain
            "Achtung: R26-009 wird mehrfach abgesetzt – in den Schlussrechnungen R26-014, R26-018."
    }

    test("ein stornierter Abschlag in einer gültigen Schlussrechnung wird gemeldet") {
        // Weder offen noch in Ordnung: Die Schlussrechnung setzt Geld ab, das
        // der Kunde zurückbekommen hat.
        val verzeichnis =
            ablage(
                "r009.yaml" to abschlagsrechnung("R26-009", "2026-06-15"),
                "r010.yaml" to stornorechnung("R26-010", "R26-009"),
                "r014.yaml" to schlussrechnung("R26-014", "R26-009"),
            )

        val ergebnis = lauf(verzeichnis)

        ergebnis.ausgabe shouldBe ""
        ergebnis.fehler shouldContain "Achtung: R26-009 ist durch R26-010 storniert, wird aber in der Schlussrechnung R26-014 abgesetzt."
    }

    test("ohne Storno bleibt die Schlussmeldung, wie sie war") {
        val verzeichnis =
            ablage(
                "r009.yaml" to abschlagsrechnung("R26-009", "2026-06-15"),
                "r014.yaml" to schlussrechnung("R26-014", "R26-009"),
            )

        val ergebnis = lauf(verzeichnis)

        ergebnis.ausgabe shouldBe ""
        ergebnis.fehler shouldContain "Alle 1 Abschlagsrechnungen an '$kunde' sind schon abgesetzt."
    }
})
