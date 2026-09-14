package de.fluchitconsulting.rechnungsgenerator.generate.render

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LatexTest : FunSpec({

    test("maskiert die LaTeX-Sonderzeichen") {
        assertSoftly {
            // Der häufigste reale Fall: ein Firmenname mit kaufmännischem Und.
            "Meier & Söhne".escapeLatex().tex shouldBe "Meier \\& Söhne"
            // Und der gefährlichste: % kommentiert in LaTeX den Zeilenrest aus.
            "50 % Anzahlung".escapeLatex().tex shouldBe "50 \\% Anzahlung"
            "Preis in \$".escapeLatex().tex shouldBe "Preis in \\\$"
            "Nr. #7".escapeLatex().tex shouldBe "Nr. \\#7"
            "snake_case".escapeLatex().tex shouldBe "snake\\_case"
            "{geschweift}".escapeLatex().tex shouldBe "\\{geschweift\\}"
        }
    }

    test("Backslash wird nicht doppelt maskiert") {
        "a\\b".escapeLatex().tex shouldBe "a\\textbackslash{}b"
    }

    test("Tilde und Zirkumflex werden zu Textbefehlen") {
        assertSoftly {
            "ca.~5".escapeLatex().tex shouldBe "ca.\\textasciitilde{}5"
            "2^10".escapeLatex().tex shouldBe "2\\textasciicircum{}10"
        }
    }

    test("Umlaute und das Eurozeichen bleiben unangetastet") {
        // LuaLaTeX liest UTF-8 direkt – hier darf nichts passieren.
        "Würzburg 95,00 € Größe".escapeLatex().tex shouldBe "Würzburg 95,00 € Größe"
    }

    test("normaler Text bleibt unverändert") {
        "IT-Beratung gemäß Rahmenvertrag".escapeLatex().tex shouldBe "IT-Beratung gemäß Rahmenvertrag"
    }

    test("ein Makro ohne Argument endet mit {}, damit der folgende Text nicht zum Namen wird") {
        // Ohne {} stünde hier \ustidtrennerUSt-IdNr. – ein unbekanntes Makro,
        // das erst LuaLaTeX bemerkte (#76).
        (Latex.macro(TemplateMacro.USTIDTRENNER) + "USt-IdNr.".escapeLatex()).tex shouldBe
            "\\ustidtrenner{}USt-IdNr."
    }
})
