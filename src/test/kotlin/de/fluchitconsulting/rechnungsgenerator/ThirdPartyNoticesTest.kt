package de.fluchitconsulting.rechnungsgenerator

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.Enabled
import io.kotest.matchers.collections.shouldBeEmpty
import java.io.File

/** Die Ressource, in die Gradle die Namen der ausgelieferten JARs schreibt. */
private const val SHIPPED_JARS = "/shipped-jars.txt"

/**
 * Nur Gradle erzeugt [SHIPPED_JARS] – es kennt als einziges den
 * runtimeClasspath. Wer die Tests im Editor startet, hat die Datei nicht; dann
 * überspringt sich der Test, statt grün oder rot zu melden. Übersprungen und
 * nicht bestanden, wie beim Altersnachweis der EU-Liste.
 */
private val mitListeAusDemBuild =
    if (ThirdPartyNoticesTest::class.java.getResource(SHIPPED_JARS) != null) {
        Enabled.enabled
    } else {
        Enabled.disabled("Die Liste der ausgelieferten JARs erzeugt nur Gradle (./gradlew test).")
    }

/**
 * THIRD-PARTY.md nennt die Lizenzen der mitgelieferten Bibliotheken. Sie ist von
 * Hand geschrieben, weil ein Generator die POMs liest und genau dort bei den
 * veraPDF-Paketen nichts steht (#268).
 *
 * Von Hand heißt aber nicht ungeprüft: Dieser Test hält die Liste gegen das,
 * was wirklich ausgeliefert wird. Er wird rot, sobald eine Abhängigkeit
 * dazukommt, verschwindet oder ihre Fassung wechselt – also genau dann, wenn
 * die Vermerke nachzusehen sind. Ein Dependabot-PR, der eine Fassung anhebt,
 * fällt damit hier auf und nicht erst beim nächsten Release.
 */
class ThirdPartyNoticesTest : FunSpec({

    test("THIRD-PARTY.md nennt genau die ausgelieferten Bibliotheken")
        .config(enabledOrReasonIf = { mitListeAusDemBuild }) {
            // Das eigene JAR gehört in keine Liste fremder Lizenzen – und es
            // steht auf beiden Seiten: ausgeliefert wird es, und die Einleitung
            // der Datei nennt es. Deshalb dieselbe Regel für beide Mengen.
            fun Sequence<String>.ohneEigenes() = filterNot { it.startsWith("rechnungsgenerator-") }.toSet()

            val shipped =
                checkNotNull(ThirdPartyNoticesTest::class.java.getResource(SHIPPED_JARS))
                    .readText()
                    .lineSequence()
                    .filter { it.isNotBlank() }
                    .ohneEigenes()

            val document = File("THIRD-PARTY.md").readText()
            val listed = Regex("`([^`]+\\.jar)`").findAll(document).map { it.groupValues[1] }.ohneEigenes()

            withClue(
                """

                THIRD-PARTY.md und die tatsächlich ausgelieferten JARs gehen auseinander.

                Das ist eine Frage und keine Aufgabe: Erst nachsehen, woher die Änderung
                kommt – ein neues oder angehobenes Paket bringt seine eigene Lizenz mit,
                und die gehört in die Datei, bevor das nächste Release hinausgeht.
                """.trimIndent(),
            ) {
                withClue("in THIRD-PARTY.md, aber nicht mehr ausgeliefert") {
                    (listed - shipped).sorted().shouldBeEmpty()
                }
                withClue("ausgeliefert, aber nicht in THIRD-PARTY.md") {
                    (shipped - listed).sorted().shouldBeEmpty()
                }
            }
        }
})
