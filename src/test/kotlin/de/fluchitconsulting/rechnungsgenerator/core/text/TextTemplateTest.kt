package de.fluchitconsulting.rechnungsgenerator.core.text

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

private enum class TestVariable { NUMBER, DATE }

private fun TextTemplate<TestVariable>.parts(): List<String> = map(text = { "Text($it)" }, variable = { "Variable($it)" })

class TextTemplateTest : FunSpec({

    test("zerlegt den Wortlaut an den Platzhaltern, in Reihenfolge") {
        TextTemplate.parse<TestVariable>("Rechnung <<NUMBER>> vom <<DATE>>.").parts() shouldContainExactly
            listOf("Text(Rechnung )", "Variable(NUMBER)", "Text( vom )", "Variable(DATE)", "Text(.)")
    }

    test("ein Wortlaut ohne Platzhalter ist ein einziger Teil") {
        TextTemplate.parse<TestVariable>("Ein bereits gezahlter Betrag wird erstattet.").parts() shouldContainExactly
            listOf("Text(Ein bereits gezahlter Betrag wird erstattet.)")
    }

    test("am Rand und zwischen zwei Platzhaltern entsteht kein leerer Text") {
        TextTemplate.parse<TestVariable>("<<NUMBER>><<DATE>>").parts() shouldContainExactly
            listOf("Variable(NUMBER)", "Variable(DATE)")
    }

    test("kleingeschrieben ist kein Platzhalter und bleibt Text") {
        TextTemplate.parse<TestVariable>("<<number>>").parts() shouldContainExactly listOf("Text(<<number>>)")
    }

    test("ein Name, den das Vokabular nicht kennt, wirft beim Bau und nennt ihn") {
        shouldThrow<IllegalArgumentException> {
            TextTemplate.parse<TestVariable>("Rechnung <<NUMMER>>")
        }.message shouldBe "Unbekannte Variable <<NUMMER>>."
    }
})
