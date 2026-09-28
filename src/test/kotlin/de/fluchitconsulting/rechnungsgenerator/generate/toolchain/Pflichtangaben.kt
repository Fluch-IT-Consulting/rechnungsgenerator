package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Discount
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceVariable
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.core.text.TextTemplate
import de.fluchitconsulting.rechnungsgenerator.core.text.TextValue
import de.fluchitconsulting.rechnungsgenerator.core.text.VariableResolver
import de.fluchitconsulting.rechnungsgenerator.core.values.Currency
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent
import de.fluchitconsulting.rechnungsgenerator.core.values.Price
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.nio.file.Path
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readBytes
import kotlin.io.path.readLines
import kotlin.io.path.writeText

/**
 * Ein Design, wie das Netz es sieht: die Stammdaten-Datei, die es wählt, und
 * je Sprache die Wörter, an denen sich zeigt, dass etwas *fehlt* ([Words]).
 *
 * [closingLineAtPaymentTerm]: Das Design nennt die Zahlungsfrist als Satz
 * unter den Summen und hält den Schlusssatz daran fest, damit er nicht allein
 * auf der letzten Seite steht. In helsinki steht die Frist im Kopf, und den
 * Schlusssatz hält der Gruß.
 *
 * [pages] nennt je Beispieldatei, wie viele Seiten sie in diesem Design hat,
 * sobald es mehr als eine sind; jede andere hat eine. Nachgemessen und nicht
 * hergeleitet: "Mehr als eine" übersähe eine Seite mehr oder weniger (#337),
 * und eine gewöhnliche Rechnung, die still auf zwei Seiten wüchse, sähe nicht
 * mehr aus wie bisher (ADR-0020).
 */
data class Design(
    val name: String,
    val sellerConfig: Path,
    val closingLineAtPaymentTerm: Boolean,
    val pages: Map<String, Int>,
    val words: Map<Language, Words>,
)

/**
 * Die Wörter eines Designs in einer Sprache, an denen sich zeigt, dass etwas
 * *fehlt*.
 *
 * Diese Wörter stehen in der Vorlage und ihrer Sprachdatei und nicht in der
 * Rechnung, deshalb gehören sie zum Design. Ob eine Angabe dasteht, lässt
 * sich an ihrem Wert prüfen; ob sie fehlt, nur an ihrer Beschriftung – die
 * IBAN etwa steht im Fuß jedes Dokuments, die Überschrift der Bankverbindung
 * nicht.
 *
 * [bankHeading] ist null, wenn das Design keinen eigenen Bankblock setzt:
 * prag führt die Bank nur im Fuß, als Angabe des Briefbogens (ADR-0011).
 * Dann gibt es keine Überschrift, deren Abwesenheit zu prüfen wäre; ob das
 * Dokument Zahlung fordert, zeigt dort allein das Zahlungsziel.
 *
 * Die Beschriftung des Zahlungsziels ist in prag der Anfang des Satzes, der
 * es als Frist nennt (#211), und keine eigene Kopfzeile.
 *
 * [discountHeading] ist der Kopf der Rabattspalte, so wie `pdftotext` ihn
 * liest: helsinki sperrt seine Spaltenköpfe, und dann kommt jeder Buchstabe
 * einzeln zurück (#213) – je nach Wort aber auch nicht.
 *
 * [continuationNote] und [pageNumber] sind die Wörter der Folgeseiten
 * (ADR-0020): der Satz am Ende jeder Seite, auf die eine weitere folgt, und
 * die Seitenzahl im Kopf jeder Folgeseite. Beides fehlt auf einer Rechnung
 * mit einer Seite.
 *
 * [greeting] steht zwischen Schlusssatz und Name; null, wenn das Design
 * keine setzt – prag schließt mit dem Schlusssatz.
 *
 * [columnHeading] ist ein Wort aus dem Kopf der Positionstabelle, so wie
 * `pdftotext` es liest. Es steht auf jeder Seite, die Positionen trägt.
 */
data class Words(
    val dueDateLabel: String,
    val bankHeading: String?,
    val discountHeading: String,
    val continuationNote: String,
    val pageNumber: (page: Int, total: Int) -> String,
    val greeting: String?,
    val columnHeading: String,
)

/**
 * Dieselben Wörter für beide Varianten des Englischen: Die Sprachdateien
 * eines Designs schreiben sie gleich (#407). Setzt eine Variante ein Wort
 * anders, bekommt sie hier ihren eigenen Eintrag.
 */
private fun englishWords(words: Words): Map<Language, Words> =
    mapOf(Language.BRITISH_ENGLISH to words, Language.AMERICAN_ENGLISH to words)

