package de.fluchitconsulting.rechnung.render

import de.fluchitconsulting.rechnung.CalculatedInvoice
import de.fluchitconsulting.rechnung.model.Address
import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.model.LineItem
import de.fluchitconsulting.rechnung.model.Prepayment
import de.fluchitconsulting.rechnung.model.SellerConfig
import de.fluchitconsulting.rechnung.model.ServicePeriod
import de.fluchitconsulting.rechnung.values.CountryCode
import de.fluchitconsulting.rechnung.values.InvoiceType
import de.fluchitconsulting.rechnung.values.Money

/**
 * Setzt die LaTeX-Vorlage mit den Werten einer Rechnung zusammen.
 *
 * Der Renderer rechnet nichts – er bekommt die fertige [CalculatedInvoice] und
 * formatiert nur. Umgekehrt kennt der Calculator kein LaTeX. Diese Trennung ist
 * der Grund, warum die Steuerlogik ohne einen einzigen LaTeX-Lauf testbar ist.
 *
 * Innen wird durchgehend mit [Latex] gearbeitet, nicht mit Zeichenketten: Was
 * hier zusammengesetzt wird, hat die Maskierung schon hinter sich. Ausgepackt
 * wird genau einmal, beim Einsetzen in die Vorlage.
 */
object InvoiceRenderer {
    private const val TEMPLATE_PATH = "/templates/invoice.tex"

    // Zeilenumbruch in LaTeX. Als Konstante, weil "\\\\" im Kotlin-Quelltext
    // schwer zu lesen ist: vier Backslashes für zwei im Ergebnis.
    private const val BR = "\\\\"

    // Ein Platzhalter der Vorlage: <<GROSSGESCHRIEBEN>>.
    private val PLACEHOLDER = Regex("<<[A-Z_]+>>")

    // Eine Makro-Definition der Vorlage: \newcommand{\name}[stelligkeit].
    private val NEWCOMMAND = Regex("""\\newcommand\{\\([a-zA-Z]+)}(?:\[(\d+)])?""")

