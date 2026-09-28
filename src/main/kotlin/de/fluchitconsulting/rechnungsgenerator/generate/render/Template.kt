package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.model.Design
import de.fluchitconsulting.rechnungsgenerator.core.values.Currency
import de.fluchitconsulting.rechnungsgenerator.core.values.Language

// Ein Platzhalter der Vorlage: <<GROSSGESCHRIEBEN>>.
internal val PLACEHOLDER = Regex("<<([A-Z_]+)>>")

/**
 * Eine geprüfte Vorlage: ohne Kommentarzeilen, mit der [Foundation] als erstem
 * Paket, mit ihrer Sprachdatei, mit jedem Makro, das der Renderer aufruft, und
 * jedem Platzhalter, den keine Vorlage weglassen darf (#201).
 *
 * Die Sprachdatei gehört zur Vorlage und nicht zur Rechnung (#377): Sie
 * enthält die Wörter eines Designs in einer Sprache und die Schreibweise der
 * Zahlen, als Makros. Geprüft wird sie zusammen mit der Vorlage – ein Makro,
 * das der Renderer aufruft, darf in der einen oder der anderen stehen, wie
 * `\betrag` in der Sprachdatei.
 *
 * Derselbe Zug wie bei der `AcceptedInvoice`: Was ungeprüft ist, lässt sich
 * nicht bauen. Der Konstruktor ist privat, gebaut wird über [of], und dort
 * laufen Streichen und Prüfen in der einzigen Reihenfolge, die stimmt – wer
 * sie einzeln aufriefe, müsste sie selbst kennen.
 *
 * Der Bau aus Quelltext gehört zum Interface und ist kein Testfenster: Die
 * Tests mit kaputten Vorlagen gehen durch dieselbe Tür wie der Betrieb.
 *
 * Jede Prüfung hier ist [check] und keine Ausnahme für den Benutzer: Keine
 * Rechnungsdatei kann sie auslösen. Laufen Vorlage und Renderer auseinander,
 * ist das ein Fehler im Programm.
 */
