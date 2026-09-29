package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.model.Design
import de.fluchitconsulting.rechnungsgenerator.core.values.Currency
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private val required = TemplateValue.entries.filter { it.required } + TemplateValue.ZAHLUNGSANGABE.first()

private val empty = TemplateValue.entries.associateWith { Latex.EMPTY }

private val head = "\\documentclass{article}\n${Foundation.USAGE}\n"

private fun macros(missing: TemplateMacro? = null, wrongArity: TemplateMacro? = null) =
    head + Template.LANGUAGE_FILE_USAGE + "\n" +
        TemplateMacro.entries
            .filterNot { it == missing }
            .joinToString("\n") { macro ->
                val arity = if (macro == wrongArity) macro.arity + 1 else macro.arity
                "\\newcommand{\\${macro.macroName}}[$arity]{}"
            }

private fun templateWith(placeholders: List<TemplateValue>) = macros() + "\n" + placeholders.joinToString("\n") { it.placeholder }

private fun templateWithout(missing: TemplateMacro? = null, wrongArity: TemplateMacro? = null) =
    macros(missing, wrongArity) + "\n" + required.joinToString("\n") { it.placeholder }

// #409
private fun currencySymbols(missing: Currency? = null) =
    Currency.entries
        .filterNot { it == missing }
        .joinToString("") { "\\newcommand{\\zeichen${it.name}}{}\n" }

// Die Makros stehen in diesen Tests in der Vorlage, nicht in der Sprachdatei.
private fun templateOf(source: String, languageFile: String = ""): Template = Template.of(source, currencySymbols() + languageFile)

// Liefert nur, was aus [text] geworden ist; das stimmt nur, solange [values]
// keinen Pflicht-Platzhalter belegt.
private fun filled(text: String, values: Map<TemplateValue, Latex> = emptyMap()): String {
    val body = templateWith(required) + "\n"
    return templateOf(body + text).fillIn(empty + values).removePrefix(templateOf(body).fillIn(empty))
}

/**
 * #201. Jede kaputte Vorlage ist in allem vollständig außer dem, was der Test
 * prüft, denn sie geht durch dieselbe Tür wie im Betrieb.
 */
