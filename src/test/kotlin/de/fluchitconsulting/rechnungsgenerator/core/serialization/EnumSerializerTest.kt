package de.fluchitconsulting.rechnungsgenerator.core.serialization

import com.charleskorn.kaml.Yaml
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.SerializationException

/** Die Werte tragen absichtlich Großbuchstaben: Eine roh geschlüsselte Map fiele sonst keinem Test auf. */
class EnumSerializerTest : FunSpec({

    data class Color(val name: String)

    val colors = listOf(Color("Rot"), Color("Grün"), Color("Blau"))
    val lookup = CodeLookup("Unbekannte Farbe", colors, { it.trim().lowercase() }, Color::name)

    test("normalisiert auf beiden Seiten, nicht nur bei der Eingabe") {
        lookup.of("rot") shouldBe Color("Rot")
        lookup.of("ROT") shouldBe Color("Rot")
        lookup.of("  Rot  ") shouldBe Color("Rot")
    }

    test("nennt bei einem Fehlschlag den Wert und alle zulässigen") {
        val error = shouldThrow<SerializationException> { lookup.of("Lila") }

        error.message!! shouldContain "Unbekannte Farbe 'Lila'"
        error.message!! shouldContain "Rot, Grün, Blau"
    }

    test("enumSerializer verpackt beides: lesen und melden") {
        val serializer = enumSerializer("Farbe", colors, "Unbekannte Farbe", { it.trim().lowercase() }, Color::name)

        Yaml.default.decodeFromString(serializer, "  ROT  ") shouldBe Color("Rot")
        Yaml.default.encodeToString(serializer, Color("Grün")) shouldBe "\"Grün\""

        val error = shouldThrow<SerializationException> { Yaml.default.decodeFromString(serializer, "Lila") }
        error.message!! shouldContain "Unbekannte Farbe 'Lila'"
    }

    test("die Beschreibung in der Meldung darf mehr zeigen als den Schlüssel") {
        // So macht es TaxCategory: "S (Regelsatz)".
        val withDescription =
            CodeLookup("Unbekannte Farbe", colors, { it.lowercase() }, Color::name) { "${it.name} (eine Farbe)" }

        val error = shouldThrow<SerializationException> { withDescription.of("Lila") }

        error.message!! shouldContain "Rot (eine Farbe)"
    }
})