class Template private constructor(
    private val source: String,
    private val languageFile: String,
) {
    /**
     * Ob die Vorlage den Platzhalter setzt. Ein Platzhalter, der nur in einer
     * Kommentarzeile steht, zählt nicht – die ist schon gestrichen.
     */
    fun uses(value: TemplateValue): Boolean = value.placeholder in source

    /**
     * Die Dateien, die jede Vorlage neben ihrer `.tex` braucht: die
     * [Foundation] und die Sprachdatei. Dass die Vorlage beide lädt, prüft der
     * Bau.
     *
     * Die Sprachdatei geht mit ihren Kommentaren hinaus, wie die Grundlage: Sie
     * steht in keinem Volltext, und TeX überliest sie.
     */
    val files: Map<String, ByteArray>
        get() =
            mapOf(
                Foundation.FILE_NAME to Foundation.BUILT_IN.content,
                LANGUAGE_FILE_NAME to languageFile.toByteArray(),
            )

    /**
     * Setzt die Werte in die Vorlage – und prüft vorher, dass jeder Platzhalter
     * der Vorlage einen Wert bekommt und kein Wert ein rohes `\\` enthält.
     *
     * Hier – und nur hier – wird aus Fragmenten wieder Text. Ersetzt wird in
     * einem Durchgang über die Vorlage: Die Werte stammen aus den Eingabedaten,
     * und ein Kundenname mit `<<POSITIONEN>>` darin ist Inhalt. Nacheinander
     * ersetzt, fände der nächste Schritt ihn im schon eingesetzten Text wieder.
     * Die Variante von `Regex.replace` mit Lambda setzt dessen Ergebnis
     * wörtlich ein; `\` und `$1` sind dort keine Rückverweise.
     *
     * Die Prüfung der Platzhalter fängt einen Fehler ab, den sonst niemand
     * bemerkt: Bekommt die Vorlage einen Platzhalter ohne Gegenstück, dann steht
     * die Zeichenkette `<<XYZ>>` am Ende **gesetzt im PDF**. LuaLaTeX übersetzt
     * sie klaglos, und veraPDF bestätigt ein einwandfreies PDF/A – es ist ja
     * eines (#30).
     *
     * Nur diese eine Richtung (#188). Die andere – jeder Wert hat einen
     * Platzhalter – finge einen vertippten Schlüssel nicht besser als die
     * erste, und weil die Schlüssel [TemplateValue] sind, übersetzt ein
     * vertippter gar nicht erst. Verbieten würde sie aber, dass ein Design
     * einen Wert nicht setzt. Welche Platzhalter jede Vorlage setzen muss,
     * prüft schon der Bau.
     */
    fun fillIn(values: Map<TemplateValue, Latex>): String {
        checkLineBreaks(values)

        // Die Werte nach dem Namen ihres Platzhalters, so wie die Vorlage ihn schreibt.
        val byName = values.mapKeys { (key, _) -> key.name }

        // Geprüft wird die Vorlage, nicht das Ergebnis: Im Ergebnis kann ein
        // Platzhalter als Inhalt stehen.
        PLACEHOLDER.findAll(source).firstOrNull { it.groupValues[1] !in byName }?.let {
            error("Die Vorlage enthält ${it.value}, aber der Renderer liefert dafür keinen Wert.")
        }

        return PLACEHOLDER.replace(source) { byName.getValue(it.groupValues[1]).tex }
    }

    /**
     * Prüft, dass kein Wert ein rohes `\\` enthält: Zeilenwechsel und
     * Zeilenenden stehen in der Vorlage, der Renderer ruft sie als Makro auf
     * (#147). Geprüft wird beim Füllen, weil dort aus Fragmenten Text wird.
     *
     * Geprüft wird das Ergebnis, nicht die Schreibweise – ob ein `\\` über
     * [Latex.raw], einen Trenner oder eine Konstante hineinkam, ist gleich. Aus
     * den Eingabedaten kommt keines: [escapeLatex] macht aus jedem Backslash
     * `\textbackslash{}`. Ohne die Prüfung fiele ein neues rohes `\\` nur als
     * Zeile in einer Vergleichsdatei auf, und dort sieht es richtig aus. Der
     * Fehler dahinter – eine Zeile, die mit `[` beginnt, bricht den Satz ab –
     * zeigte sich erst bei der Rechnung mit dieser Zeile.
     */
    private fun checkLineBreaks(values: Map<TemplateValue, Latex>) {
        values.forEach { (key, value) ->
            check("\\\\" !in value.tex) {
                "Der Renderer liefert für ${key.placeholder} ein rohes \\\\. Zeilenwechsel stehen in der Vorlage, " +
                    "der Renderer ruft sie als Makro aus TemplateMacro auf."
            }
        }
    }

    companion object {
        /** Der Name, unter dem die Sprachdatei neben der `.tex` liegt. */
        const val LANGUAGE_FILE_NAME = "sprache.tex"

        /** Die Zeile, mit der eine Vorlage ihre Sprachdatei lädt. */
        const val LANGUAGE_FILE_USAGE = "\\input{sprache}"

        // Eine Makro-Definition der Vorlage: \newcommand{\name}[stelligkeit].
        private val NEWCOMMAND = Regex("""\\newcommand\{\\([a-zA-Z]+)}(?:\[(\d+)])?""")

        /**
         * Die Vorlage eines Designs in einer Sprache. Jedes Design ist eine
         * Datei unter `templates/`, benannt wie in der YAML, und daneben liegt
         * je Sprache seine Sprachdatei, benannt nach Design und Tag:
         * `helsinki-en-gb.tex`.
         */
        fun of(design: Design, language: Language): Template =
            of(
                resource("/templates/${design.label}.tex", "Vorlage"),
                resource("/templates/${languageFileOf(design, language)}", "Sprachdatei"),
            )

        /** Der Name der Sprachdatei eines Designs unter `templates/`. */
        fun languageFileOf(design: Design, language: Language): String = "${design.label}-${language.tag.lowercase()}.tex"

        private fun resource(path: String, what: String): String =
            checkNotNull(Template::class.java.getResource(path)) { "$what $path fehlt" }.readText()

        /**
         * Baut eine Vorlage aus Quelltext und dem ihrer Sprachdatei: erst die
         * Kommentarzeilen streichen, dann prüfen. Geprüft wird also nur, was
         * TeX liest – ein auskommentiertes `\newcommand` definiert nichts, und
         * ein Platzhalter, der nur noch in einem Kommentar steht, setzt nichts.
         */
        fun of(source: String, languageFile: String): Template {
            val withoutComments = withoutCommentLines(source)
            val languageWithoutComments = withoutCommentLines(languageFile)
            checkFoundation(withoutComments)
            checkLanguageFile(withoutComments, languageWithoutComments)
            checkMacros(withoutComments + "\n" + languageWithoutComments)
            checkPlaceholders(withoutComments)
            return Template(withoutComments, languageFile)
        }

        /**
         * Prüft, dass die Vorlage ihre Sprachdatei lädt, dass die keinen
         * Platzhalter enthält und für jede [Currency] ein Zeichen kennt.
         *
         * Ohne die erste Prüfung fehlten die Wörter erst LuaLaTeX auf, als
         * unbekannte Makros. Ohne die zweite stünde ein Platzhalter der
         * Sprachdatei wörtlich im PDF: Gefüllt wird nur die Vorlage, die
         * Sprachdatei geht als Datei daneben, wie sie ist.
         *
         * Ohne die dritte stünde ein Betrag in dieser Währung ohne Zeichen da:
         * Die Sprachdatei schlägt es mit `\csname` nach, und ein Name ohne
         * Definition ist dort `\relax` und setzt still nichts (#409).
         */
        private fun checkLanguageFile(source: String, languageFile: String) {
            check(source.lines().any { it.trim() == LANGUAGE_FILE_USAGE }) {
                "Die Vorlage lädt ihre Sprachdatei nicht. Sie braucht eine Zeile $LANGUAGE_FILE_USAGE."
            }
            PLACEHOLDER.find(languageFile)?.let {
                error("Die Sprachdatei enthält ${it.value}. Werte der Rechnung setzt die Vorlage, die Sprachdatei nur Wörter.")
            }
            val defined = NEWCOMMAND.findAll(languageFile).map { it.groupValues[1] }.toSet()
            Currency.entries.forEach { currency ->
                check(currencySymbolOf(currency) in defined) {
                    "Die Sprachdatei kennt kein Zeichen für $currency. Sie braucht \\newcommand{\\${currencySymbolOf(currency)}}."
                }
            }
        }

        /** Das Makro der Sprachdatei, das das Zeichen einer Währung setzt: `\zeichenUSD`. */
        private fun currencySymbolOf(currency: Currency): String = "zeichen${currency.name}"

        /**
         * Prüft, dass die Vorlage die [Foundation] lädt, und zwar in der Zeile
         * direkt nach `\documentclass`: Die Grundlage lädt pdfx, und das muss
         * vor allem anderen stehen.
         *
         * Ohne die Prüfung übersetzte eine Vorlage ohne Grundlage weiter, und
         * nur manches fiele auf – `\ungeteilt` als unbekanntes Makro. Das
         * Rundungsverbot von siunitx fehlte dagegen einfach (ADR-0022).
         */
        private fun checkFoundation(source: String) {
            val afterClass =
                source
                    .lines()
                    .dropWhile { !it.startsWith("\\documentclass") }
                    .drop(1)
                    .firstOrNull { it.isNotBlank() }
            check(afterClass?.trim() == Foundation.USAGE) {
                "Die Vorlage lädt die Grundlage nicht direkt nach \\documentclass. Die erste Zeile danach muss " +
                    "${Foundation.USAGE} sein, denn die Grundlage lädt pdfx."
            }
        }

        /**
         * Prüft, dass Vorlage und Sprachdatei zusammen jedes Makro kennen, das
         * der Renderer aufruft – und zwar mit derselben Stelligkeit.
         *
         * Dieselbe Sorte Fehler wie beim Platzhalter (#30), nur eine Ebene tiefer:
         * Ein vertippter Makroname übersetzt in Kotlin klaglos und fällt erst
         * LuaLaTeX auf, also im Integrationstest, und dort als Fehlermeldung von
         * TeX. Hier fällt er beim Bau auf, mit Namen.
         */
        private fun checkMacros(source: String) {
            val defined =
                NEWCOMMAND
                    .findAll(source)
                    .associate { it.groupValues[1] to (it.groupValues[2].toIntOrNull() ?: 0) }

            TemplateMacro.entries.forEach { macro ->
                val arity =
                    checkNotNull(defined[macro.macroName]) {
                        "Der Renderer ruft \\${macro.macroName} auf, aber die Vorlage definiert es nicht."
                    }
                check(arity == macro.arity) {
                    "\\${macro.macroName} nimmt in der Vorlage $arity Argumente, im Renderer ${macro.arity}."
                }
            }
        }

        /**
         * Prüft, dass die Vorlage jeden Platzhalter enthält, den keine Vorlage
         * weglassen darf ([TemplateValue.required]) – die, deren Inhalt an
         * Rechnungsart oder Rechnung hängt.
         *
         * Die Gegenrichtung zu [fillIn], aber nur für diese Platzhalter: Ein Design
         * darf eine Angabe der Stammdaten weglassen, `helsinki` etwa setzt weder
         * Webseite noch Steuernummer. Ließe es den Pflichtsatz der Art weg, fehlte
         * er dagegen genau auf den Rechnungen, die keines der Beispiele zeigt.
         * Das Pflichtangaben-Netz sieht nur die Beispieldateien, diese Prüfung
         * jede Vorlage beim Bau; ob die Angaben der Stammdaten dastehen, prüft
         * das Netz am fertigen PDF.
         *
         * Dazu einen aus [TemplateValue.ZAHLUNGSANGABE]: Dass die Vorlage
         * über die Zahlung etwas sagt, verlangt der Bau, welches von beiden
         * entscheidet das Design.
         */
        private fun checkPlaceholders(source: String) {
            TemplateValue.entries.filter { it.required }.forEach { key ->
                check(key.placeholder in source) {
                    "Die Vorlage setzt ${key.placeholder} nicht, und das darf keine Vorlage weglassen."
                }
            }
            check(TemplateValue.ZAHLUNGSANGABE.any { it.placeholder in source }) {
                "Die Vorlage sagt nichts über die Zahlung: Sie setzt keinen von " +
                    TemplateValue.ZAHLUNGSANGABE.joinToString(", ") { it.placeholder } +
                    ", und einen davon muss jede Vorlage setzen."
            }
        }
    }
}

