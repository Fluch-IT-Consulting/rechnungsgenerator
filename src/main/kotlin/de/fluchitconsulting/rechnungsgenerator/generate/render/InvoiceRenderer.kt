package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.rules.AcceptedInvoice

/**
 * Setzt die LaTeX-Vorlage mit den Werten einer Rechnung zusammen.
 *
 * Der Renderer rechnet nichts – er bekommt die fertige
 * [de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice] und
 * formatiert nur. Umgekehrt kennt der Calculator kein LaTeX. Diese Trennung ist
 * der Grund, warum die Steuerlogik ohne einen einzigen LaTeX-Lauf testbar ist.
 *
 * Und er bekommt sie nur angenommen, als [AcceptedInvoice] (ADR-0007): Eine
 * Rechnung mit Abbruch lässt sich nicht setzen, statt bloß nicht gesetzt zu
 * werden, weil beide Kommandos daran denken.
 *
 * Innen wird durchgehend mit [Latex] gearbeitet, nicht mit Zeichenketten: Was
 * hier zusammengesetzt wird, hat die Maskierung schon hinter sich. Ausgepackt
 * wird genau einmal, beim Einsetzen in die Vorlage.
 *
 * Die Blöcke stehen je Gruppe in einer eigenen Datei – [SellerBlocks],
 * [MetadataBlocks], [ItemBlocks], [DeductionBlocks], die geteilten Bausteine
 * in `RenderFragments.kt`. Hier bleiben der Einstieg und der Mechanismus:
 * Platzhalter füllen und beide Seiten der Vorlage prüfen. Der Schnitt folgt
 * den Blöcken und nicht den Rechnungsarten (Notiz vom 14.09.2026): Designs
 * vervielfachen sich, die Arten bleiben die geschlossene Menge, die jeder
 * Renderer vollständig beantwortet.
 */
object InvoiceRenderer {
    private const val TEMPLATE_PATH = "/templates/invoice.tex"

    // Eine Makro-Definition der Vorlage: \newcommand{\name}[stelligkeit].
    private val NEWCOMMAND = Regex("""\\newcommand\{\\([a-zA-Z]+)}(?:\[(\d+)])?""")