/**
 * Das Pflichtangaben-Netz unter jedem Design: Für jede Rechnungsart entsteht
 * das PDF mit den echten Werkzeugen, veraPDF bestätigt PDF/A-1b, und
 * `pdftotext` liest zurück, ob die Pflichtangaben auf dem Blatt stehen.
 *
 * Die Vergleichsdateien im `InvoiceRendererTest` schützen Zeichen, nicht das
 * Blatt: je Rechnungsart die Werte, die in die Vorlage gehen
 * (`<art>-inhalt.tex`), und je Design den ganzen Text der gewöhnlichen
 * Rechnung (`rechnung-erwartet.tex`, `prag-rechnung-erwartet.tex`, #204).
 * Wie ein Design die übrigen Arten setzt, steht in keiner davon wörtlich.
 * Dieses Netz fragt etwas anderes: ob auf dem Blatt steht, was dort stehen
 * muss – §14 Abs. 4 und 5 UStG, §35a GmbHG, der Pflichtsatz der Art. Deshalb
 * kennt es keine Schreibweise eines Designs:
 *
 *  - Die erwarteten Werte kommen aus der gerechneten Rechnung und den
 *    Stammdaten, nicht als abgeschriebene Zeichenketten.
 *  - Formatiert werden sie hier, nicht mit `DateFormat` – das liefert LaTeX,
 *    und es ist die Schreibweise *eines* Designs.
 *  - Wo Designs verschieden schreiben dürfen, gilt jede der Schreibweisen:
 *    Datum kurz oder lang, Zeitraum in jeder der Formen, Beträge ohne €, weil
 *    ein Design das Zeichen in den Spaltenkopf stellen darf.
 *  - Leerraum zählt nicht: `pdftotext` bricht Zeilen, wo der Satz sie bricht,
 *    und ein geschütztes Leerzeichen ist nur ein anderes Leerzeichen.
 *
 * Das Netz steht in mehreren Testklassen, weil Gradle Klassen auf die Test-JVMs
 * verteilt und keine teilt (#434): `PflichtangabenArtenTest`,
 * `PflichtangabenSonderfaelleTest`, `PflichtangabenEnglischTest` und
 * `PflichtangabenSeitenTest`. Jede baut sich einen eigenen Lauf, und alle
 * setzen unter denselben [designs] – deshalb hält der Test, dass kein Design
 * und keine Sprache fehlt, in einer davon für alle.
 */
class PflichtangabenRun {
    val workDir: Path = createTempDirectory("rechnungsgenerator-pflichtangaben")
    private val run = ToolchainRun(workDir)
    val sellerConfig: Path = Path.of("src/test/resources/stammdaten.yaml")

    // Die Stammdaten des Beispiels, nur mit anderem Design – dieselben Angaben,
    // damit ein Unterschied im Befund am Design liegt und nicht an den Daten.
    // Dazu auf Wunsch das Beispiellogo, mit absolutem Pfad: Die Stammdaten
    // liegen hier im Temp-Verzeichnis, das Logo nicht (#198). Ohne
    // Geschäftsführung werden sie die eines Einzelunternehmens.
    fun sellerConfigWith(
        design: String,
        logo: Boolean = false,
        withManagingDirector: Boolean = true,
    ): Path {
        val logoEntry = if (logo) "logo: ${Path.of("src/test/resources/logo.tex").toAbsolutePath()}\n" else ""
        val lines = sellerConfig.readLines().filter { withManagingDirector || !it.startsWith("geschaeftsfuehrer:") }
        val name = "stammdaten-$design" + (if (logo) "-logo" else "") + (if (withManagingDirector) "" else "-einzel")
        return workDir.resolve("$name.yaml").also {
            it.writeText(lines.joinToString("\n", postfix = "\n") + "design: $design\n" + logoEntry)
        }
    }

