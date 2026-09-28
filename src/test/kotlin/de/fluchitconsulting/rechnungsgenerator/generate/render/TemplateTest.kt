package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.model.Design
import de.fluchitconsulting.rechnungsgenerator.core.values.Currency
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

// Die Pflicht-Platzhalter und aus jeder Gruppe, von der einer dastehen muss,
// der erste.
private val required = TemplateValue.entries.filter { it.required } + TemplateValue.ZAHLUNGSANGABE.first()

// Für jeden Platzhalter ein Wert, und zwar ein leerer. Ein Test legt darüber,
// was er prüfen will.
private val empty = TemplateValue.entries.associateWith { Latex.EMPTY }

// Der Kopf jeder Vorlage: die Klasse und direkt danach die Grundlage.
private val head = "\\documentclass{article}\n${Foundation.USAGE}\n"

/**
 * Der Kopf, das Laden der Sprachdatei und die Definitionen aller Makros – bis
 * auf die genannte Abweichung.
 */
private fun macros(missing: TemplateMacro? = null, wrongArity: TemplateMacro? = null) =
    head + Template.LANGUAGE_FILE_USAGE + "\n" +
        TemplateMacro.entries
            .filterNot { it == missing }
            .joinToString("\n") { macro ->
                val arity = if (macro == wrongArity) macro.arity + 1 else macro.arity
                "\\newcommand{\\${macro.macroName}}[$arity]{}"
            }

/** Eine Vorlage mit allen Makros und genau diesen Platzhaltern. */
private fun templateWith(placeholders: List<TemplateValue>) = macros() + "\n" + placeholders.joinToString("\n") { it.placeholder }

/** Eine Vorlage mit allen Pflicht-Platzhaltern, die bei den Makros abweicht. */
private fun templateWithout(missing: TemplateMacro? = null, wrongArity: TemplateMacro? = null) =
    macros(missing, wrongArity) + "\n" + required.joinToString("\n") { it.placeholder }

// Die Zeichen der Währungen, ohne die keine Sprachdatei angenommen wird (#409),
// bis auf die genannte.
private fun currencySymbols(missing: Currency? = null) =
    Currency.entries
        .filterNot { it == missing }
        .joinToString("") { "\\newcommand{\\zeichen${it.name}}{}\n" }

/**
 * Baut eine Vorlage mit einer Sprachdatei, die außer den Zeichen der
 * Währungen nur [languageFile] definiert – die Makros stehen in diesen Tests
 * in der Vorlage, bis auf die, die eigens der Sprachdatei gelten.
 */
private fun templateOf(source: String, languageFile: String = ""): Template = Template.of(source, currencySymbols() + languageFile)

/**
 * Hängt [text] an eine sonst vollständige Vorlage, füllt sie und liefert nur,
 * was aus [text] geworden ist. Der Rumpf davor ist dabei immer derselbe, solange
 * [values] keinen Pflicht-Platzhalter belegt.
 */
private fun filled(text: String, values: Map<TemplateValue, Latex> = emptyMap()): String {
    val body = templateWith(required) + "\n"
    return templateOf(body + text).fillIn(empty + values).removePrefix(templateOf(body).fillIn(empty))
}

/**
 * Die Vorlage als Modul (#201): Bau aus Quelltext, füllen, fragen. Alle Tests
 * gehen durch dieselbe Tür wie der Betrieb – eine halb geprüfte Vorlage gibt
 * es nicht, auch nicht im Test. Deshalb sind die kaputten Vorlagen hier in
 * allem vollständig außer dem, was der Test prüft.
 *
 * Keine dieser Prüfungen kann eine Rechnungsdatei auslösen – sie schlagen zu,
 * wenn Vorlage und Renderer auseinanderlaufen. Ohne diese Tests wäre die
 * Zusicherung eingebaut, aber unbelegt.
 */
