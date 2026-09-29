package de.fluchitconsulting.rechnungsgenerator

import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.parse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class RechnungsgeneratorCommandTest : FunSpec({

    test("ohne Unterkommando zeigt es die Hilfe und endet nicht mit Exitcode 0") {
        val failure = shouldThrow<PrintHelpMessage> { RechnungsgeneratorCommand().parse(emptyArray()) }

        failure.statusCode shouldBe 1
    }

    test("die Hilfe nennt jedes Unterkommando") {
        val result = runCli("--help")

        result.exitCode shouldBe 0
        result.out shouldContain "erzeuge"
        result.out shouldContain "abschlaege"
        result.out shouldContain "einrichten"
    }

    test("--help bleibt ein Erfolg") {
        val help = shouldThrow<PrintHelpMessage> { RechnungsgeneratorCommand().parse(arrayOf("--help")) }

        help.statusCode shouldBe 0
    }
})
