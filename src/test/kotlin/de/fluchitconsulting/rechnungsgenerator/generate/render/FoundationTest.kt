package de.fluchitconsulting.rechnungsgenerator.generate.render

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** ADR-0022 */
class FoundationTest : FunSpec({

    test("die mitgebrachte Grundlage hält beide Regeln und behält ihre Kommentare") {
        val source = Foundation::class.java.getResource("/templates/${Foundation.FILE_NAME}")!!.readText()

        String(Foundation.BUILT_IN.content) shouldBe source
    }

    test("ein Platzhalter wird abgelehnt, auch einer, den der Renderer nicht kennt") {
        val error = shouldThrow<IllegalStateException> { Foundation.of("\\newcommand{\\firma}{<<FRIMA>>}") }

        error.message!! shouldContain "<<FRIMA>>"
    }

    test("ein Makro, das der Renderer aufruft, wird abgelehnt, gleich wie definiert") {
        val definitions =
            listOf(
                "\\newcommand{\\hinweisabsatz}[1]{#1}",
                "\\renewcommand*{\\hinweisabsatz}[1]{#1}",
                "\\providecommand{\\hinweisabsatz}[1]{#1}",
                "\\def\\hinweisabsatz#1{#1}",
            )

        definitions.forEach { definition ->
            val error = shouldThrow<IllegalStateException> { Foundation.of(definition) }
            error.message!! shouldContain "\\hinweisabsatz"
        }
    }

    test("in einer Kommentarzeile zählt beides nicht") {
        Foundation.of("% <<FIRMA>>\n% \\newcommand{\\hinweisabsatz}[1]{#1}\n\\newcommand{\\programmsatz}[1]{#1}")
    }
})
