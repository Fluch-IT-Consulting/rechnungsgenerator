package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

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

    val nichtKonform = Path.of("src/test/resources/nicht-konform.pdf")

    test("ein nicht konformes PDF wird als solches gemeldet") {
        val ergebnis = BuiltInPdfAValidator.validate(nichtKonform)

        ergebnis.valid shouldBe false
        ergebnis.describe() shouldContain "PDF/A-1b verletzt"
    }

    test("die verletzten Regeln stehen genau so im Ergebnis") {
        val ergebnis = BuiltInPdfAValidator.validate(nichtKonform)

        // Die Kennung als eine Zeichenkette, wie beim Vergleich der beiden
        // Prüfer: So steht im Fehlerfall "6.3.4-1" im Bericht und nicht ein Paar.
        ergebnis.violations.map { "${it.clause}-${it.testNumber}" }.sorted() shouldBe
            listOf("6.1.2-2", "6.1.3-1", "6.1.7-1", "6.2.3.3-3", "6.3.4-1", "6.7.2-1")
    }

    /**
     * Der eigentliche Punkt: Zugesagt wird, dass Beschreibung und Meldung da
     * sind – nicht ihr Wortlaut. Den setzt veraPDF aus seinem Profil zusammen
     * und darf ihn ändern; dass die Regel überhaupt gefunden wird, darf es
     * nicht.
     */
    test("jeder Verstoß trägt Beschreibung und Meldung aus dem Profil") {
        val ergebnis = BuiltInPdfAValidator.validate(nichtKonform)

        ergebnis.violations.forEach { verstoss ->
            withClue("${verstoss.clause}-${verstoss.testNumber}") {
                verstoss.description.isNotBlank() shouldBe true
                verstoss.errorMessage.isNotBlank() shouldBe true
            }
        }
    }
})