    // Ein weiteres Design ist hier ein weiterer Eintrag, eine weitere Sprache
    // je Design ein Eintrag in words.
    val designs =
        listOf(
            Design(
                name = "helsinki",
                // Jedes Design setzt das Logo; so sieht veraPDF es bei jeder
                // Rechnungsart.
                sellerConfig = sellerConfigWith("helsinki", logo = true),
                closingLineAtPaymentTerm = false,
                pages =
                    mapOf(
                        "viele-positionen" to 3,
                        "schluss-an-der-seitengrenze" to 3,
                        "befreiungsgrund-an-der-seitengrenze" to 3,
                        "englische-rechnung" to 2,
                    ),
                words =
                    mapOf(
                        Language.GERMAN to
                            Words(
                                dueDateLabel = "Zahlbar ohne Abzug bis",
                                bankHeading = "Bankverbindung",
                                discountHeading = "R A B AT T",
                                continuationNote = "Fortsetzung auf der nächsten Seite",
                                pageNumber = { page, total -> "Seite $page von $total" },
                                greeting = "Mit freundlichen Grüßen",
                                columnHeading = "EINZELPREIS",
                            ),
                    ) +
                        englishWords(
                            Words(
                                dueDateLabel = "Due in full by",
                                bankHeading = "Bank details",
                                discountHeading = "DISCOUNT",
                                continuationNote = "Continued on the next page",
                                pageNumber = { page, total -> "Page $page of $total" },
                                greeting = "Kind regards",
                                columnHeading = "UNIT PRICE",
                            ),
                        ),
            ),
            Design(
                name = "prag",
                sellerConfig = sellerConfigWith("prag", logo = true),
                closingLineAtPaymentTerm = true,
                pages =
                    mapOf(
                        "viele-positionen" to 3,
                        "schluss-an-der-seitengrenze" to 3,
                        "befreiungsgrund-an-der-seitengrenze" to 3,
                    ),
                words =
                    mapOf(
                        Language.GERMAN to
                            Words(
                                dueDateLabel = "Die Zahlung ist",
                                bankHeading = null,
                                discountHeading = "Rabatt",
                                continuationNote = "Fortsetzung auf der nächsten Seite",
                                pageNumber = { page, total -> "Seite $page von $total" },
                                greeting = null,
                                columnHeading = "Einzelpreis",
                            ),
                    ) +
                        englishWords(
                            Words(
                                dueDateLabel = "Payment is due",
                                bankHeading = null,
                                discountHeading = "Discount",
                                continuationNote = "Continued on the next page",
                                pageNumber = { page, total -> "Page $page of $total" },
                                greeting = null,
                                columnHeading = "Unit price",
                            ),
                        ),
            ),
        )

    // Setzt eine Beispieldatei mit den echten Werkzeugen und prüft das Blatt.
    fun typesetAndCheck(
        file: String,
        design: Design,
        sellerConfig: Path,
        targetName: String,
        input: Path = Path.of("src/test/resources/$file.yaml"),
    ): Typeset {
        val calculated = CalculatedInvoice(YamlLoader.loadInvoice(input), YamlLoader.loadSellerConfig(sellerConfig))

        val (pdf, output) = run.generate(file, targetName = targetName, input = input, sellerConfig = sellerConfig)
        withClue(output.joinToString("\n")) { output shouldContain "PDF/A-1b: gültig" }

        // Eine Rechnung hat keine Lesezeichen, und das PDF sagt das auch (#321):
        // Mit Lesezeichen verlangte hyperref ohne Not einen weiteren Lauf, und
        // `/UseOutlines` öffnete im Viewer eine leere Seitenleiste. Geprüft an
        // den Bytes und nicht am Blatt, deshalb hier und nicht in verify: pdfx
        // lädt zwar die Grundlage, aber was eine Vorlage oder Sprachdatei
        // danach lädt, kann hyperref noch umstellen. ISO-8859-1 bildet jedes
        // Byte auf ein Zeichen ab, so stört der Binärteil beim Suchen nicht.
        val bytes = String(pdf.readBytes(), Charsets.ISO_8859_1)
        val sheet = Sheet(run.textOf(pdf))
        assertSoftly {
            withClue("Seitenzahl") { sheet.pages shouldBe (design.pages[file] ?: 1) }
            // Als Wahrheitswert: shouldContain druckte das ganze PDF in die Meldung.
            withClue("/PageMode/UseNone fehlt im PDF") { ("/PageMode/UseNone" in bytes) shouldBe true }
            withClue("/Outlines steht im PDF") { ("/Outlines" in bytes) shouldBe false }
            sheet.verify(calculated, design)
        }
        return Typeset(calculated, sheet)
    }
}

/** Eine gesetzte Rechnung: das gerechnete Ergebnis und das zurückgelesene Blatt. */
data class Typeset(
    val calculated: CalculatedInvoice,
    val sheet: Sheet,
)

/**
 * Der zurückgelesene Text, mit zusammengefasstem Leerraum – im Ganzen und je
 * Seite. `pdftotext` schließt jede Seite mit einem Seitenvorschub ab; daran
 * zählt der Test die Seiten, ohne ein weiteres Werkzeug.
 */
