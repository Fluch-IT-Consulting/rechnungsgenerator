package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.values.Design

// Ein Platzhalter der Vorlage: <<GROSSGESCHRIEBEN>>.
// Die Klammer um den Namen ist eine Gruppe: groupValues[1] liefert den
// Schlüssel ohne die spitzen Klammern.
private val PLACEHOLDER = Regex("<<([A-Z_]+)>>")

/**
 * Eine geprüfte Vorlage: ohne Kommentarzeilen, mit jedem Makro, das der
 * Renderer aufruft, und jedem Platzhalter, den keine Vorlage weglassen darf
 * (#201).
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
) {
    /**
     * Ob die Vorlage den Platzhalter setzt. Ein Platzhalter, der nur in einer
     * Kommentarzeile steht, zählt nicht – die ist schon gestrichen.
     */
    fun uses(value: TemplateValue): Boolean = value.placeholder in source

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
     * Nur diese eine Richtung (#188). Bis dahin wurde auch verlangt, dass jeder
     * Wert einen Platzhalter hat. Einen vertippten Schlüssel fing das nicht
     * besser als die erste Richtung, und seit die Schlüssel [TemplateValue]
     * sind, übersetzt ein vertippter gar nicht mehr. Verboten hätte die zweite
     * Richtung aber, dass ein Design einen Wert nicht setzt. Welche Platzhalter
     * jede Vorlage setzen muss, prüft schon der Bau.
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
        // `(key, value)` zerlegt jeden Eintrag der Map in seine zwei Teile
        // ("destructuring"), statt it.key und it.value zu schreiben.
        values.forEach { (key, value) ->
            check("\\\\" !in value.tex) {
                "Der Renderer liefert für ${key.placeholder} ein rohes \\\\. Zeilenwechsel stehen in der Vorlage, " +
                    "der Renderer ruft sie als Makro aus TemplateMacro auf."
            }
        }
    }

    // Die Fabriken stehen im Companion, weil nur er den privaten Konstruktor
    // rufen darf.
    companion object {
        // Eine Makro-Definition der Vorlage: \newcommand{\name}[stelligkeit].
        private val NEWCOMMAND = Regex("""\\newcommand\{\\([a-zA-Z]+)}(?:\[(\d+)])?""")

        /**
         * Die Vorlage eines Designs. Jedes Design ist eine Datei unter
         * `templates/`, benannt wie in der YAML.
         */
        fun of(design: Design): Template {
            val path = "/templates/${design.label}.tex"
            return of(checkNotNull(Template::class.java.getResource(path)) { "Vorlage $path fehlt" }.readText())
        }

        /**
         * Baut eine Vorlage aus Quelltext: erst die Kommentarzeilen streichen,
         * dann prüfen. Geprüft wird also nur, was TeX liest – ein
         * auskommentiertes `\newcommand` definiert nichts, und ein Platzhalter,
         * der nur noch in einem Kommentar steht, setzt nichts.
         */
        fun of(source: String): Template {
            val withoutComments = withoutCommentLines(source)
            checkMacros(withoutComments)
            checkPlaceholders(withoutComments)
            return Template(withoutComments)
        }

        /**
         * Streicht die Kommentarzeilen der Vorlage – jede Zeile, deren erstes
         * Zeichen nach Leerraum ein `%` ist, samt ihrem Zeilenende (#149).
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
        private fun withoutCommentLines(source: String): String =
            source
                .lines()
                .filterNot { it.trimStart().startsWith("%") }
                .joinToString("\n")

        /**
         * Prüft, dass die Vorlage jedes Makro kennt, das der Renderer aufruft – und
         * zwar mit derselben Stelligkeit.
         *
         * Dieselbe Sorte Fehler wie beim Platzhalter (#30), nur eine Ebene tiefer:
         * Ein vertippter Makroname übersetzt in Kotlin klaglos und fällt erst
         * LuaLaTeX auf, also im Integrationstest, und dort als Fehlermeldung von
         * TeX. Hier fällt er beim Bau auf, mit Namen.
         */
        private fun checkMacros(source: String) {
            val definiert =
                NEWCOMMAND
                    .findAll(source)
                    .associate { it.groupValues[1] to (it.groupValues[2].toIntOrNull() ?: 0) }

            TemplateMacro.entries.forEach { macro ->
                val stelligkeit =
                    checkNotNull(definiert[macro.macroName]) {
                        "Der Renderer ruft \\${macro.macroName} auf, aber die Vorlage definiert es nicht."
                    }
                check(stelligkeit == macro.arity) {
                    "\\${macro.macroName} nimmt in der Vorlage $stelligkeit Argumente, im Renderer ${macro.arity}."
                }
            }
        }

        /**
         * Prüft, dass die Vorlage jeden Platzhalter enthält, den keine Vorlage
         * weglassen darf ([TemplateValue.required]) – die, deren Inhalt an
         * Rechnungsart oder Rechnung hängt.
         *
         * Die Gegenrichtung zu [fillIn], aber nur für diese Platzhalter: Ein Design
         * darf eine Angabe der Stammdaten weglassen, `balken` etwa setzt weder
         * Webseite noch Steuernummer. Ließe es den Pflichtsatz der Art weg, fehlte
         * er dagegen genau auf den Rechnungen, die keines der Beispiele zeigt.
         * Diese Prüfung deckt ab, was kein Beispiel enthält (Skonto,
         * Befreiungsgrund); ob die Angaben der Stammdaten dastehen, prüft das
         * Pflichtangaben-Netz am fertigen PDF.
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