    fun render(calculatedInvoice: CalculatedInvoice): String {
        val invoice = calculatedInvoice.invoice
        val config = calculatedInvoice.config
        val template = checkNotNull(javaClass.getResource(TEMPLATE_PATH)) { "Vorlage $TEMPLATE_PATH fehlt" }.readText()

        val values =
            mapOf(
                "FIRMA" to config.companyName.escapeLatex(),
                "INHABER" to config.ownerName.escapeLatex(),
                "KOPF_PERSON" to letterheadPerson(config),
                "ABSENDER_KONTAKT" to senderContact(config),
                "EMPFAENGER" to recipient(invoice, config),
                "EMPFAENGER_RECHTS" to recipientSideBlock(calculatedInvoice),
                "DOKUMENTART" to invoice.type.documentName.escapeLatex(),
                "RECHNUNGSNUMMER" to invoice.number.escapeLatex(),
                "RECHNUNGSDATUM" to GermanFormat.date(invoice.issueDate),
                "POSITIONEN" to lineItems(calculatedInvoice),
                "SUMMEN" to totalsBlock(calculatedInvoice),
                "ABSETZUNG" to deductionBlock(calculatedInvoice),
                "ARTHINWEIS" to typeNote(invoice),
                "STEUERHINWEISE" to taxNotes(calculatedInvoice),
                "SCHLUSSSATZ" to (invoice.note ?: config.closingLine).escapeLatex(),
                "BANKVERBINDUNG" to bankDetails(invoice.type, config),
                "FUSSZEILE_OBEN" to footerCompanyLine(config),
                "FUSSZEILE_UNTEN" to footerTaxAndBankLine(config),
            )

        checkMacros(template)
        return fillIn(template, values)
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
     * Hier – und nur hier – wird aus Fragmenten wieder Text. `fold` statt einer
     * Schleife mit `var`: Jeder Schritt ersetzt einen Platzhalter und gibt den
     * neuen Text zurück.
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

        val text = values.entries.fold(template) { text, (key, value) -> text.replace("<<$key>>", value.tex) }

        PLACEHOLDER.find(text)?.let {
            error("Die Vorlage enthält ${it.value}, aber der Renderer liefert dafür keinen Wert.")
        }
        return text
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
    fun renderMetadata(calculatedInvoice: CalculatedInvoice): String {
        val invoice = calculatedInvoice.invoice
        val document = invoice.type.documentName.escapeLatex() + " ".escapeLatex() + invoice.number.escapeLatex()
        return listOf(
            Latex.macro("Title", document),
            Latex.macro("Author", calculatedInvoice.config.companyName.escapeLatex()),
            Latex.macro("Subject", document + " vom ".escapeLatex() + GermanFormat.date(invoice.issueDate)),
            Latex.macro("Language", "de-DE".escapeLatex()),
        ).joinToLatex("\n").tex
    }

    /**
     * Der Kontaktblock im Briefkopf: Anschrift, Telefon, E-Mail. Die Webseite
     * steht bewusst nicht darin – sie wiederholte nur die Domain der E-Mail und
     * ließ den Block wie eine Dopplung aussehen. Die E-Mail selbst gehört
     * hierher: Rückfragen zu einer Rechnung kommen meist schriftlich.
     */
    private fun senderContact(config: SellerConfig): Latex =
        buildList {
            add("${config.address.street} · ${config.address.postalCode} ${config.address.city}".escapeLatex())
            config.phone?.let { add("Tel. ".escapeLatex() + it.escapeLatex()) }
            add(config.email.escapeLatex())
        }.joinToLatex(BR)

    private fun recipient(invoice: Invoice, config: SellerConfig): Latex {
        val anschrift =
            buildList {
                add(invoice.customer.name.escapeLatex())
                addAll(addressLines(invoice.customer.address, config.address.countryCode))
            }.joinToLatex(BR)
        // BT-48. Bei Reverse Charge und innergemeinschaftlicher Lieferung verlangt
        // §14a UStG die USt-IdNr. beider Parteien auf der Rechnung – die des
        // Ausstellers steht im Fuß, die des Empfängers hier.
        //
        // Der Abstand wird angehängt, nicht in die Liste gelegt: joinToLatex
        // setzt selbst ein \\ zwischen die Einträge, und \\ direkt gefolgt von
        // \\[2mm] ist in LaTeX ein Fehler ("There's no line here to end").
        val ustId =
            invoice.customer.vatId
                ?.let { Latex.raw("$BR[\\ustidabstand]") + "USt-IdNr. ".escapeLatex() + it.formatted().escapeLatex() }
                ?: Latex.EMPTY
        return anschrift + ustId
    }

    /**
     * Ein Zeitraum, geschrieben wie es die Rechnungsart verlangt: die Prognose
     * einer Abschlagsrechnung in Monaten, der tatsächliche Zeitraum auf den Tag.
     */
    private fun servicePeriod(period: ServicePeriod, type: InvoiceType): Latex =
        if (type.servicePeriodIsForecast) {
            GermanFormat.monthPeriod(period.start, period.end)
        } else {
            GermanFormat.servicePeriod(period.start, period.end)
        }

    /**
     * Die Anschrift, Zeile für Zeile. [sellerCountry] ist das Land des
     * Rechnungsstellers, gegen das verglichen wird – nicht "DE".
     *
     * Der Unterschied ist nicht theoretisch: Vorher stand hier ein fester
     * Vergleich mit "DE", und der bedeutete zweierlei Falsches. Ein
     * kleingeschriebenes `land: de` galt als Ausland und bekam eine Landzeile,
     * während der Calculator dieselbe Rechnung als Inlandsrechnung behandelte.
     * Und säße der Rechnungssteller in Österreich, hätte jede seiner
     * Inlandsrechnungen "AT" unter der Anschrift stehen.
     */
    private fun addressLines(address: Address, sellerCountry: CountryCode): List<Latex> =
        buildList {
            add(address.street.escapeLatex())
            add("${address.postalCode} ${address.city}".escapeLatex())
            // Das Land nur nennen, wenn es ein anderes ist als das des
            // Rechnungsstellers – sonst steht auf jeder Inlandsrechnung eine
            // überflüssige Zeile.
            if (address.countryCode != sellerCountry) add(address.countryCode.value.escapeLatex())
        }

    /**
     * Die Angaben, die weder in den Balken noch in die Tabelle passen. Bewusst
     * als schlichte "Label: Wert"-Zeilen statt als beschriftete Spalte – der
     * frühere Block mit gesperrten Versalien hat oben rechts zu viel Unruhe
     * erzeugt. Das Rechnungsdatum steht jetzt im Titelbalken.
     */
    private fun metadata(calculatedInvoice: CalculatedInvoice): Latex =
        buildList {
            val invoice = calculatedInvoice.invoice
            // Der Bezug auf die Ursprungsrechnung (BG-3 mit BT-25 und BT-26) –
            // Pflicht auf einer Stornorechnung. Als eigene Angabe und nicht im
            // Pflichtsatz, weil EN 16931 ihn als Struktur führt und nicht als
            // Prosa (ADR-0006): Wer ihn später fürs XML aus einem Satz
            // herausklauben müsste, rät. Zuerst, weil er sagt, worauf sich das
            // ganze Blatt bezieht.
            invoice.cancelled?.let { storniert ->
                val bezug =
                    Latex.macro(
                        "mbox",
                        storniert.invoiceNumber.escapeLatex() + " vom ".escapeLatex() +
                            GermanFormat.date(storniert.issueDate),
                    )
                add("Ursprungsrechnung: ".escapeLatex() + bezug)
            }
            // Pflichtangabe nach §14 Abs. 4 Nr. 6 UStG. Wie der Zeitraum heißt,
            // hängt an der Rechnungsart – siehe InvoiceType.servicePeriodLabel.
            // \mbox: Im schmalen Block oben rechts bricht "Juni bis Oktober 2026"
            // sonst hinter dem "bis" um und liest sich wie zwei Angaben. Die
            // Zeile darf stattdessen hinter dem Doppelpunkt umbrechen – der
            // Zeitraum selbst bleibt zusammen. Beim Bezug gilt dasselbe.
            val zeitraum = Latex.macro("mbox", servicePeriod(invoice.servicePeriod, invoice.type))
            add("${invoice.type.servicePeriodLabel}: ".escapeLatex() + zeitraum)
            // BT-7: Steht der Zeitpunkt der Vereinnahmung fest, gehört er zu
            // den Pflichtangaben – deshalb hier und nicht in den Fließtext.
            invoice.receivedOn?.let { add("Abschlag vereinnahmt am ".escapeLatex() + GermanFormat.date(it)) }
            invoice.customer.buyerReference?.let { add("Ihre Referenz: ".escapeLatex() + it.escapeLatex()) }
            // "ohne Abzug" stand bisher im Zahlungssatz unter den Summen. Der Satz
            // ist entfallen, weil er das Fälligkeitsdatum ein zweites Mal nannte –
            // der Hinweis, dass kein Skonto gewährt wird, gehört aber erhalten.
            //
            // Ob es ein Zahlungsziel gibt, entscheidet der Calculator; hier wird
            // nur gesetzt, was dasteht.
            calculatedInvoice.dueDate?.let { add("Zahlbar ohne Abzug bis ".escapeLatex() + GermanFormat.date(it)) }
        }.joinToLatex(BR)

    // Die Zeilen werden mit \\ VERBUNDEN, nicht damit abgeschlossen: Ein
    // Umbruch hinter der letzten Zeile erzeugt in LaTeX eine leere Extrazeile.
    //
    // 2.5mm statt 1.5mm: Sobald eine Position eine Zusatzzeile hat, verschmilzt
    // sie bei knappem Abstand optisch mit der nächsten Position.
    private fun lineItems(calculatedInvoice: CalculatedInvoice): Latex =
        calculatedInvoice.lines
            .map { line ->
                val item = line.item
                Latex.macro(
                    TemplateMacro.POSITIONSZEILE,
                    listOf(
                        describeItem(item, calculatedInvoice.invoice.type),
                        GermanFormat.quantity(item.quantity),
                        item.unit.label.escapeLatex(),
                        GermanFormat.priceWithCurrency(item.unitPrice),
                        GermanFormat.amountWithCurrency(line.net),
                    ),
                )
            }.joinToLatex(" $BR[\\positionsabstand]\n")

    /**
     * Die Beschreibungsspalte einer Position: Bezeichnung, darunter – falls
     * vorhanden – die Zusatzangaben klein und gedämpft. Das ergibt die
     * zweizeilige Position, die die bisherigen Rechnungen hatten, nur dass die
     * zweite Zeile hier aus benannten Feldern entsteht statt aus einem
     * Textzusatz.
     *
     * \newline statt \\ für den Umbruch: Die Spalte ist eine X-Spalte, also
     * ein Absatz. Ein \\ darin würde die Tabellenzeile beenden statt die
     * Textzeile.
     */
    private fun describeItem(item: LineItem, type: InvoiceType): Latex {
        val zusatz =
            buildList {
                item.description?.let { add(it.escapeLatex()) }
                item.note?.let { add(it.escapeLatex()) }
                // Auch hier die Beschriftung der Rechnungsart: Auf einer
                // Abschlagsrechnung ist der Zeitraum einer Position genauso eine
                // Prognose wie der Zeitraum der ganzen Rechnung.
                item.servicePeriod?.let { add("${type.itemPeriodLabel}: ".escapeLatex() + servicePeriod(it, type)) }
            }
        val bezeichnung = item.name.escapeLatex()
        if (zusatz.isEmpty()) return bezeichnung
        return bezeichnung + Latex.macro(TemplateMacro.POSITIONSDETAIL, listOf(zusatz.joinToLatex(" · ")))
    }

    /**
     * Der Summenblock. Gruppen ohne Steuerbetrag erscheinen hier NICHT als
     * "zzgl. 0 % USt. 0,00 €" – das sähe aus wie ein Rechenfehler oder eine
     * vergessene Zeile. Warum keine Steuer anfällt, sagt [taxNotes] darunter.
     */
    private fun totalsBlock(calculatedInvoice: CalculatedInvoice): Latex =
        buildList {
            add("Nettobetrag".escapeLatex() to GermanFormat.amountWithCurrency(calculatedInvoice.netTotal))
            calculatedInvoice.vatGroups.filterNot { it.amount.isZero() }.forEach { group ->
                // Das Prozentzeichen ist maskiert, bevor es hier ankommt –
                // GermanFormat.percent liefert ein fertiges Fragment.
                val label = "zzgl. ".escapeLatex() + GermanFormat.percent(group.rate) + " USt.".escapeLatex()
                add(label to GermanFormat.amountWithCurrency(group.amount))
            }
        }.map { (label, value) -> Latex.raw("  ") + Latex.macro(TemplateMacro.SUMMENZEILE, listOf(label, value)) }
            .joinToLatex(" $BR[\\summenabstand]\n") + summaryRow("Gesamtbetrag", calculatedInvoice.grossTotal)

    /**
     * Die Absetzung der vereinnahmten Abschläge – der Block unter dem
     * Gesamtbetrag, den eine Schlussrechnung bekommt.
     *
     * Er steht **unter** den Summen und nie in der Positionstabelle (ADR-0002):
     * Ein Abschlag ist keine negative Leistung. Als Position gerechnet, senkte
     * er die Bemessungsgrundlage und damit die ausgewiesene Umsatzsteuer der
     * Schlussrechnung – die aber schuldet der Rechnungssteller in voller Höhe;
     * abgesetzt wird nur, was auf den Abschlag schon entrichtet wurde.
     *
     * Jeder Abschlag steht einzeln da. UStAE 14.8 Abs. 7 ließe die Summe
     * genügen, aber der Kunde soll sehen, welche seiner Zahlungen abgesetzt
     * wurde – und die Zeilen liegen ohnehin im Modell.
     */
    private fun deductionBlock(calculatedInvoice: CalculatedInvoice): Latex {
        if (calculatedInvoice.prepayments.isEmpty()) return Latex.EMPTY

        // 2.5mm wie bei den Positionen: Jede Zeile hat eine Zusatzzeile, und bei
        // knapperem Abstand verschmilzt sie optisch mit der nächsten.
        val rows =
            calculatedInvoice.prepayments
                .map { abgesetzt ->
                    Latex.raw("  ") +
                        Latex.macro(
                            TemplateMacro.SUMMENZEILE,
                            listOf(
                                describePrepayment(abgesetzt.prepayment),
                                "\u2212".escapeLatex() + GermanFormat.amountWithCurrency(abgesetzt.gross),
                            ),
                        )
                }.joinToLatex(" $BR[\\positionsabstand]\n")

        // Ein Restbetrag von null bekommt einen Satz statt einer Zahl: Ein
        // nacktes "0,00 €" unter dem Strich sieht aus wie ein Fehler, nicht wie
        // ein ausgeglichener Betrag.
        val (closingRow, trailingNote) =
            if (calculatedInvoice.remainingAmount.isZero()) {
                Latex.EMPTY to
                    Latex.raw("\n\n") +
                    Latex.macro(
                        TemplateMacro.HINWEISABSATZ,
                        listOf(
                            (
                                "Der Rechnungsbetrag ist durch die vereinnahmten Abschläge ausgeglichen; " +
                                    "es verbleibt kein Restbetrag."
                            ).escapeLatex(),
                        ),
                    )
            } else {
                summaryRow("Restbetrag", calculatedInvoice.remainingAmount) to Latex.EMPTY
            }

        return Latex.macro(
            TemplateMacro.ABSETZUNGSKOPF,
            listOf("Abzüglich der berechneten und vereinnahmten Abschläge:".escapeLatex()),
        ) +
            Latex.raw("\n") +
            Latex.macro(TemplateMacro.ABSETZUNGSTABELLE, listOf(Latex.raw("\n") + rows + closingRow + Latex.raw("\n"))) +
            trailingNote
    }

    /**
     * Die Beschreibungsspalte eines abgesetzten Abschlags: Nummer und Daten in
     * der ersten Zeile, die Aufteilung in Netto und Steuer klein darunter –
     * dieselbe Zweizeiligkeit wie bei einer Position.
     */
    private fun describePrepayment(abschlag: Prepayment): Latex {
        // checkNotNull statt eines stillen Ausweichzweigs: Regel 3 im Calculator
        // lässt keinen Abschlag ohne Vereinnahmung durch. Wäre hier doch einer,
        // soll es krachen und nicht eine Zeile ohne Datum gedruckt werden.
        val vereinnahmtAm = checkNotNull(abschlag.receivedOn) { "Abschlag ${abschlag.invoiceNumber} ohne Vereinnahmung" }
        val heading =
            "Abschlagsrechnung ".escapeLatex() + abschlag.invoiceNumber.escapeLatex() + " vom ".escapeLatex() +
                GermanFormat.shortDate(abschlag.issueDate) + ", vereinnahmt am ".escapeLatex() +
                GermanFormat.shortDate(vereinnahmtAm)
        val anteile =
            abschlag.taxShares
                .map { anteil ->
                    "netto ".escapeLatex() + GermanFormat.amountWithCurrency(anteil.net) + " zzgl. ".escapeLatex() +
                        GermanFormat.percent(anteil.vatRate) + " USt. ".escapeLatex() +
                        GermanFormat.amountWithCurrency(anteil.vat)
                }.joinToLatex(" · ")
        return heading + Latex.macro(TemplateMacro.ABSETZUNGSDETAIL, listOf(anteile))
    }

    /**
     * Der Pflichtsatz der Rechnungsart, unter den Summen – heute hat ihn nur
     * die Abschlagsrechnung. Den Wortlaut trägt [InvoiceType], den Abstand die
     * Vorlage: \hinweisabsatz bringt ihn mit, sodass bei den übrigen Arten der
     * leere Platzhalter auch wirklich nichts einnimmt.
     */
    private fun typeNote(invoice: Invoice): Latex =
        invoice.type.mandatoryNote
            ?.let { Latex.macro(TemplateMacro.HINWEISABSATZ, listOf(it.escapeLatex())) }
            ?: Latex.EMPTY

    /**
     * Die fette Zeile unter dem Strich, mit der ein Betragsblock endet – der
     * Gesamtbetrag der Summen, der Restbetrag der Absetzung.
     */
    private fun summaryRow(label: String, amount: Money): Latex =
        Latex.raw(" $BR\n  ") +
            Latex.macro(
                TemplateMacro.ABSCHLUSSZEILE,
                listOf(label.escapeLatex(), GermanFormat.amountWithCurrency(amount)),
            )

    /**
     * Die Begründungen für Positionen ohne Umsatzsteuer – EN 16931 BT-120.
     *
     * Bei Reverse Charge ist das keine Höflichkeit: §14a Abs. 5 UStG verlangt
     * den Hinweis auf die Steuerschuldnerschaft des Leistungsempfängers
     * ausdrücklich auf der Rechnung.
     */
    private fun taxNotes(calculatedInvoice: CalculatedInvoice): Latex =
        calculatedInvoice.vatGroups
            .filter { it.amount.isZero() && it.exemptionReason != null }
            .map { group ->
                "Auf ".escapeLatex() + GermanFormat.amountWithCurrency(group.base) +
                    " entfällt keine Umsatzsteuer: ".escapeLatex() + group.exemptionReason!!.escapeLatex() +
                    ".".escapeLatex()
            }.joinToLatex(BR)

    /**
     * Die Bankverbindung – auf jedem Dokument außer der Stornorechnung.
     *
     * Sie fordert kein Geld, sie kündigt eine Erstattung an; ein Konto darunter
     * läse sich wie eine Zahlungsaufforderung. Überschrift und Abstand stecken
     * im Makro der Vorlage, damit hier das Ganze wegfallen kann und nicht eine
     * Überschrift ohne Inhalt stehen bleibt.
     */
    private fun bankDetails(type: InvoiceType, config: SellerConfig): Latex {
        if (type == InvoiceType.CANCELLATION) return Latex.EMPTY
        return Latex.macro(TemplateMacro.BANKBLOCK, listOf(bankLines(config)))
    }

    private fun bankLines(config: SellerConfig): Latex =
        listOf(
            "Kontoinhaber: ".escapeLatex() + config.bank.accountHolder.escapeLatex(),
            "Institut: ".escapeLatex() + config.bank.bankName.escapeLatex(),
            "IBAN: ".escapeLatex() +
                config.bank.iban
                    .formatted()
                    .escapeLatex(),
            "BIC: ".escapeLatex() +
                config.bank.bic.value
                    .escapeLatex(),
        ).joinToLatex(BR)

    /**
     * Die Person unter dem Firmennamen im Briefkopf – aber nur dann, wenn sie
     * nicht ohnehin schon als Geschäftsführung neben dem Empfänger steht.
     * Bei einer UG sind Inhaber und Geschäftsführung meist dieselbe Person;
     * beides zu setzen nennt sie zweimal im selben Seitenviertel.
     *
     * Für Einzelunternehmen ohne Geschäftsführung bleibt der Name hier stehen:
     * §14 Abs. 4 Nr. 1 UStG verlangt den vollständigen Namen des leistenden
     * Unternehmers, und der ist dort die Person, nicht die Firmierung.
     */
    private fun letterheadPerson(config: SellerConfig): Latex =
        if (config.managingDirector != null) Latex.EMPTY else Latex.raw(BR) + config.ownerName.escapeLatex()

    /**
     * Der Block rechts neben dem Empfängerfeld: Vertretung und die Angaben zur
     * Rechnung. Beides stand vorher an zwei verschiedenen Stellen – die
     * Rechnungsangaben unter dem Balken, die Vertretung hier. Zusammengefasst
     * bleibt der Bereich unter dem Balken frei für die Positionen.
     *
     * "Geschäftsführung" statt "Geschäftsführer": Die Bezeichnung steht neben
     * einem Namen, dessen Geschlecht die Konfiguration nicht kennt.
     */
    private fun recipientSideBlock(calculatedInvoice: CalculatedInvoice): Latex =
        buildList {
            val config = calculatedInvoice.config
            config.managingDirector?.let {
                add("Geschäftsführung".escapeLatex() + Latex.raw(BR) + it.escapeLatex())
            }
            add(metadata(calculatedInvoice))
        }.joinToLatex("$BR[\\blockabstand]")

    /** Erste Fußzeile: Firmierung, Sitz und die Angaben nach §35a GmbHG. */
    private fun footerCompanyLine(config: SellerConfig): Latex =
        buildList {
            add(config.companyName.escapeLatex())
            add("${config.address.street}, ${config.address.postalCode} ${config.address.city}".escapeLatex())
            // Die Geschäftsführung steht oben neben dem Empfänger, nicht hier:
            // Sonst bricht diese Zeile um, und §35a GmbHG verlangt nur, dass die
            // Angabe auf dem Geschäftsbrief steht – nicht, dass sie im Fuß steht.
            config.registerCourt?.let { court ->
                val number = config.registerNumber?.let { ", ".escapeLatex() + it.escapeLatex() } ?: Latex.EMPTY
                add("Registergericht ".escapeLatex() + court.escapeLatex() + number)
            }
        }.joinToLatex(" · ")

    /**
     * Zweite Fußzeile: steuerliche Angaben und Bankverbindung, wie bisher gewohnt.
     *
     * Bewusst OHNE Steuernummer: §14 Abs. 4 Nr. 2 UStG verlangt die Steuernummer
     * ODER die USt-IdNr., nicht beides. Mit beiden wurde die Zeile 178 mm breit
     * und passte nicht mehr in die 165 mm Textbreite. Die Steuernummer bleibt in
     * der Konfiguration – für eine spätere Kleinunternehmer-Variante ohne USt-IdNr.
     */
    private fun footerTaxAndBankLine(config: SellerConfig): Latex =
        buildList {
            add("USt-IdNr. ".escapeLatex() + config.vatId.formatted().escapeLatex())
            add(config.bank.bankName.escapeLatex())
            add(
                "BIC ".escapeLatex() +
                    config.bank.bic.value
                        .escapeLatex(),
            )
            add(
                "IBAN ".escapeLatex() +
                    config.bank.iban
                        .formatted()
                        .escapeLatex(),
            )
        }.joinToLatex(" · ")
}