class Sheet(
    text: String,
) {
    private val text = normalize(text)
    private val pageTexts = text.split('\u000C').dropLastWhile { it.isBlank() }.map { normalize(it) }

    val pages: Int get() = pageTexts.size

    fun shows(
        detail: String,
        vararg spellings: String,
    ) = shows(text, detail, spellings)

    fun lacks(
        detail: String,
        vararg spellings: String,
    ) = lacks(text, detail, spellings)

    fun showsOnPage(
        page: Int,
        detail: String,
        vararg spellings: String,
    ) = shows(pageTexts[page - 1], "$detail auf Seite $page", spellings)

    /** Die Seiten, auf denen eine der Schreibweisen vollständig steht. */
    fun pagesWith(vararg spellings: String): Set<Int> =
        (1..pages).filter { page -> spellings.any { normalize(it) in pageTexts[page - 1] } }.toSet()

    /** Die erste Seite, auf der die Angabe vollständig steht, oder null. */
    fun pageWith(detail: String): Int? = pageTexts.indexOfFirst { normalize(detail) in it }.takeIf { it >= 0 }?.plus(1)

    fun lacksOnPage(
        page: Int,
        detail: String,
        vararg spellings: String,
    ) = lacks(pageTexts[page - 1], "$detail auf Seite $page", spellings)

    private fun shows(
        text: String,
        detail: String,
        spellings: Array<out String>,
    ) {
        withClue("$detail: keine der Schreibweisen ${spellings.toList()} im Text\n$text") {
            spellings.any { normalize(it) in text } shouldBe true
        }
    }

    private fun lacks(
        text: String,
        detail: String,
        spellings: Array<out String>,
    ) {
        withClue("$detail: ${spellings.toList()} steht im Text, sollte aber fehlen\n$text") {
            spellings.none { normalize(it) in text } shouldBe true
        }
    }

    companion object {
        // \s kennt weder das geschützte (U+00A0) noch die schmalen Leerzeichen.
        // Das Minuszeichen bleibt, wie es ist: Gleichgesetzt mit "-" ließe das
        // Netz einen Bindestrich vor einer Zahl durch (#320).
        private val WHITESPACE = Regex("[\\s\\u00A0\\u2009\\u202F]+")

        fun normalize(s: String): String = s.replace(WHITESPACE, " ").trim()
    }
}

