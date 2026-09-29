package de.fluchitconsulting.rechnungsgenerator

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.Enabled
import io.kotest.matchers.collections.shouldBeEmpty
import java.io.File

private const val SHIPPED_JARS = "/shipped-jars.txt"

// Übersprungen, nicht bestanden: Nur Gradle erzeugt die Datei, der Editor nicht.
private val withListFromBuild =
    if (ThirdPartyNoticesTest::class.java.getResource(SHIPPED_JARS) != null) {
        Enabled.enabled
    } else {
        Enabled.disabled("Die Liste der ausgelieferten JARs erzeugt nur Gradle (./gradlew test).")
    }

/** THIRD-PARTY.md ist von Hand geschrieben (#268); wird der Test rot, sind die Vermerke nachzusehen. */
class ThirdPartyNoticesTest : FunSpec({

    test("THIRD-PARTY.md nennt genau die ausgelieferten Bibliotheken")
        .config(enabledOrReasonIf = { withListFromBuild }) {
            // Das eigene JAR steht auf beiden Seiten, deshalb für beide Mengen.
            fun Sequence<String>.withoutOwn() = filterNot { it.startsWith("rechnungsgenerator-") }.toSet()

            val shipped =
                checkNotNull(ThirdPartyNoticesTest::class.java.getResource(SHIPPED_JARS))
                    .readText()
                    .lineSequence()
                    .filter { it.isNotBlank() }
                    .withoutOwn()

            val document = File("THIRD-PARTY.md").readText()
            val listed = Regex("`([^`]+\\.jar)`").findAll(document).map { it.groupValues[1] }.withoutOwn()

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
