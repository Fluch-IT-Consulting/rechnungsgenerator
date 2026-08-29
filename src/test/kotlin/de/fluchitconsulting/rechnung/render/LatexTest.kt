package de.fluchitconsulting.rechnung.render

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LatexTest : FunSpec({

    test("maskiert die LaTeX-Sonderzeichen") {
        assertSoftly {
            // Der häufigste reale Fall: ein Firmenname mit kaufmännischem Und.
            "Meier & Söhne".escapeLatex() shouldBe "Meier \\& Söhne"
            // Und der gefährlichste: % kommentiert in LaTeX den Zeilenrest aus.
            "50 % Anzahlung".escapeLatex() shouldBe "50 \\% Anzahlung"
            "Preis in \$".escapeLatex() shouldBe "Preis in \\\$"
            "Nr. #7".escapeLatex() shouldBe "Nr. \\#7"
            "snake_case".escapeLatex() shouldBe "snake\\_case"
            "{geschweift}".escapeLatex() shouldBe "\\{geschweift\\}"
        }
    }

    test("Backslash wird nicht doppelt maskiert") {
        "a\\b".escapeLatex() shouldBe "a\\textbackslash{}b"
    }

    test("Tilde und Zirkumflex werden zu Textbefehlen") {
        assertSoftly {
            "ca.~5".escapeLatex() shouldBe "ca.\\textasciitilde{}5"
            "2^10".escapeLatex() shouldBe "2\\textasciicircum{}10"
        }
    }

    test("Umlaute und das Eurozeichen bleiben unangetastet") {
        // LuaLaTeX liest UTF-8 direkt – hier darf nichts passieren.
        "Würzburg 95,00 € Größe".escapeLatex() shouldBe "Würzburg 95,00 € Größe"
    }

    test("normaler Text bleibt unverändert") {
        "IT-Beratung gemäß Rahmenvertrag".escapeLatex() shouldBe "IT-Beratung gemäß Rahmenvertrag"
    }
})