private fun Sheet.verify(
    calculated: CalculatedInvoice,
    design: Design,
) {
    val invoice = calculated.invoice
    val config = calculated.config
    val words = design.words.getValue(invoice.language)
    val spelling = Spelling(invoice.language, invoice.currency)

    // §14 Abs. 4 Nr. 1: Name und Anschrift beider Seiten. Der vollständige
    // Name des leistenden Unternehmers ist ohne Geschäftsführung die Person,
    // nicht die Firmierung.
    shows("Rechnungssteller", config.companyName)
    if (config.managingDirector == null) shows("Name des Inhabers", config.ownerName)
    shows("Anschrift Rechnungssteller", config.address.street)
    shows("Ort Rechnungssteller", "${config.address.postalCode} ${config.address.city}")
    shows("Kunde", invoice.customer.name)
    shows("Anschrift Kunde", invoice.customer.address.street)
    shows("Ort Kunde", "${invoice.customer.address.postalCode} ${invoice.customer.address.city}")
    // Die USt-IdNr. des Kunden (BT-48), Pflicht bei AE und K (§14a Abs. 1
    // UStG). Das Design setzt sie, sobald die Datei sie nennt.
    invoice.customer.vatId?.let { shows("USt-IdNr. des Kunden", it.value, it.formatted()) }

    // Nr. 2: Steuernummer oder USt-IdNr. – eine genügt.
    shows(
        "Steuernummer oder USt-IdNr.",
        *listOfNotNull(config.vatId.value, config.vatId.formatted(), config.taxNumber?.value).toTypedArray(),
    )

    // Nr. 3 und 4: Rechnungsdatum und Rechnungsnummer.
    shows("Rechnungsdatum", *spelling.date(invoice.issueDate))
    shows("Rechnungsnummer", invoice.number)

    // Nr. 5 und 6: Menge und Art der Leistung, Zeitpunkt der Leistung und –
    // bei einer Abschlagsrechnung, wenn er feststeht – der Vereinnahmung.
    invoice.items.forEachIndexed { i, item ->
        shows("Bezeichnung der Position ${i + 1}", item.name)
        shows("Menge der Position ${i + 1}", spelling.quantity(item.quantity))
    }
    // Einzelpreis und Betrag einer Position sind keine Pflichtangaben, aber
    // ohne sie ließe sich die Position nicht nachrechnen (#14). Und nur der
    // Einzelpreis trägt mehr als zwei Nachkommastellen: Setzte ein Design ihn
    // in falscher Schreibweise, sähe das Netz es sonst nirgends (ADR-0021).
    calculated.lines.forEachIndexed { i, line ->
        shows("Einzelpreis der Position ${i + 1}", spelling.price(line.item.unitPrice))
        shows("Betrag der Position ${i + 1}", spelling.amount(line.net))
    }
    // Der Rabatt ist keine Pflichtangabe: §14 Abs. 4 Nr. 7 verlangt nur
    // Minderungen, die nicht schon im Entgelt berücksichtigt sind, und der
    // Rabatt ist es. Geprüft wird trotzdem, dass die Spalte genau dann steht,
    // wenn eine Position einen Rabatt hat (#213) – sonst sähe man einen
    // Betrag, der nicht Menge × Einzelpreis ist, ohne Grund.
    calculated.lines.forEachIndexed { i, line ->
        when (val discount = line.item.discount) {
            null -> {}
            is Discount.Rate -> {
                shows("Rabatt der Position ${i + 1}", *spelling.rate(discount.rate))
            }
            is Discount.Amount -> {
                shows("Rabatt der Position ${i + 1}", spelling.amount(discount.amount))
            }
        }
    }
    if (invoice.items.any { it.discount != null }) {
        shows("Kopf der Rabattspalte", words.discountHeading)
    } else {
        lacks("Kopf der Rabattspalte", words.discountHeading)
    }
    shows("Leistungszeitraum", *spelling.zeitraum(invoice.servicePeriod.start, invoice.servicePeriod.end))
    invoice.receivedOn?.let { shows("Tag der Vereinnahmung", *spelling.date(it)) }

    // Nr. 7 und 8: Entgelt je Steuersatz, dazu Satz und Steuerbetrag – oder,
    // bei einer Steuerbefreiung, der Hinweis darauf statt beider (Nr. 8
    // zweiter Halbsatz, #218). Das Entgelt verlangt Nr. 7 in jedem Fall; bei
    // einer befreiten Gruppe nennt es der Hinweis selbst. Ob eine Gruppe
    // befreit ist, sagt ihr Grund und nicht ihr Steuerbetrag von null: Die
    // Kategorie Z trägt keinen Grund und bleibt deshalb eine Gruppe, die Satz
    // und Steuerbetrag zeigen muss.
    calculated.vatGroups.forEach { group ->
        val name = "Steuergruppe ${group.category} ${group.rate}"
        shows("$name: Entgelt", spelling.amount(group.base))
        val reason = group.exemptionReason
        if (reason == null) {
            shows("$name: Satz", *spelling.rate(group.rate))
            shows("$name: Steuerbetrag", spelling.amount(group.amount))
        } else {
            shows("$name: Hinweis auf die Steuerbefreiung", reason)
        }
    }

    // §14 Abs. 5 Satz 2: Die Schlussrechnung setzt Entgelt und Steuer der
    // vereinnahmten Abschläge ab.
    calculated.prepayments.forEach { abschlag ->
        val number = abschlag.prepayment.invoiceNumber
        shows("Abschlag $number", number)
        shows("Abschlag $number: Entgelt", spelling.amount(abschlag.net))
        shows("Abschlag $number: Steuer", spelling.amount(abschlag.vat))
    }

    // §35a GmbHG, soweit die Stammdaten die Angaben führen.
    config.registerCourt?.let { shows("Registergericht", it) }
    config.registerNumber?.let { shows("Handelsregisternummer", it) }
    config.managingDirector?.let { shows("Geschäftsführer", it) }

    // Der Pflichtsatz der Art, mit eingesetzten Werten.
    // Die Werte holt der Test selbst und nicht über den InvoiceVariableResolver:
    // Holte der das falsche Datum, sähe der Test sonst dasselbe falsche Datum.
    val mandatoryNote =
        invoice.type.mandatoryNote(invoice.language)?.let { template ->
            val values =
                VariableResolver<InvoiceVariable> { variable ->
                    val original = checkNotNull(invoice.cancelled) { "<<$variable>> ohne Ursprungsrechnung" }
                    when (variable) {
                        InvoiceVariable.URSPRUNGSNUMMER -> TextValue.Text(original.invoiceNumber)
                        InvoiceVariable.URSPRUNGSDATUM -> TextValue.Date(original.issueDate)
                    }
                }
            listOf(template.plainText(values, spelling.short), template.plainText(values, spelling.long))
        }
    mandatoryNote?.let { shows("Pflichtsatz", *it.toTypedArray()) }

    // Zahlungsziel und Bankverbindung genau dann, wenn das Dokument Zahlung
    // fordert. Die Abwesenheit zeigt sich an der Beschriftung: Ein Design, das
    // sie ohne Wert stehen ließe, fiele hier auf.
    val dueDate = calculated.dueDate
    val bankHeading = words.bankHeading
    if (dueDate != null) {
        shows("Zahlungsziel", *spelling.date(dueDate), *spelling.term(calculated.paymentTermDays))
        bankHeading?.let { shows("Überschrift der Bankverbindung", it) }
        shows("IBAN", config.bank.iban.formatted())
        shows("BIC", config.bank.bic.value)
    } else {
        lacks("Beschriftung des Zahlungsziels", words.dueDateLabel)
        bankHeading?.let { lacks("Überschrift der Bankverbindung", it) }
    }

    // §14 Abs. 4 Satz 1 Nr. 7: das im Voraus vereinbarte Skonto, je Stufe mit
    // Satz und Frist. Die Frist rechnet der Test selbst, aus demselben Grund
    // wie die Werte des Pflichtsatzes.
    val skonto = invoice.skonto.map { stufe -> spelling.rate(stufe.rate) to spelling.date(invoice.issueDate.plusDays(stufe.days.toLong())) }
    skonto.forEach { (rate, term) ->
        shows("Skontosatz", *rate)
        shows("Skontofrist", *term)
    }

    // Decken die Abschläge den Gesamtbetrag genau, steht statt eines
    // Restbetrags von null ein Satz da, und das Dokument fordert kein Geld.
    // Das prüft der Test hier an den Beträgen und nicht erst an `dueDate`:
    // Setzte das Programm ein Zahlungsziel, obwohl nichts offen ist, sähe der
    // Zweig oben es als Forderung.
    val settled = calculated.prepayments.isNotEmpty() && calculated.remainingAmount.isZero()
    val settledNote = settledNote(invoice.language)
    if (settled) {
        shows("Satz zum ausgeglichenen Restbetrag", settledNote)
        lacks("Beschriftung des Zahlungsziels", words.dueDateLabel)
        bankHeading?.let { lacks("Überschrift der Bankverbindung", it) }
    } else {
        lacks("Satz zum ausgeglichenen Restbetrag", settledNote)
    }

    // Die Folgeseiten (ADR-0020): Jede trägt Rechnungsnummer, Datum und
    // Seitenzahl, damit eine lose Seite zu ihrer Rechnung findet, und den Fuß
    // des Briefbogens mit Anschrift, Register, USt-IdNr. und Bankverbindung.
    // Vom Register prüft das Netz nur die Nummer: Das Registergericht heißt
    // wie ein Ort und stünde meist schon mit der Anschrift da. Jede Seite, auf
    // die eine folgt, sagt das am Ende. Eine Rechnung mit einer Seite trägt
    // nichts davon – „Seite 1 von 1“ sagte nichts.
    if (pages == 1) {
        lacks("Seitenzahl", words.pageNumber(1, 1))
        lacks("Fortsetzungshinweis", words.continuationNote)
    } else {
        for (page in 1..pages) {
            if (page < pages) {
                showsOnPage(page, "Fortsetzungshinweis", words.continuationNote)
            } else {
                lacksOnPage(page, "Fortsetzungshinweis", words.continuationNote)
            }
            showsOnPage(page, "Rechnungssteller im Fuß", config.companyName)
            showsOnPage(page, "USt-IdNr. im Fuß", config.vatId.value, config.vatId.formatted())
            showsOnPage(page, "Anschrift im Fuß", config.address.street)
            showsOnPage(page, "Ort im Fuß", "${config.address.postalCode} ${config.address.city}")
            config.registerNumber?.let { showsOnPage(page, "Handelsregisternummer im Fuß", it) }
            showsOnPage(page, "IBAN im Fuß", config.bank.iban.formatted())
            showsOnPage(page, "BIC im Fuß", config.bank.bic.value)
            if (page == 1) continue
            showsOnPage(page, "Seitenzahl", words.pageNumber(page, pages))
            showsOnPage(page, "Rechnungsnummer", invoice.number)
            showsOnPage(page, "Rechnungsdatum", *spelling.date(invoice.issueDate))
        }

        // Jede Seite mit Positionen trägt den Spaltenkopf, und die Summen
        // stehen bei der letzten Position: Summen ohne das, was sie summieren,
        // waren der Befund aus #318.
        invoice.items
            .mapNotNull { pageWith(it.name) }
            .distinct()
            .forEach { showsOnPage(it, "Spaltenkopf", words.columnHeading) }
        pageWith(invoice.items.last().name)?.let {
            showsOnPage(it, "Gesamtbetrag bei der letzten Position", spelling.amount(calculated.grossTotal))
        }

        // Schlusssatz, Grußformel und Name brechen nicht auseinander: Der Satz
        // steht ganz auf einer Seite, und Gruß und Name stehen auf derselben.
        val closingLine = invoice.note ?: config.closingLine(invoice.language)
        val page = pageWith(closingLine)
        withClue("Schlusssatz steht auf keiner Seite vollständig: $closingLine") { (page != null) shouldBe true }
        val greeting = words.greeting
        if (page != null && greeting != null) {
            showsOnPage(page, "Grußformel beim Schlusssatz", greeting)
            showsOnPage(page, "Name beim Schlusssatz", config.ownerName)
        }
        // Ohne Skonto ist die Zahlungsfrist der Satz direkt vor dem Schlusssatz.
        if (page != null && design.closingLineAtPaymentTerm && dueDate != null && invoice.skonto.isEmpty()) {
            showsOnPage(page, "Zahlungsfrist beim Schlusssatz", words.dueDateLabel)
        }

        // Kein Hinweis auf eine Steuerbefreiung bricht über zwei Seiten: Der
        // Grund steht ganz auf einer Seite, und das Entgelt, mit dem der Satz
        // beginnt, auf derselben.
        calculated.vatGroups.forEach { group ->
            val reason = group.exemptionReason ?: return@forEach
            val reasonPage = pageWith(reason)
            withClue("Befreiungsgrund steht auf keiner Seite vollständig: $reason") { (reasonPage != null) shouldBe true }
            if (reasonPage != null) showsOnPage(reasonPage, "Entgelt beim Befreiungsgrund", spelling.amount(group.base))
        }

        // Ebenso wenig die übrigen fachlichen Sätze: Pflichtsatz und Satz zum
        // ausgeglichenen Restbetrag stehen ganz auf einer Seite, das Skonto
        // mit allen Stufen auf einer, das Zahlungsziel auf der Seite seiner
        // Beschriftung – in helsinki ist das die Kopfzeile, in prag der Anfang
        // des Satzes.
        mandatoryNote?.let { spellings ->
            withClue("Pflichtsatz steht auf keiner Seite vollständig: ${spellings.first()}") {
                spellings.any { pageWith(it) != null } shouldBe true
            }
        }
        if (settled) {
            withClue("Satz zum ausgeglichenen Restbetrag steht auf keiner Seite vollständig") {
                (pageWith(settledNote) != null) shouldBe true
            }
        }
        if (skonto.isNotEmpty()) {
            val common =
                skonto
                    .flatMap { (rate, term) -> listOf(pagesWith(*rate), pagesWith(*term)) }
                    .reduce { a, b -> a intersect b }
            withClue("Skonto steht nicht mit allen Stufen auf einer Seite") { common.isNotEmpty() shouldBe true }
        }
        if (dueDate != null) {
            val dueDatePage = pageWith(words.dueDateLabel)
            withClue("Beschriftung des Zahlungsziels auf keiner Seite") { (dueDatePage != null) shouldBe true }
            if (dueDatePage != null) {
                showsOnPage(
                    dueDatePage,
                    "Zahlungsziel bei seiner Beschriftung",
                    *spelling.date(dueDate),
                    *spelling.term(calculated.paymentTermDays),
                )
            }
        }
    }
}