/**
 * Streicht die Kommentarzeilen – jede Zeile, deren erstes Zeichen nach
 * Leerraum ein `%` ist, samt ihrem Zeilenende (#149). Die [Foundation] wird
 * nur so geprüft und behält ihre Kommentare, sie steht in keiner
 * Vergleichsdatei.
 *
 * Die Kommentare erklären die Vorlage, im erzeugten `.tex` liest sie
 * niemand. Blieben sie stehen, machte eine geänderte Kommentarzeile jede
 * Vergleichsdatei rot, und Inhaltstests fänden Wörter aus den Kommentaren.
 *
 * Für TeX ändert sich nichts: Eine Kommentarzeile verschluckt ihr eigenes
 * Zeilenende, und Leerraum am Zeilenanfang überliest TeX ohnehin.
 * Nachgestellte Kommentare bleiben dagegen stehen – ein `%` am Zeilenende
 * unterdrückt in einer Makrodefinition den Zwischenraum, und `\%` ist gar
 * keiner. Das zu unterscheiden, bräuchte einen LaTeX-Parser. Aus demselben
 * Grund bleiben die Leerzeilen, die zwischen gestrichenen Blöcken
 * zusammenrücken: Zwei Leerzeilen sind für TeX dasselbe wie eine, aber das
 * wäre eine zweite Umformung mit eigener Begründung.
 */
internal fun withoutCommentLines(source: String): String =
    source
        .lines()
        .filterNot { it.trimStart().startsWith("%") }
        .joinToString("\n")
