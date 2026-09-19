package de.fluchitconsulting.rechnungsgenerator.generate.render

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
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

    test("das schmale Leerzeichen wird zu \\, – die Schrift hat dafür kein Zeichen (#192)") {
        "01.06.2026\u2009–\u200931.08.2026".escapeLatex().tex shouldBe "01.06.2026\\,–\\,31.08.2026"
    }

    test("Umlaute und das Eurozeichen bleiben unangetastet") {
        // LuaLaTeX liest UTF-8 direkt – hier darf nichts passieren.
        "Würzburg 95,00 € Größe".escapeLatex().tex shouldBe "Würzburg 95,00 € Größe"
    }

    test("normaler Text bleibt unverändert") {
        "IT-Beratung gemäß Rahmenvertrag".escapeLatex().tex shouldBe "IT-Beratung gemäß Rahmenvertrag"
    }

    test("ein Makro ohne Argument endet mit {}, damit der folgende Text nicht zum Namen wird") {
        // Ohne {} stünde hier \zeilenwechselMusterweg – ein unbekanntes Makro,
        // das erst LuaLaTeX bemerkte (#76).
        (Latex.macro(TemplateMacro.ZEILENWECHSEL) + "Musterweg".escapeLatex()).tex shouldBe
            "\\zeilenwechsel{}Musterweg"
    }

    context("Text mit Platzhaltern") {
        test("maskiert nur den festen Text, die eingesetzten Werte bleiben unberührt") {
            "50 % von <<BETRAG>> & mehr".escapeLatex { Latex.raw("\\textbf{x}") }.tex shouldBe
                "50 \\% von \\textbf{x} \\& mehr"
        }

        test("löst jeden Platzhalter nach seinem Namen auf, auch am Anfang und am Ende") {
            val values = mapOf("A" to "eins".escapeLatex(), "B" to "zwei".escapeLatex())
            "<<A>> und <<B>>".escapeLatex { values.getValue(it) }.tex shouldBe "eins und zwei"
        }

        test("ohne Platzhalter dasselbe wie escapeLatex(), und der Resolver wird nicht gefragt") {
            "Meier & Söhne".escapeLatex { error("nicht gefragt: $it") } shouldBe "Meier & Söhne".escapeLatex()
        }

        test("ein Wert, der selbst einen Platzhalter enthält, ist Inhalt") {
            "<<A>>"
                .escapeLatex { name ->
                    if (name == "A") "<<B>>".escapeLatex() else error("nicht gefragt: $name")
                }.tex shouldBe "<<B>>"
        }

        test("kleingeschrieben ist es kein Platzhalter") {
            "<<name>>".escapeLatex { error("nicht gefragt: $it") }.tex shouldBe "<<name>>"
        }

        test("kann der Resolver einen Namen nicht auflösen, kommt seine Ausnahme durch") {
            shouldThrow<IllegalStateException> {
                "<<FRIMA>>".escapeLatex { error("Unbekannte Variable <<$it>>") }
            }.message shouldBe "Unbekannte Variable <<FRIMA>>"
        }
    }
})
