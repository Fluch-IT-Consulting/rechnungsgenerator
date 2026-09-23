package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.text.TextTemplate
import de.fluchitconsulting.rechnungsgenerator.core.text.TextValue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

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

    // Das Zerlegen prüft TextTemplateTest; hier nur, was das Format dazutut.
    context("Wortlaut mit Variablen") {
        test("maskiert festen Text und Werte, jeden Teil genau einmal") {
            TextTemplate
                .parse<TestVariable>("50 % von <<NUMBER>> & mehr")
                .toLatex { TextValue.Text("Meier & Söhne") }
                .tex shouldBe "50 \\% von \\zusammen{Meier \\& Söhne} \\& mehr"
        }

        test("ein Datum steht ausgeschrieben und zusammengehalten") {
            TextTemplate
                .parse<TestVariable>("vom <<DATE>>")
                .toLatex { TextValue.Date(LocalDate.of(2026, 7, 15)) }
                .tex shouldBe "vom \\zusammen{15.~Juli 2026}"
        }

        test("ein Wert, der selbst einen Platzhalter enthält, ist Inhalt") {
            TextTemplate
                .parse<TestVariable>("<<NUMBER>>")
                .toLatex { TextValue.Text("<<DATE>>") }
                .tex shouldBe "\\zusammen{<<DATE>>}"
        }

        test("ohne Variablen dasselbe wie escapeLatex(), und der Resolver wird nicht gefragt") {
            TextTemplate.parse<TestVariable>("Meier & Söhne").toLatex { error("nicht gefragt: $it") } shouldBe
                "Meier & Söhne".escapeLatex()
        }

        test("kann der Resolver eine Variable nicht auflösen, kommt seine Ausnahme durch") {
            shouldThrow<IllegalStateException> {
                TextTemplate.parse<TestVariable>("<<DATE>>").toLatex { error("kein Wert für <<$it>>") }
            }.message shouldBe "kein Wert für <<DATE>>"
        }
    }
})

private enum class TestVariable { NUMBER, DATE }
