package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.values.Design
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

/** Die Definitionen aller Makros – bis auf die genannte Abweichung. */
private fun macros(missing: TemplateMacro? = null, wrongArity: TemplateMacro? = null) =
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

/**
 * Hängt [text] an eine sonst vollständige Vorlage, füllt sie und liefert nur,
 * was aus [text] geworden ist. Der Rumpf davor ist dabei immer derselbe, solange
 * [values] keinen Pflicht-Platzhalter belegt.
 */
private fun filled(text: String, values: Map<TemplateValue, Latex> = emptyMap()): String {
    val body = templateWith(required) + "\n"
    return Template.of(body + text).fillIn(empty + values).removePrefix(Template.of(body).fillIn(empty))
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

    val complete = Template.of(templateWith(required))

    test("für jedes Design lässt sich die Vorlage bauen") {
        // Der Positivfall, damit die Prüfungen nicht nur beim Kaputten greifen:
        // Jede echte Vorlage kennt jedes Makro und setzt jeden Platzhalter,
        // den sie setzen muss.
        Design.entries.forEach { design ->
            withClue(design) { Template.of(design) }
        }
    }

    context("Vorlage und Renderer müssen dieselben Makros kennen") {
        // Dieselbe Sorte Prüfung wie bei den Platzhaltern (#30), eine Ebene
        // tiefer: Ein vertippter Makroname übersetzt klaglos und fiele sonst
        // erst LuaLaTeX auf.

        test("ein Makro, das die Vorlage nicht kennt, wird benannt") {
            val error =
                shouldThrow<IllegalStateException> {
                    Template.of(templateWithout(missing = TemplateMacro.POSITIONSZEILE))
                }

            error.message!! shouldContain "positionszeile"
        }

        test("eine falsche Stelligkeit fällt ebenfalls auf") {
            val error =
                shouldThrow<IllegalStateException> {
                    Template.of(templateWithout(wrongArity = TemplateMacro.POSITIONSZEILE))
                }

            error.message!! shouldContain "positionszeile"
            error.message!! shouldContain "7"
        }

        test("ein auskommentiertes Makro gilt nicht als definiert") {
            // Ein \newcommand hinter % definiert für TeX nichts.
            val template =
                templateWithout(missing = TemplateMacro.POSITIONSZEILE) +
                    "\n% \\newcommand{\\positionszeile}[${TemplateMacro.POSITIONSZEILE.arity}]{}"

            val error = shouldThrow<IllegalStateException> { Template.of(template) }

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
                    Template.of(templateWith(required - TemplateValue.SKONTO))
                }

            error.message!! shouldContain "<<SKONTO>>"
        }

        test("einer, der nur in einer Kommentarzeile steht, fehlt ebenso") {
            // Der Fall aus 553ff49: Geprüft wurde die Vorlage mit
            // Kommentarzeilen, gesetzt wird sie ohne.
            val error =
                shouldThrow<IllegalStateException> {
                    Template.of(templateWith(required - TemplateValue.SKONTO) + "\n% <<SKONTO>>")
                }

            error.message!! shouldContain "<<SKONTO>>"
        }

        test("aus einer Gruppe genügt einer, gleich welcher") {
            // Das Zahlungsziel als Datum im Kopf oder als Frist unter den
            // Summen (#211).
            val withoutPaymentInfo = required - TemplateValue.ZAHLUNGSZIEL - TemplateValue.ZAHLUNGSFRIST

            Template.of(templateWith(withoutPaymentInfo + TemplateValue.ZAHLUNGSZIEL))
            Template.of(templateWith(withoutPaymentInfo + TemplateValue.ZAHLUNGSFRIST))
        }

        test("fehlt eine Gruppe ganz, werden alle ihre Platzhalter genannt") {
            val error =
                shouldThrow<IllegalStateException> {
                    Template.of(templateWith(required - TemplateValue.ZAHLUNGSZIEL - TemplateValue.ZAHLUNGSFRIST))
                }

            error.message!! shouldContain "<<ZAHLUNGSZIEL>>"
            error.message!! shouldContain "<<ZAHLUNGSFRIST>>"
        }

        test("die Angaben der Stammdaten darf eine Vorlage weglassen") {
            // "Steuernummer oder USt-IdNr." lässt sich als Kennzeichen nicht
            // ausdrücken; das prüft das Pflichtangaben-Netz am fertigen PDF.
            Template.of(templateWith(required))
        }

        test("einen Schalter darf sie ebenso weglassen, und die Einleitung auch") {
            // Ein Schalter ist kein Inhalt: Er sagt nicht, was dasteht, sondern
            // ob die Vorlage ein Mittel einsetzt (#248). Die Einleitung darf ein
            // Design weglassen (#209). Unter Pflicht müsste ein drittes Design
            // sie leer aufnehmen (#248).
            val template = Template.of(templateWith(required))

            template.uses(TemplateValue.LOGO) shouldBe false
            template.uses(TemplateValue.RABATTSPALTE) shouldBe false
            template.uses(TemplateValue.EINLEITUNG) shouldBe false
        }
    }

    context("Ob die Vorlage einen Platzhalter setzt") {
        test("steht er in der Vorlage, setzt sie ihn") {
            Template.of(templateWith(required) + "\n<<LOGO>>").uses(TemplateValue.LOGO) shouldBe true
        }

        test("steht er nur in einer Kommentarzeile, setzt sie ihn nicht") {
            Template.of(templateWith(required) + "\n% <<LOGO>>").uses(TemplateValue.LOGO) shouldBe false
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