class TemplateTest : FunSpec({

    val complete = templateOf(templateWith(required))

    test("für jedes Design lässt sich die Vorlage in jeder Sprache bauen") {
        Design.entries.forEach { design ->
            Language.entries.forEach { language ->
                withClue("$design $language") { Template.of(design, language) }
            }
        }
    }

    test("alle Sprachdateien eines Designs definieren dieselben Makros (#377)") {
        // Eine Hilfe mit @ im Namen gehört allein ihrer Datei.
        val definition = Regex("""\\newcommand\{\\([a-zA-Z]+)}(?:\[(\d+)])?""")

        fun macrosOf(design: Design, language: Language): Map<String, String> {
            val path = "/templates/${Template.languageFileOf(design, language)}"
            val source = withoutCommentLines(checkNotNull(Template::class.java.getResource(path)).readText())
            return definition.findAll(source).associate { it.groupValues[1] to it.groupValues[2] }
        }

        Design.entries.forEach { design ->
            val reference = macrosOf(design, Language.GERMAN)
            Language.entries.forEach { language ->
                withClue("$design $language") { macrosOf(design, language) shouldBe reference }
            }
        }
    }

    context("Jede Vorlage lädt die Grundlage (ADR-0022)") {
        val body = templateWith(required).removePrefix(head)

        test("ohne sie wird die Vorlage abgelehnt") {
            val error = shouldThrow<IllegalStateException> { templateOf("\\documentclass{article}\n$body") }

            error.message!! shouldContain Foundation.USAGE
        }

        test("sie muss direkt nach der Klasse stehen, denn sie lädt pdfx") {
            shouldThrow<IllegalStateException> {
                templateOf("\\documentclass{article}\n\\usepackage{geometry}\n${Foundation.USAGE}\n$body")
            }
        }

        test("Leerzeilen und Kommentarzeilen dazwischen zählen nicht, eine auskommentierte Grundlage aber auch nicht") {
            templateOf("\\documentclass{article}\n\n%% Die Grundlage.\n${Foundation.USAGE}\n$body")
            shouldThrow<IllegalStateException> { templateOf("\\documentclass{article}\n% ${Foundation.USAGE}\n$body") }
        }

        test("die Vorlage bringt die Grundlage als Datei mit") {
            complete.files.keys shouldBe setOf(Foundation.FILE_NAME, Template.LANGUAGE_FILE_NAME)
        }
    }

    context("Jede Vorlage lädt ihre Sprachdatei (#377)") {
        val languageFile = "\\newcommand{\\wortbetrag}{Betrag}"

        test("ohne sie wird die Vorlage abgelehnt") {
            val template = templateWith(required).replace(Template.LANGUAGE_FILE_USAGE, "")

            val error = shouldThrow<IllegalStateException> { templateOf(template, languageFile) }

            error.message!! shouldContain Template.LANGUAGE_FILE_USAGE
        }

        test("die Sprachdatei geht mit, wie sie ist") {
            String(templateOf(templateWith(required), languageFile).files.getValue(Template.LANGUAGE_FILE_NAME)) shouldBe
                currencySymbols() + languageFile
        }

        test("ein Makro des Renderers darf in der Sprachdatei stehen") {
            val template = templateWithout(missing = TemplateMacro.BETRAG)

            templateOf(template, "\\newcommand{\\betrag}[1]{\\num{#1}~€}")
            shouldThrow<IllegalStateException> { templateOf(template, "\\newcommand{\\betrag}{€}") }
                .message!! shouldContain "betrag"
        }

        test("eine Sprachdatei ohne Zeichen für eine Währung wird abgelehnt, auch mit auskommentiertem (#409)") {
            Currency.entries.forEach { currency ->
                withClue(currency) {
                    val error =
                        shouldThrow<IllegalStateException> {
                            Template.of(templateWith(required), currencySymbols(missing = currency))
                        }
                    error.message!! shouldContain "zeichen${currency.name}"

                    shouldThrow<IllegalStateException> {
                        Template.of(
                            templateWith(required),
                            currencySymbols(missing = currency) + "% \\newcommand{\\zeichen${currency.name}}{}",
                        )
                    }
                }
            }
        }

        test("ein Platzhalter in der Sprachdatei wird abgelehnt, einer im Kommentar nicht") {
            val error =
                shouldThrow<IllegalStateException> { templateOf(templateWith(required), "\\newcommand{\\wortfirma}{<<FIRMA>>}") }

            error.message!! shouldContain "<<FIRMA>>"
            templateOf(templateWith(required), "% <<FIRMA>>")
        }
    }

    context("Vorlage und Renderer müssen dieselben Makros kennen") {
        // #30

        test("ein Makro, das die Vorlage nicht kennt, wird benannt") {
            val error =
                shouldThrow<IllegalStateException> {
                    templateOf(templateWithout(missing = TemplateMacro.POSITIONSZEILE))
                }

            error.message!! shouldContain "positionszeile"
        }

        test("eine falsche Stelligkeit fällt ebenfalls auf") {
            val error =
                shouldThrow<IllegalStateException> {
                    templateOf(templateWithout(wrongArity = TemplateMacro.POSITIONSZEILE))
                }

            error.message!! shouldContain "positionszeile"
            error.message!! shouldContain "7"
        }

        test("ein auskommentiertes Makro gilt nicht als definiert") {
            val template =
                templateWithout(missing = TemplateMacro.POSITIONSZEILE) +
                    "\n% \\newcommand{\\positionszeile}[${TemplateMacro.POSITIONSZEILE.arity}]{}"

            val error = shouldThrow<IllegalStateException> { templateOf(template) }

            error.message!! shouldContain "positionszeile"
        }
    }

    context("Welche Platzhalter jede Vorlage setzen muss") {
        // Nur die Platzhalter, deren Inhalt an Rechnungsart oder Rechnung hängt (#188).

        test("fehlt einer, den keine Vorlage weglassen darf, wird er benannt") {
            val error =
                shouldThrow<IllegalStateException> {
                    templateOf(templateWith(required - TemplateValue.SKONTO))
                }

            error.message!! shouldContain "<<SKONTO>>"
        }

        test("einer, der nur in einer Kommentarzeile steht, fehlt ebenso") {
            val error =
                shouldThrow<IllegalStateException> {
                    templateOf(templateWith(required - TemplateValue.SKONTO) + "\n% <<SKONTO>>")
                }

            error.message!! shouldContain "<<SKONTO>>"
        }

        test("aus einer Gruppe genügt einer, gleich welcher") {
            // #211
            val withoutPaymentInfo = required - TemplateValue.ZAHLUNGSZIEL - TemplateValue.ZAHLUNGSFRIST

            templateOf(templateWith(withoutPaymentInfo + TemplateValue.ZAHLUNGSZIEL))
            templateOf(templateWith(withoutPaymentInfo + TemplateValue.ZAHLUNGSFRIST))
        }

        test("fehlt eine Gruppe ganz, werden alle ihre Platzhalter genannt") {
            val error =
                shouldThrow<IllegalStateException> {
                    templateOf(templateWith(required - TemplateValue.ZAHLUNGSZIEL - TemplateValue.ZAHLUNGSFRIST))
                }

            error.message!! shouldContain "<<ZAHLUNGSZIEL>>"
            error.message!! shouldContain "<<ZAHLUNGSFRIST>>"
        }

        test("die Angaben der Stammdaten darf eine Vorlage weglassen") {
            // "Steuernummer oder USt-IdNr." prüft das Pflichtangaben-Netz am PDF.
            templateOf(templateWith(required))
        }

        test("einen Schalter darf sie ebenso weglassen, und die Einleitung auch") {
            // Ein Schalter ist kein Inhalt (#248), die Einleitung optional (#209).
            val template = templateOf(templateWith(required))

            template.uses(TemplateValue.LOGO) shouldBe false
            template.uses(TemplateValue.RABATTSPALTE) shouldBe false
            template.uses(TemplateValue.EINLEITUNG) shouldBe false
        }
    }

    context("Ob die Vorlage einen Platzhalter setzt") {
        test("steht er in der Vorlage, setzt sie ihn") {
            templateOf(templateWith(required) + "\n<<LOGO>>").uses(TemplateValue.LOGO) shouldBe true
        }

        test("steht er nur in einer Kommentarzeile, setzt sie ihn nicht") {
            templateOf(templateWith(required) + "\n% <<LOGO>>").uses(TemplateValue.LOGO) shouldBe false
        }
    }

    context("Vorlage und Werte müssen zusammenpassen") {
        test("ein Platzhalter ohne Wert bleibt nicht stehen, sondern bricht ab") {
            // Sonst stünde "<<SUMMEN>>" im PDF, und LuaLaTeX wie veraPDF ließen es durch.
            val error = shouldThrow<IllegalStateException> { complete.fillIn(empty - TemplateValue.SUMMEN) }

            error.message!! shouldContain "<<SUMMEN>>"
            error.message!! shouldContain "keinen Wert"
        }

        test("ein Platzhalter, den der Renderer nicht kennt, bricht ebenfalls ab") {
            val error = shouldThrow<IllegalStateException> { filled("<<FRIMA>>") }

            error.message!! shouldContain "<<FRIMA>>"
        }

        test("ein Wert ohne Platzhalter ist erlaubt") {
            // #188
            filled(
                "<<FIRMA>>",
                mapOf(TemplateValue.FIRMA to "Muster".escapeLatex(), TemplateValue.WEBSEITE to "muster.example".escapeLatex()),
            ) shouldBe "Muster"
        }

        test("passen beide zusammen, kommt der gefüllte Text heraus") {
            filled(
                "<<FIRMA>> und <<ORT>>",
                mapOf(TemplateValue.FIRMA to "eins".escapeLatex(), TemplateValue.ORT to "zwei".escapeLatex()),
            ) shouldBe "eins und zwei"
        }

        test("ein Wert, der wie ein späterer Platzhalter aussieht, bleibt stehen") {
            filled(
                "<<FIRMA>> und <<ORT>>",
                mapOf(TemplateValue.FIRMA to "Kunde <<ORT>>".escapeLatex(), TemplateValue.ORT to "zwei".escapeLatex()),
            ) shouldBe "Kunde <<ORT>> und zwei"
        }

        test("ein Wert, der wie ein früherer Platzhalter aussieht, gilt nicht als Lücke der Vorlage") {
            filled(
                "<<FIRMA>> und <<ORT>>",
                mapOf(TemplateValue.FIRMA to "eins".escapeLatex(), TemplateValue.ORT to "Kunde <<FIRMA>>".escapeLatex()),
            ) shouldBe "eins und Kunde <<FIRMA>>"
        }

        test("Backslash und Dollar im Wert werden wörtlich eingesetzt") {
            // Ein Ersatztext per regulärem Ausdruck läse "\" und "$1" als Rückverweis.
            filled("<<FIRMA>>", mapOf(TemplateValue.FIRMA to Latex.raw("\\textbf{\$1}"))) shouldBe "\\textbf{\$1}"
        }
    }

    context("Kein Wert enthält ein rohes Zeilenende") {
        test("ein rohes Zeilenende in einem Wert bricht ab und nennt den Platzhalter") {
            // #147
            val error =
                shouldThrow<IllegalStateException> {
                    complete.fillIn(empty + (TemplateValue.EMPFAENGER to Latex.raw("Muster GmbH\\\\Musterweg 1")))
                }

            error.message!! shouldContain "<<EMPFAENGER>>"
        }

        test("ein Zeilenwechsel über das Makro der Vorlage besteht die Prüfung") {
            val address = "Muster GmbH".escapeLatex() + Latex.macro(TemplateMacro.ZEILENWECHSEL) + "Musterweg 1".escapeLatex()

            complete.fillIn(empty + (TemplateValue.EMPFAENGER to address))
        }

        test("zwei Backslashes aus den Eingabedaten sind kein Zeilenende") {
            complete.fillIn(empty + (TemplateValue.SCHLUSSSATZ to "Ablage unter \\\\server\\rechnungen".escapeLatex()))
        }
    }

    context("Die Kommentarzeilen der Vorlage") {
        // #149

        test("eine ganze Kommentarzeile verschwindet samt Zeilenende, auch eingerückt") {
            filled("a\n%% Kommentar\n  % eingerückt\nb\n") shouldBe "a\nb\n"
        }

        test("ein % hinter Code bleibt stehen") {
            // Am Zeilenende einer Makrodefinition unterdrückt es den Zwischenraum.
            val text = "\\newcommand{\\x}[1]{%\n  #1}%\n\\usepackage{booktabs}  %% Hinweis\n"

            filled(text) shouldBe text
        }

        test("ein maskiertes Prozentzeichen am Zeilenanfang ist kein Kommentar") {
            filled("\\% Rabatt\n") shouldBe "\\% Rabatt\n"
        }
    }
})
