package de.fluchitconsulting.rechnungsgenerator.core.values

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class LanguageTableTest : FunSpec({

    val german = Language.GERMAN
    val english = Language.BRITISH_ENGLISH

    test("die Hauptsprache gilt für jede ihrer Varianten") {
        LanguageTable(mapOf("de" to "X"))[german] shouldBe "X"
    }

    test("der genaue Code geht der Hauptsprache vor") {
        LanguageTable(mapOf("de" to "X", "de-DE" to "Y"))[german] shouldBe "Y"
    }

    test("seitwärts wird nicht gesucht, und ohne passenden Schlüssel gibt es keinen Text") {
        LanguageTable(mapOf("de-DE" to "X"))[english] shouldBe null
        LanguageTable.EMPTY[german] shouldBe null
    }

    test("die Schreibweise der Schlüssel zählt nicht") {
        LanguageTable(mapOf("EN-gb" to "X"))[english] shouldBe "X"
        LanguageTable(mapOf("EN-gb" to "X")) shouldBe LanguageTable(mapOf("en-GB" to "X"))
    }

    test("nur unterstützte Sprachen und ihre Hauptsprache sind Schlüssel") {
        // Ein Satz, den keine Rechnung je verwendet, ist derselbe stille
        // Fehler wie ein vertippter Schlüssel.
        listOf("fr", "en-US", "englisch", "").forEach { key ->
            withClue(key) {
                val error = shouldThrow<InvalidLanguageRangeException> { LanguageTable(mapOf(key to "X")) }

                error.message!! shouldContain "Unbekannte Sprache '$key'"
                error.message!! shouldContain "de-DE, de, en-GB, en"
            }
        }
    }

    test("zwei Schlüssel für denselben Sprachbereich sind ein Widerspruch") {
        val error = shouldThrow<InvalidLanguageRangeException> { LanguageTable(mapOf("de-DE" to "X", "DE-de" to "Y")) }

        error.message!! shouldContain "'de-DE' und 'DE-de'"
    }
})
