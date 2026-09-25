package de.fluchitconsulting.rechnungsgenerator.setup

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlMap
import de.fluchitconsulting.rechnungsgenerator.core.model.Address
import de.fluchitconsulting.rechnungsgenerator.core.model.BankDetails
import de.fluchitconsulting.rechnungsgenerator.core.model.InputFileException
import de.fluchitconsulting.rechnungsgenerator.core.model.SellerConfig
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
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
     * Die Pflichtfelder mit gültigen Werten – den fiktiven aus den Testdaten.
     * [extra] sind Zeilen der Vorlage; eingerückte gehören unter `anschrift`,
     * die übrigen auf die oberste Ebene.
     */
    fun requiredFields(extra: List<String> = emptyList()): String {
        val (inAddress, topLevel) = extra.partition { it.startsWith(" ") }
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
        // Eine Zeile mit Wert, die kein erklärender Text ist: genau die
        // freiwilligen Einträge, die eine Vorgabe mitbringen.
        val defaults =
            uncommented(SellerConfigTemplate.text)
                .lines()
                .filter { !it.trimStart().startsWith("# ") && it.substringAfter(":", "").isNotBlank() }
        defaults.shouldNotBeEmpty()

        YamlLoader.parseSellerConfig(requiredFields(defaults)) shouldBe YamlLoader.parseSellerConfig(requiredFields())
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