/**
 * Der Satz, der bei ausgeglichenem Restbetrag statt einer Zahl dasteht, in
 * jeder Sprache. Abgeschrieben und nicht aus `DeductionBlocks` gelesen: Ändert
 * sich der Wortlaut, wird das Netz rot, und das ist eine Frage wie bei den
 * Vergleichsdateien. Er ist ein Satz des Programms und keiner eines Designs,
 * deshalb steht er hier und nicht in [Words].
 */
private fun settledNote(language: Language): String =
    when (language) {
        Language.GERMAN -> {
            "Der Rechnungsbetrag ist durch die vereinnahmten Abschläge ausgeglichen; es verbleibt kein Restbetrag."
        }
        Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> {
            "The invoice amount is settled by the advance payments received; no balance remains."
        }
    }

/**
 * Der Grund, den eine Kategorie ohne eigenen auf das Blatt bringt, in jeder
 * Sprache (#427). Abgeschrieben und nicht aus `TaxCategory` gelesen: verify
 * prüft den Grund der gerechneten Gruppe und sähe dort einen falschen
 * Wortlaut als richtig an. Ändert er sich, wird das Netz rot, und das ist
 * eine Frage wie bei den Vergleichsdateien.
 */
fun defaultReasonOnSheet(
    category: TaxCategory,
    language: Language,
): String {
    val (german, english) =
        when (category) {
            TaxCategory.REVERSE_CHARGE -> {
                "Steuerschuldnerschaft des Leistungsempfängers (Reverse Charge)" to "Reverse charge"
            }
            TaxCategory.INTRA_COMMUNITY -> {
                "Steuerfreie innergemeinschaftliche Lieferung" to "Exempt intra-Community supply of goods"
            }
            TaxCategory.EXPORT -> {
                "Steuerfreie Ausfuhrlieferung" to "Exempt export of goods"
            }
            TaxCategory.STANDARD, TaxCategory.ZERO_RATED, TaxCategory.EXEMPT, TaxCategory.OUT_OF_SCOPE -> {
                error(
                    "$category bringt keinen Grund mit",
                )
            }
        }
    return when (language) {
        Language.GERMAN -> german
        Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> english
    }
}

