package de.fluchitconsulting.rechnungsgenerator.values

import com.charleskorn.kaml.Yaml
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.SerializationException

/**
 * Der Fall, der dieses Werkzeug ausgelöst hat: Beim dritten Enum wich die Kopie
 * ab – die Map schlüsselte roh, nachgeschlagen wurde kleingeschrieben. Solange
 * alle Schreibweisen klein sind, ist das folgenlos, und kein Test der
 * Eingabeseite bemerkt es.
 *
 * Hier wird es bemerkt: Die Werte tragen absichtlich Großbuchstaben.
 */
class EnumSerializerTest : FunSpec({

    data class Farbe(val name: String)

    val farben = listOf(Farbe("Rot"), Farbe("Grün"), Farbe("Blau"))
    val lookup = CodeLookup("Unbekannte Farbe", farben, { it.trim().lowercase() }, Farbe::name)

    test("normalisiert auf beiden Seiten, nicht nur bei der Eingabe") {
        // Der Schlüssel im Eintrag ist großgeschrieben, die Eingabe klein –
        // gefunden wird trotzdem, weil beide Seiten durch dieselbe
        // Normalisierung gehen.
        lookup.of("rot") shouldBe Farbe("Rot")
        lookup.of("ROT") shouldBe Farbe("Rot")
        lookup.of("  Rot  ") shouldBe Farbe("Rot")
    }

    test("nennt bei einem Fehlschlag den Wert und alle zulässigen") {
        val fehler = shouldThrow<SerializationException> { lookup.of("Lila") }

        fehler.message!! shouldContain "Unbekannte Farbe 'Lila'"
        fehler.message!! shouldContain "Rot, Grün, Blau"
    }

    test("enumSerializer verpackt beides: lesen und melden") {
        // Die öffentliche Tür. Dass sie dieselbe Vereinheitlichung mitbringt
        // wie CodeLookup, ist der Sinn der Übung – ein Enum deklariert damit
        // an einer Stelle, was unter seinem Schlüssel stehen darf.
        val serializer = enumSerializer("Farbe", farben, "Unbekannte Farbe", { it.trim().lowercase() }, Farbe::name)

        Yaml.default.decodeFromString(serializer, "  ROT  ") shouldBe Farbe("Rot")
        Yaml.default.encodeToString(serializer, Farbe("Grün")) shouldBe "\"Grün\""

        val fehler = shouldThrow<SerializationException> { Yaml.default.decodeFromString(serializer, "Lila") }
        fehler.message!! shouldContain "Unbekannte Farbe 'Lila'"
    }

    test("die Beschreibung in der Meldung darf mehr zeigen als den Schlüssel") {
        // So macht es TaxCategory: Der Code allein ("S") sagt niemandem etwas,
        // "S (Regelsatz)" schon.
        val mitErklaerung =
            CodeLookup("Unbekannte Farbe", farben, { it.lowercase() }, Farbe::name) { "${it.name} (eine Farbe)" }

        val fehler = shouldThrow<SerializationException> { mitErklaerung.of("Lila") }

        fehler.message!! shouldContain "Rot (eine Farbe)"
    }
})
