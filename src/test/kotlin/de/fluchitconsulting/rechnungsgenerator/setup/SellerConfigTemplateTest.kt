package de.fluchitconsulting.rechnungsgenerator.setup

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlMap
import de.fluchitconsulting.rechnungsgenerator.core.model.Address
import de.fluchitconsulting.rechnungsgenerator.core.model.BankDetails
import de.fluchitconsulting.rechnungsgenerator.core.model.InputFileException
import de.fluchitconsulting.rechnungsgenerator.core.model.SellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

/**
 * Das Netz unter der Vorlage: Sie ist eine zweite Abschrift des Schemas und
 * veraltet sonst still – ein neues Feld in [SellerConfig] fiele niemandem auf,
 * weil ein fehlender Eintrag in einer Vorlage nichts kaputtmacht. Er kostet nur
 * den Nächsten, der die Datei ausfüllt.
 *
 * Geprüft wird die **einkommentierte** Vorlage: jedes `#`, das unmittelbar vor
 * einem Schlüssel steht, weggenommen. Genau das tut auch, wer einen
 * freiwilligen Eintrag haben will.
 */
class SellerConfigTemplateTest : FunSpec({

    /**
     * Nimmt jedes `#` weg, das unmittelbar vor einem Schlüssel steht
     * (`#telefon:`). Erklärender Text beginnt mit `# ` und bleibt stehen.
     */
    fun uncommented(text: String): String =
        text.lines().joinToString("\n") { line ->
            val rest = line.trimStart()
            if (rest.startsWith("#") && !rest.startsWith("# ")) line.replaceFirst("#", "") else line
        }

    val template = Yaml.default.parseToYamlNode(uncommented(SellerConfigTemplate.text)) as YamlMap

    /** Die YAML-Schlüssel einer Datenklasse: ihre `@SerialName` in der Reihenfolge des Deskriptors. */
    fun keysOf(descriptor: SerialDescriptor): Set<String> =
        (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }.toSet()

    fun keysIn(map: YamlMap): Set<String> =
        map.entries.keys
            .map { it.content }
            .toSet()

    /**
     * Die freiwilligen Einträge, die eine Vorgabe nennen, nach ihrem Schlüssel
     * auf oberster Ebene: `design` mit der Zeile `design: helsinki`,
     * `anschrift` mit `  land: DE`, `schlusssatz` mit einer Zeile je Sprache.
     */
    val defaults: Map<String, List<String>> =
        buildMap<String, MutableList<String>> {
            var parent = ""
            uncommented(SellerConfigTemplate.text)
                .lines()
                .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
                .forEach { line ->
                    if (!line.startsWith(" ")) parent = line.substringBefore(":")
                    if (line.substringAfter(":", "").isNotBlank()) getOrPut(parent) { mutableListOf() }.add(line)
                }
        }

    /**
     * Die Pflichtfelder mit gültigen Werten – den fiktiven aus den Testdaten –
     * und dazu [extra], Einträge aus [defaults]. Die unter `anschrift` kommen
     * in die Anschrift, eingerückte unter ihren eigenen Schlüssel, die übrigen
     * auf die oberste Ebene.
     */
    fun requiredFields(extra: Map<String, List<String>> = emptyMap()): String {
        val inAddress = extra["anschrift"].orEmpty()
        val topLevel =
            extra
                .filterKeys { it != "anschrift" }
                .flatMap { (key, lines) -> if (lines.first().startsWith(" ")) listOf("$key:") + lines else lines }
        return (
            listOf(
                "firma: Muster IT-Consulting UG (haftungsbeschränkt)",
                "inhaber: Dr. Erika Musterfrau",
                "anschrift:",
                "  strasse: Beispielweg 42",
                """  plz: "97070"""",
                "  ort: Würzburg",
            ) + inAddress +
                listOf(
                    "email: rechnung@muster-it-consulting.example",
                    "umsatzsteuer-id: DE123456788",
                    "bank:",
                    "  kontoinhaber: Muster IT-Consulting UG (haftungsbeschränkt)",
                    "  iban: DE89 3704 0044 0532 0130 00",
                    "  bic: MUSTDEFFXXX",
                    "  institut: Musterbank Würzburg",
                ) + topLevel
        ).joinToString("\n") + "\n"
    }

    test("die Vorlage nennt genau die Schlüssel der Stammdaten") {
        // In beide Richtungen: Ein neues Feld ohne Eintrag wird rot, ein
        // Eintrag ohne Feld ebenso – letzterer bräche sonst erst beim
        // Einkommentieren, und zwar bei der Benutzerin.
        keysIn(template) shouldBe keysOf(SellerConfig.serializer().descriptor)
        keysIn(template.get<YamlMap>("anschrift")!!) shouldBe keysOf(Address.serializer().descriptor)
        keysIn(template.get<YamlMap>("bank")!!) shouldBe keysOf(BankDetails.serializer().descriptor)
    }

    test("nennt die Vorlage eine Vorgabe, ist es die echte") {
        defaults.keys shouldContainAll listOf("anschrift", "zahlungsziel-tage", "design", "schlusssatz")

        // Der Schlusssatz ist eine Tabelle, und eine volle ist nicht gleich
        // einer leeren – gleich sein muss, was auf dem Blatt steht.
        val withDefaults = YamlLoader.parseSellerConfig(requiredFields(defaults))
        val without = YamlLoader.parseSellerConfig(requiredFields())
        Language.entries.forEach { language ->
            withClue(language) { withDefaults.closingLine(language) shouldBe without.closingLine(language) }
        }
        YamlLoader.parseSellerConfig(requiredFields(defaults - "schlusssatz")) shouldBe without
    }

    test("der Schlusssatz steht je Sprache unter ihrem genauen Code") {
        // Unter "en" stünde die britische Vorgabe auch für en-US, ob die
        // beiden gleich lauten oder nicht.
        keysIn(template.get<YamlMap>("schlusssatz")!!) shouldBe Language.entries.map { it.tag }.toSet()
    }

    test("die unausgefüllte Vorlage wird abgelehnt, und die Meldung nennt Datei und Schlüssel") {
        // Der Sinn der leeren Pflichtfelder: Wer eine Zeile übersieht, bekommt
        // einen Abbruch und kein PDF mit fremder Bankverbindung.
        val file = createTempDirectory("vorlage-test").resolve("stammdaten.yaml")
        file.writeText(SellerConfigTemplate.text)

        val failure = shouldThrow<InputFileException> { YamlLoader.loadSellerConfig(file) }

        failure.message!! shouldContain file.toString()
        failure.message!! shouldContain "firma"
    }
})
