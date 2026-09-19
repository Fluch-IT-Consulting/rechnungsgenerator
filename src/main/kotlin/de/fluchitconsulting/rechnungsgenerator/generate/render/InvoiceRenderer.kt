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
        val template =
            withoutCommentLines(
                checkNotNull(javaClass.getResource(TEMPLATE_PATH)) { "Vorlage $TEMPLATE_PATH fehlt" }.readText(),
            )
        val values = values(acceptedInvoice)

        checkMacros(template)
        checkPlaceholders(template)
        checkLineBreaks(values)
        return fillIn(template, values)
    }

    /**
     * Die Werte aller Platzhalter. Eigens abrufbar, damit ein Test fragen kann,
     * was der Renderer entschieden hat – ob eine Angabe leer ist –, ohne das
     * aus dem gesetzten Text einer bestimmten Vorlage herauszulesen.
     */
    internal fun values(acceptedInvoice: AcceptedInvoice): Map<TemplateValue, Latex> =
        TemplateValue.entries.associateWith { value(it, acceptedInvoice) }

    /**
     * Der Wert eines Platzhalters. Ein `when` über alle [TemplateValue] ohne
     * `else`: Kommt ein Platzhalter dazu, übersetzt diese Stelle erst, wenn
     * entschieden ist, was er enthält.
     *
     * Eine Angabe, die nicht dasteht, ist leer ([Latex.EMPTY]) und nicht
     * abwesend. Der Vorlage ist es gleich, warum: weil die Eingabe sie nicht
     * nennt oder weil ein `when` hier so entschieden hat.
     */
    private fun value(
        key: TemplateValue,
        acceptedInvoice: AcceptedInvoice,
    ): Latex {
        val calculatedInvoice = acceptedInvoice.calculated
        val invoice = calculatedInvoice.invoice
        val config = calculatedInvoice.config
        val address = config.address
        val bank = config.bank
        val taxNumber = config.taxNumber
        // `orEmpty()` macht aus null eine leere Zeichenkette: Eine fehlende
        // Angabe der Stammdaten wird ein leerer Wert.
        return when (key) {
            TemplateValue.FIRMA -> config.companyName.escapeLatex()
            TemplateValue.INHABER -> config.ownerName.escapeLatex()
            TemplateValue.STRASSE -> address.street.escapeLatex()
            TemplateValue.PLZ -> address.postalCode.escapeLatex()
            TemplateValue.ORT -> address.city.escapeLatex()
            TemplateValue.EMAIL -> config.email.escapeLatex()
            TemplateValue.TELEFON -> config.phone.orEmpty().escapeLatex()
            TemplateValue.WEBSEITE -> config.website.orEmpty().escapeLatex()
            TemplateValue.USTID -> config.vatId.formatted().escapeLatex()
            TemplateValue.STEUERNUMMER -> taxNumber?.value.orEmpty().escapeLatex()
            TemplateValue.REGISTERGERICHT -> config.registerCourt.orEmpty().escapeLatex()
            TemplateValue.REGISTERNUMMER -> config.registerNumber.orEmpty().escapeLatex()
            TemplateValue.GESCHAEFTSFUEHRUNG -> config.managingDirector.orEmpty().escapeLatex()
            TemplateValue.KONTOINHABER -> bank.accountHolder.escapeLatex()
            TemplateValue.INSTITUT -> bank.bankName.escapeLatex()
            TemplateValue.IBAN -> bank.iban.formatted().escapeLatex()
            TemplateValue.BIC -> bank.bic.value.escapeLatex()
            TemplateValue.DOKUMENTART -> invoice.type.documentName.escapeLatex()
            TemplateValue.RECHNUNGSNUMMER -> invoice.number.escapeLatex()
            TemplateValue.RECHNUNGSDATUM -> GermanFormat.date(invoice.issueDate)
            TemplateValue.EMPFAENGER -> MetadataBlocks.recipient(calculatedInvoice)
            TemplateValue.KUNDE_USTID -> MetadataBlocks.customerVatId(calculatedInvoice)
            TemplateValue.KUNDENNUMMER -> MetadataBlocks.buyerIdentifier(calculatedInvoice)
            TemplateValue.AUFTRAGSNUMMER -> MetadataBlocks.salesOrderReference(calculatedInvoice)
            TemplateValue.ZEITRAUM_BESCHRIFTUNG -> MetadataBlocks.servicePeriodLabel(calculatedInvoice)
            TemplateValue.LEISTUNGSZEITRAUM -> MetadataBlocks.servicePeriodValue(calculatedInvoice)
            TemplateValue.VEREINNAHMT_AM -> MetadataBlocks.receivedOn(acceptedInvoice)
            TemplateValue.KUNDENREFERENZ -> MetadataBlocks.buyerReference(calculatedInvoice)
            TemplateValue.ZAHLUNGSZIEL -> MetadataBlocks.dueDate(calculatedInvoice)
            TemplateValue.POSITIONEN -> ItemBlocks.lineItems(calculatedInvoice)
            TemplateValue.SUMMEN -> ItemBlocks.totalsBlock(calculatedInvoice)
            TemplateValue.ARTHINWEIS -> ItemBlocks.typeNote(acceptedInvoice)
            TemplateValue.STEUERHINWEISE -> ItemBlocks.taxNotes(calculatedInvoice)
            TemplateValue.ABSETZUNG -> DeductionBlocks.deductionBlock(acceptedInvoice)
            TemplateValue.SKONTO -> ItemBlocks.skontoNote(calculatedInvoice)
            TemplateValue.BANKVERBINDUNG -> SellerBlocks.bankDetails(calculatedInvoice)
            TemplateValue.SCHLUSSSATZ -> (invoice.note ?: config.closingLine).escapeLatex()
        }
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
    internal fun checkLineBreaks(values: Map<TemplateValue, Latex>) {
        // `(key, value)` zerlegt jeden Eintrag der Map in seine zwei Teile
        // ("destructuring"), statt it.key und it.value zu schreiben.
        values.forEach { (key, value) ->
            check("\\\\" !in value.tex) {
                "Der Renderer liefert für ${key.placeholder} ein rohes \\\\. Zeilenwechsel stehen in der Vorlage, " +
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
     * Prüft, dass die Vorlage jeden Platzhalter enthält, den keine Vorlage
     * weglassen darf ([TemplateValue.required]) – die, deren Inhalt an
     * Rechnungsart oder Rechnung hängt.
     *
     * Die Gegenrichtung zu [fillIn], aber nur für diese Platzhalter: Ein Design
     * darf eine Angabe der Stammdaten weglassen, `balken` etwa setzt weder
     * Webseite noch Steuernummer. Ließe es den Pflichtsatz der Art weg, fehlte
     * er dagegen genau auf den Rechnungen, die keines der Beispiele zeigt.
     * Diese Prüfung deckt ab, was kein Beispiel enthält (Skonto,
     * Befreiungsgrund); ob die Angaben der Stammdaten dastehen, prüft das Netz
     * aus #187 am fertigen PDF.
     *
     * [check] wie bei [checkMacros]: Keine Rechnungsdatei kann das auslösen.
     */
    internal fun checkPlaceholders(template: String) {
        TemplateValue.entries.filter { it.required }.forEach { key ->
            check(key.placeholder in template) {
                "Die Vorlage setzt ${key.placeholder} nicht, und das darf keine Vorlage weglassen."
            }
        }
    }

    /**
     * Setzt die Werte in die Vorlage – und prüft, dass jeder Platzhalter der
     * Vorlage einen Wert bekommt.
     *
     * Hier – und nur hier – wird aus Fragmenten wieder Text. Ersetzt wird in
     * einem Durchgang über die Vorlage: Die Werte stammen aus den Eingabedaten,
     * und ein Kundenname mit `<<POSITIONEN>>` darin ist Inhalt. Nacheinander
     * ersetzt, fände der nächste Schritt ihn im schon eingesetzten Text wieder.
     * Die Variante von `Regex.replace` mit Lambda setzt dessen Ergebnis
     * wörtlich ein; `\` und `$1` sind dort keine Rückverweise.
     *
     * Die Prüfung fängt einen Fehler ab, den sonst niemand bemerkt: Bekommt die
     * Vorlage einen Platzhalter ohne Gegenstück, dann steht die Zeichenkette
     * `<<XYZ>>` am Ende **gesetzt im PDF**. LuaLaTeX übersetzt sie klaglos, und
     * veraPDF bestätigt ein einwandfreies PDF/A – es ist ja eines (#30).
     *
     * Nur diese eine Richtung (#188). Bis dahin verlangte `fillIn` auch, dass
     * jeder Wert einen Platzhalter hat. Einen vertippten Schlüssel fing das
     * nicht besser als die erste Richtung, und seit die Schlüssel [TemplateValue]
     * sind, übersetzt ein vertippter gar nicht mehr. Verboten hätte die zweite
     * Richtung aber, dass ein Design einen Wert nicht setzt. Welche Platzhalter
     * jede Vorlage setzen muss, prüft [checkPlaceholders].
     *
     * [check] und nicht eine Ausnahme für den Benutzer: Keine Rechnungsdatei
     * kann das auslösen. Wenn Vorlage und Renderer auseinanderlaufen, ist das
     * ein Fehler im Programm, so wie die fehlende Vorlage.
     */
    internal fun fillIn(
        template: String,
        values: Map<TemplateValue, Latex>,
    ): String {
        // Die Werte nach dem Namen ihres Platzhalters, so wie die Vorlage ihn schreibt.
        val byName = values.mapKeys { (key, _) -> key.name }

        // Geprüft wird die Vorlage, nicht das Ergebnis: Im Ergebnis kann ein
        // Platzhalter als Inhalt stehen.
        PLACEHOLDER.findAll(template).firstOrNull { it.groupValues[1] !in byName }?.let {
            error("Die Vorlage enthält ${it.value}, aber der Renderer liefert dafür keinen Wert.")
        }

        return PLACEHOLDER.replace(template) { byName.getValue(it.groupValues[1]).tex }
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