    fun render(acceptedInvoice: AcceptedInvoice): String {
        val calculatedInvoice = acceptedInvoice.calculated
        val invoice = calculatedInvoice.invoice
        val config = calculatedInvoice.config
        val template =
            withoutCommentLines(
                checkNotNull(javaClass.getResource(TEMPLATE_PATH)) { "Vorlage $TEMPLATE_PATH fehlt" }.readText(),
            )

        val values =
            mapOf(
                "FIRMA" to config.companyName.escapeLatex(),
                "INHABER" to config.ownerName.escapeLatex(),
                "KOPF_PERSON" to SellerBlocks.letterheadPerson(config),
                "ABSENDER_KONTAKT" to SellerBlocks.senderContact(config),
                "EMPFAENGER" to MetadataBlocks.recipient(calculatedInvoice),
                "EMPFAENGER_RECHTS" to MetadataBlocks.recipientSideBlock(acceptedInvoice),
                "DOKUMENTART" to invoice.type.documentName.escapeLatex(),
                "RECHNUNGSNUMMER" to invoice.number.escapeLatex(),
                "RECHNUNGSDATUM" to GermanFormat.date(invoice.issueDate),
                "POSITIONEN" to ItemBlocks.lineItems(calculatedInvoice),
                "SUMMEN" to ItemBlocks.totalsBlock(calculatedInvoice),
                "ABSETZUNG" to DeductionBlocks.deductionBlock(acceptedInvoice),
                "ARTHINWEIS" to ItemBlocks.typeNote(acceptedInvoice),
                "STEUERHINWEISE" to ItemBlocks.taxNotes(calculatedInvoice),
                "SCHLUSSSATZ" to (invoice.note ?: config.closingLine).escapeLatex(),
                "BANKVERBINDUNG" to SellerBlocks.bankDetails(calculatedInvoice),
                "FUSSZEILE_OBEN" to SellerBlocks.footerCompanyLine(config),
                "FUSSZEILE_UNTEN" to SellerBlocks.footerTaxAndBankLine(config),
            )

        checkMacros(template)
        checkLineBreaks(values)
        return fillIn(template, values)
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
     *
     * Gestrichen wird vor [checkMacros] und [fillIn], damit beide nur sehen,
     * was TeX liest: Ein auskommentiertes `\newcommand` definiert nichts.
     */
    internal fun withoutCommentLines(template: String): String =
        template
            .lines()
            .filterNot { it.trimStart().startsWith("%") }
            .joinToString("\n")

    /**
     * Prüft, dass kein Wert ein rohes `\\` enthält: Zeilenwechsel und
     * Zeilenenden stehen in der Vorlage, der Renderer ruft sie als Makro auf
     * (#147).
     *
     * Geprüft wird das Ergebnis, nicht die Schreibweise – ob ein `\\` über
     * [Latex.raw], einen Trenner oder eine Konstante hineinkam, ist gleich. Aus
     * den Eingabedaten kommt keines: [escapeLatex] macht aus jedem Backslash
     * `\textbackslash{}`. Ohne die Prüfung fiele ein neues rohes `\\` nur als
     * Zeile in einer Vergleichsdatei auf, und dort sieht es richtig aus. Der
     * Fehler dahinter – eine Zeile, die mit `[` beginnt, bricht den Satz ab –
     * zeigte sich erst bei der Rechnung mit dieser Zeile.
     *
     * [check] wie bei [checkMacros]: Keine Rechnungsdatei kann das auslösen.
     */
    internal fun checkLineBreaks(values: Map<String, Latex>) {
        // `(key, value)` zerlegt jeden Eintrag der Map in seine zwei Teile
        // ("destructuring"), statt it.key und it.value zu schreiben.
        values.forEach { (key, value) ->
            check("\\\\" !in value.tex) {
                "Der Renderer liefert für <<$key>> ein rohes \\\\. Zeilenwechsel stehen in der Vorlage, " +
                    "der Renderer ruft sie als Makro aus TemplateMacro auf."
            }
        }
    }

    /**
     * Prüft, dass die Vorlage jedes Makro kennt, das der Renderer aufruft – und
     * zwar mit derselben Stelligkeit.
     *
     * Dieselbe Sorte Fehler wie beim Platzhalter (#30), nur eine Ebene tiefer:
     * Ein vertippter Makroname übersetzt in Kotlin klaglos und fällt erst
     * LuaLaTeX auf, also im Integrationstest, und dort als Fehlermeldung von
     * TeX. Hier fällt er beim Setzen auf, mit Namen.
     *
     * [check] und keine Ausnahme für den Benutzer: Keine Rechnungsdatei kann
     * das auslösen. Laufen Vorlage und Renderer auseinander, ist das ein Fehler
     * im Programm.
     */
    internal fun checkMacros(template: String) {
        val definiert =
            NEWCOMMAND
                .findAll(template)
                .associate { it.groupValues[1] to (it.groupValues[2].toIntOrNull() ?: 0) }

        TemplateMacro.entries.forEach { macro ->
            val stelligkeit = definiert[macro.macroName]
            check(stelligkeit != null) {
                "Der Renderer ruft \\${macro.macroName} auf, aber die Vorlage definiert es nicht."
            }
            check(stelligkeit == macro.arity) {
                "\\${macro.macroName} nimmt in der Vorlage $stelligkeit Argumente, im Renderer ${macro.arity}."
            }
        }
    }

    /**
     * Setzt die Werte in die Vorlage – und prüft, dass beide Seiten
     * zusammenpassen.
     *
     * Hier – und nur hier – wird aus Fragmenten wieder Text. Ersetzt wird in
     * einem Durchgang über die Vorlage: Die Werte stammen aus den Eingabedaten,
     * und ein Kundenname mit `<<POSITIONEN>>` darin ist Inhalt. Nacheinander
     * ersetzt, fände der nächste Schritt ihn im schon eingesetzten Text wieder.
     * Die Variante von `Regex.replace` mit Lambda setzt dessen Ergebnis
     * wörtlich ein; `\` und `$1` sind dort keine Rückverweise.
     *
     * Die zwei Prüfungen drumherum fangen einen Fehler ab, den sonst niemand
     * bemerkt. Verschreibt sich jemand bei einem Schlüssel, oder bekommt die
     * Vorlage einen Platzhalter ohne Gegenstück, dann steht die Zeichenkette
     * `<<XYZ>>` am Ende **gesetzt im PDF**: Der Compiler kennt die Schlüssel
     * nicht, LuaLaTeX übersetzt sie klaglos, und veraPDF bestätigt ein
     * einwandfreies PDF/A – es ist ja eines. Aufgefallen wäre es bisher nur
     * mittelbar, als Unterschied in einer der Vergleichsdateien, und dort als
     * Diff über 216 Zeilen statt als benannte Meldung.
     *
     * [check] und nicht eine Ausnahme für den Benutzer: Keine Rechnungsdatei
     * kann das auslösen. Wenn Vorlage und Renderer auseinanderlaufen, ist das
     * ein Fehler im Programm, so wie die fehlende Vorlage eine Zeile weiter
     * oben.
     */
    internal fun fillIn(template: String, values: Map<String, Latex>): String {
        values.keys.forEach { key ->
            check(template.contains("<<$key>>")) {
                "Der Renderer liefert einen Wert für <<$key>>, aber die Vorlage hat dort keinen Platzhalter."
            }
        }

        // Geprüft wird die Vorlage, nicht das Ergebnis: Im Ergebnis kann ein
        // Platzhalter als Inhalt stehen.
        PLACEHOLDER.findAll(template).firstOrNull { it.groupValues[1] !in values }?.let {
            error("Die Vorlage enthält ${it.value}, aber der Renderer liefert dafür keinen Wert.")
        }

        return PLACEHOLDER.replace(template) { values.getValue(it.groupValues[1]).tex }
    }

    /**
     * Die begleitende .xmpdata-Datei, aus der pdfx die XMP-Metadaten des
     * PDF/A-Dokuments baut. Ohne sie bleiben Titel und Autor im PDF leer –
     * für PDF/A-1b sind gültige Metadaten Pflicht.
     *
     * Bewusst OHNE \Keywords: Die pdfx-Version in TeX Live 2019 schreibt den
     * Eintrag ins Info-Dictionary, aber nicht als pdf:Keywords ins XMP. PDF/A-1b
     * verlangt in Klausel 6.7.3, dass beide übereinstimmen – veraPDF lehnt das
     * Dokument sonst ab. Nachgemessen: mit \Keywords genau ein Verstoß, ohne keiner.
     *
     * Auch hier maskierte Werte: Ein `&` im Firmennamen zerlegte sonst die
     * Metadaten, und ohne gültige Metadaten gibt es kein PDF/A.
     */
    fun renderMetadata(acceptedInvoice: AcceptedInvoice): String {
        val calculatedInvoice = acceptedInvoice.calculated
        val invoice = calculatedInvoice.invoice
        val document = invoice.type.documentName.escapeLatex() + " ".escapeLatex() + invoice.number.escapeLatex()
        return listOf(
            xmpField("Title", document),
            xmpField("Author", calculatedInvoice.config.companyName.escapeLatex()),
            xmpField("Subject", document + " vom ".escapeLatex() + GermanFormat.dateInMetadata(invoice.issueDate)),
            xmpField("Language", "de-DE".escapeLatex()),
        ).joinToLatex("\n").tex
    }

    /**
     * Ein Feld der .xmpdata-Datei: `\Title{…}`. Kein [TemplateMacro]: Diese
     * Felder definiert pdfx und nicht die Vorlage, die Prüfung fände sie dort
     * nicht. Deshalb privat und nur hier – bis #76 war das `Latex.macro` mit
     * einem Namen als Zeichenkette, und darüber lief auch `\mbox`.
     */
    private fun xmpField(name: String, content: Latex): Latex = Latex.raw("\\$name{") + content + Latex.raw("}")
}