// --- Schreibweisen ----------------------------------------------------------
// Bewusst unabhängig von DateFormat, siehe oben. Jede Funktion liefert alle
// Schreibweisen, die ein Design in der Sprache der Rechnung wählen darf. Ohne
// `else` über die Sprache: Eine neue sagt hier, wie sie schreibt.

/**
 * Die Schreibweisen einer Sprache, wie das Blatt sie tragen muss.
 *
 * Eine negative Zahl trägt das Minuszeichen U+2212, in jeder Sprache (#320).
 * Hier eigens gesetzt und nicht aus DateFormat übernommen: Es ist die
 * Erwartung an das Blatt, und das JDK brächte den Bindestrich mit.
 *
 * Beträge ohne Währungszeichen, weil ein Design das Zeichen in den
 * Spaltenkopf stellen darf. Im Englischen steht es aber zwischen Minus und
 * Zahl, `−€200.00`; ohne Zeichen stünde `−200.00` nirgends auf dem Blatt. Ein
 * negativer Betrag nennt es dort deshalb mit, das der [currency] (#409).
 */
private class Spelling(
    private val language: Language,
    private val currency: Currency,
) {
    // Abgeschrieben und nicht aus der Sprachdatei gelesen, aus demselben
    // Grund wie die Wörter der Designs.
    private val symbol =
        when (currency) {
            Currency.EUR -> "€"
            Currency.USD -> "$"
            Currency.GBP -> "£"
        }

    private val symbols =
        when (language) {
            Language.GERMAN -> DecimalFormatSymbols(Locale.GERMANY)
            Language.BRITISH_ENGLISH -> DecimalFormatSymbols(Locale.UK)
            Language.AMERICAN_ENGLISH -> DecimalFormatSymbols(Locale.US)
        }.apply { minusSign = '\u2212' }

    val short: DateTimeFormatter =
        when (language) {
            Language.GERMAN -> DateTimeFormatter.ofPattern("dd.MM.yyyy", language.locale)
            Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> DateTimeFormatter.ofPattern("yyyy-MM-dd", language.locale)
        }
    val long: DateTimeFormatter =
        when (language) {
            Language.GERMAN -> DateTimeFormatter.ofPattern("d. MMMM yyyy", language.locale)
            Language.BRITISH_ENGLISH -> DateTimeFormatter.ofPattern("d MMMM yyyy", language.locale)
            Language.AMERICAN_ENGLISH -> DateTimeFormatter.ofPattern("MMMM d, yyyy", language.locale)
        }
    private val month = DateTimeFormatter.ofPattern("MMMM", language.locale)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", language.locale)

    private fun number(
        value: BigDecimal,
        minFraction: Int,
    ): String =
        DecimalFormat("#,##0", symbols)
            .apply {
                minimumFractionDigits = minFraction
                maximumFractionDigits = maxOf(value.stripTrailingZeros().scale(), minFraction)
            }.format(value)

    fun amount(money: Money): String {
        val digits = number(money.value, minFraction = 2)
        return when (language) {
            Language.GERMAN -> {
                digits
            }
            Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> {
                if (money.isNegative()) {
                    "\u2212$symbol" +
                        digits.removePrefix(
                            "\u2212",
                        )
                } else {
                    digits
                }
            }
        }
    }

    fun price(price: Price): String = number(price.value, minFraction = 2)

    fun quantity(value: BigDecimal): String = number(value, minFraction = 0)

    fun rate(percent: Percent): Array<String> {
        val formatted = number(percent.valueAsPercentage, minFraction = 0)
        return arrayOf("$formatted %", "$formatted%")
    }

    fun date(value: LocalDate): Array<String> = arrayOf(short.format(value), long.format(value))

    /** Das Zahlungsziel als Frist ab Rechnungsdatum statt als Datum (#211). */
    fun term(days: Int): Array<String> =
        when (language) {
            Language.GERMAN -> {
                when (days) {
                    0 -> arrayOf("sofort fällig")
                    1 -> arrayOf("1 Tag nach Rechnungsdatum")
                    else -> arrayOf("$days Tage nach Rechnungsdatum")
                }
            }
            Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> {
                when (days) {
                    0 -> arrayOf("due immediately")
                    1 -> arrayOf("1 day after the invoice date")
                    else -> arrayOf("$days days after the invoice date")
                }
            }
        }

    /**
     * Ein Zeitraum auf den Tag genau oder – wo er ganze Kalendermonate füllt –
     * in Monaten (§31 Abs. 4 UStDV, UStAE 14.8 Abs. 1 und 4).
     */
    fun zeitraum(
        start: LocalDate,
        end: LocalDate,
    ): Array<String> {
        val until =
            when (language) {
                Language.GERMAN -> "bis"
                Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> "to"
            }
        val days = listOf("–", "-", until).map { "${short.format(start)} $it ${short.format(end)}" }
        val wholeMonths = start.dayOfMonth == 1 && end == end.withDayOfMonth(end.lengthOfMonth())
        if (!wholeMonths) return days.toTypedArray()
        val months =
            if (YearMonth.from(start) == YearMonth.from(end)) {
                listOf(monthYear.format(start))
            } else {
                listOf(
                    "${month.format(start)} $until ${monthYear.format(end)}",
                    "${monthYear.format(start)} $until ${monthYear.format(end)}",
                )
            }
        return (days + months).toTypedArray()
    }
}

/** Ein Wortlaut als Klartext, wie ihn pdftotext liest, mit einer Schreibweise des Datums. */
private fun <V> TextTemplate<V>.plainText(
    resolver: VariableResolver<V>,
    dateFormat: DateTimeFormatter,
): String =
    map(
        text = { it },
        variable = { variable ->
            when (val value = resolver.resolve(variable)) {
                is TextValue.Text -> value.text
                is TextValue.Date -> dateFormat.format(value.date)
            }
        },
    ).joinToString("")