class TemplateTest : FunSpec({

    val complete = templateOf(templateWith(required))

    test("für jedes Design lässt sich die Vorlage in jeder Sprache bauen") {
        // Der Positivfall, damit die Prüfungen nicht nur beim Kaputten greifen:
        // Jede echte Vorlage kennt mit jeder Sprachdatei jedes Makro und setzt
        // jeden Platzhalter, den sie setzen muss.
        Design.entries.forEach { design ->
            Language.entries.forEach { language ->
                withClue("$design $language") { Template.of(design, language) }
            }
        }
    }

    test("alle Sprachdateien eines Designs definieren dieselben Makros (#377)") {
        // Sonst fehlte ein Wort erst LuaLaTeX auf, und nur in der Sprache, in
        // der es fehlt – oder stünde als Wort einer Sprache in der anderen.
        // Gezählt wird \newcommand; eine Hilfe mit @ im Namen gehört allein
        // ihrer Datei.
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
            // Sonst stünde ein Betrag in dieser Währung still ohne Zeichen da.
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
        // Dieselbe Sorte Prüfung wie bei den Platzhaltern (#30), eine Ebene
        // tiefer: Ein vertippter Makroname übersetzt klaglos und fiele sonst
        // erst LuaLaTeX auf.

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
            // Ein \newcommand hinter % definiert für TeX nichts.
            val template =
                templateWithout(missing = TemplateMacro.POSITIONSZEILE) +
                    "\n% \\newcommand{\\positionszeile}[${TemplateMacro.POSITIONSZEILE.arity}]{}"

            val error = shouldThrow<IllegalStateException> { templateOf(template) }

            error.message!! shouldContain "positionszeile"
        }
    }

    context("Welche Platzhalter jede Vorlage setzen muss") {
        // Die Gegenrichtung zum Füllen, aber nur für die Platzhalter, deren
        // Inhalt an Rechnungsart oder Rechnung hängt (#188).

        test("fehlt einer, den keine Vorlage weglassen darf, wird er benannt") {
            // Der teure Fall: Ein Design ohne <<SKONTO>> ließe das Skonto
            // weg, und keines der Beispiele hat eines.
            val error =
                shouldThrow<IllegalStateException> {
                    templateOf(templateWith(required - TemplateValue.SKONTO))
                }

            error.message!! shouldContain "<<SKONTO>>"
        }

        test("einer, der nur in einer Kommentarzeile steht, fehlt ebenso") {
            // Der Fall aus 553ff49: Gesetzt wird die Vorlage ohne
            // Kommentarzeilen, also zählen sie auch bei der Prüfung nicht.
            val error =
                shouldThrow<IllegalStateException> {
                    templateOf(templateWith(required - TemplateValue.SKONTO) + "\n% <<SKONTO>>")
                }

            error.message!! shouldContain "<<SKONTO>>"
        }

        test("aus einer Gruppe genügt einer, gleich welcher") {
            // Das Zahlungsziel als Datum im Kopf oder als Frist unter den
            // Summen (#211).
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
            // "Steuernummer oder USt-IdNr." lässt sich als Kennzeichen nicht
            // ausdrücken; das prüft das Pflichtangaben-Netz am fertigen PDF.
            templateOf(templateWith(required))
        }

        test("einen Schalter darf sie ebenso weglassen, und die Einleitung auch") {
            // Ein Schalter ist kein Inhalt: Er sagt nicht, was dasteht, sondern
            // ob die Vorlage ein Mittel einsetzt (#248). Die Einleitung darf ein
            // Design weglassen (#209). Unter Pflicht müsste ein drittes Design
            // sie leer aufnehmen (#248).
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
            // Der teure Fall: Ohne die Prüfung stünde "<<SUMMEN>>" gesetzt im
            // PDF. LuaLaTeX übersetzt das klaglos, und veraPDF bestätigt ein
            // einwandfreies PDF/A – es ist ja eines.
            val error = shouldThrow<IllegalStateException> { complete.fillIn(empty - TemplateValue.SUMMEN) }

            error.message!! shouldContain "<<SUMMEN>>"
            error.message!! shouldContain "keinen Wert"
        }

        test("ein Platzhalter, den der Renderer nicht kennt, bricht ebenfalls ab") {
            // Ein vertippter Name in der Vorlage: Im Renderer übersetzt er nicht
            // mehr, seit die Schlüssel TemplateValue sind – in der Vorlage schon.
            val error = shouldThrow<IllegalStateException> { filled("<<FRIMA>>") }

            error.message!! shouldContain "<<FRIMA>>"
        }

        test("ein Wert ohne Platzhalter ist erlaubt") {
            // Ein Design muss nicht jede Angabe setzen: helsinki setzt weder
            // Webseite noch Steuernummer (#188).
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

        // Die Werte stammen aus den Eingabedaten, und ein Kundenname darf
        // spitze Klammern enthalten. Eingesetzter Text ist Inhalt, nicht Vorlage.
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
            // Beim Ersetzen per regulärem Ausdruck deuten manche Schnittstellen
            // "\" und "$1" im Ersatztext als Rückverweis – LaTeX besteht aus beidem.
            filled("<<FIRMA>>", mapOf(TemplateValue.FIRMA to Latex.raw("\\textbf{\$1}"))) shouldBe "\\textbf{\$1}"
        }
    }

    context("Kein Wert enthält ein rohes Zeilenende") {
        test("ein rohes Zeilenende in einem Wert bricht ab und nennt den Platzhalter") {
            // Der Fall aus #147: Hinter einem rohen \\ liest LaTeX eine Zeile,
            // die mit "[" beginnt, als Abstandsangabe.
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
            // escapeLatex macht aus jedem einzelnen \textbackslash{} – deshalb
            // kann keine Rechnungsdatei die Prüfung auslösen.
            complete.fillIn(empty + (TemplateValue.SCHLUSSSATZ to "Ablage unter \\\\server\\rechnungen".escapeLatex()))
        }
    }

    context("Die Kommentarzeilen der Vorlage") {
        // #149: Die Kommentare erklären die Vorlage, im erzeugten .tex liest
        // sie niemand – und eine geänderte Kommentarzeile soll keinen Test rot
        // machen.

        test("eine ganze Kommentarzeile verschwindet samt Zeilenende, auch eingerückt") {
            filled("a\n%% Kommentar\n  % eingerückt\nb\n") shouldBe "a\nb\n"
        }

        test("ein % hinter Code bleibt stehen") {
            // Am Zeilenende einer Makrodefinition unterdrückt es den
            // Zwischenraum; gestrichen, änderte sich das Satzbild.
            val text = "\\newcommand{\\x}[1]{%\n  #1}%\n\\usepackage{booktabs}  %% Hinweis\n"

            filled(text) shouldBe text
        }

        test("ein maskiertes Prozentzeichen am Zeilenanfang ist kein Kommentar") {
            filled("\\% Rabatt\n") shouldBe "\\% Rabatt\n"
        }
    }
})
