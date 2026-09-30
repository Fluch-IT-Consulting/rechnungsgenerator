package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.text.TextTemplate
import de.fluchitconsulting.rechnungsgenerator.core.text.TextValue
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class LatexTest : FunSpec({

    test("maskiert die LaTeX-Sonderzeichen") {
        assertSoftly {
            "Meier & Söhne".escapeLatex().tex shouldBe "Meier \\& Söhne"
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
        "Würzburg 95,00 € Größe".escapeLatex().tex shouldBe "Würzburg 95,00 € Größe"
    }

    test("normaler Text bleibt unverändert") {
        "IT-Beratung gemäß Rahmenvertrag".escapeLatex().tex shouldBe "IT-Beratung gemäß Rahmenvertrag"
    }

    context("Metadaten für pdfx (#485)") {
        test("was pdfx wörtlich liest, bleibt stehen, sonst stünden Info-Dictionary und XMP verschieden da") {
            "RE_2026 #7 ~5 2^10 \$ Meier & Söhne <a> \"b\" 'c'".escapeXmpData().tex shouldBe
                "RE_2026 #7 ~5 2^10 \$ Meier & Söhne <a> \"b\" 'c'"
        }

        test("Prozent, Klammern und Backslash nimmt pdfx nur maskiert") {
            "50 % {a} a\\b".escapeXmpData().tex shouldBe "50 \\% \\{a\\} a\\backslash b"
        }

        test("ein Leerzeichen nach dem Backslash ist ausdrücklich, sonst verschluckte es TeX") {
            "a\\ b".escapeXmpData().tex shouldBe "a\\backslash \\ b"
        }

        test("jeder Leerraum wird ein Leerzeichen, auch das geschützte und das schmale") {
            "Muster GmbH 01.06. – 31.08. a\nb".escapeXmpData().tex shouldBe "Muster GmbH 01.06. – 31.08. a b"
        }
    }

    test("ein Makro ohne Argument endet mit {}, damit der folgende Text nicht zum Namen wird") {
        // #76
        (Latex.macro(TemplateMacro.ZEILENWECHSEL) + "Musterweg".escapeLatex()).tex shouldBe
            "\\zeilenwechsel{}Musterweg"
    }

    // Das Zerlegen prüft TextTemplateTest; hier nur, was das Format dazutut.
    context("Wortlaut mit Variablen") {
        test("maskiert festen Text und Werte, jeden Teil genau einmal") {
            TextTemplate
                .parse<TestVariable>("50 % von <<NUMBER>> & mehr")
                .toLatex(DateFormat(Language.GERMAN)) { TextValue.Text("Meier & Söhne") }
                .tex shouldBe "50 \\% von \\zusammen{Meier \\& Söhne} \\& mehr"
        }

        test("ein Datum steht ausgeschrieben und zusammengehalten") {
            TextTemplate
                .parse<TestVariable>("vom <<DATE>>")
                .toLatex(DateFormat(Language.GERMAN)) { TextValue.Date(LocalDate.of(2026, 7, 15)) }
                .tex shouldBe "vom \\zusammen{15.~Juli 2026}"
        }

        test("ein Wert, der selbst einen Platzhalter enthält, ist Inhalt") {
            TextTemplate
                .parse<TestVariable>("<<NUMBER>>")
                .toLatex(DateFormat(Language.GERMAN)) { TextValue.Text("<<DATE>>") }
                .tex shouldBe "\\zusammen{<<DATE>>}"
        }

        test("ohne Variablen dasselbe wie escapeLatex(), und der Resolver wird nicht gefragt") {
            TextTemplate.parse<TestVariable>("Meier & Söhne").toLatex(DateFormat(Language.GERMAN)) { error("nicht gefragt: $it") } shouldBe
                "Meier & Söhne".escapeLatex()
        }

        test("kann der Resolver eine Variable nicht auflösen, kommt seine Ausnahme durch") {
            shouldThrow<IllegalStateException> {
                TextTemplate.parse<TestVariable>("<<DATE>>").toLatex(DateFormat(Language.GERMAN)) { error("kein Wert für <<$it>>") }
            }.message shouldBe "kein Wert für <<DATE>>"
        }
    }
})

private enum class TestVariable { NUMBER, DATE }
