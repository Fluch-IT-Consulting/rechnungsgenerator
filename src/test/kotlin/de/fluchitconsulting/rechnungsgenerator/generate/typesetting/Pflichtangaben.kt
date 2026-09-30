package de.fluchitconsulting.rechnungsgenerator.generate.typesetting

import de.fluchitconsulting.rechnungsgenerator.core.accepted.accepted
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
import de.fluchitconsulting.rechnungsgenerator.generate.render.InvoiceRenderer
import de.fluchitconsulting.rechnungsgenerator.toolchain.process.ExternalTool
import de.fluchitconsulting.rechnungsgenerator.toolchain.process.LuaLatex
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
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * [closingLineAtPaymentTerm]: Die Zahlungsfrist ist der Satz direkt vor dem
 * Schlusssatz, wie in prag; helsinki setzt sie in den Kopf.
 *
 * [pages] ist nachgemessen, nicht hergeleitet (#337); eine Datei ohne Eintrag
 * hat genau eine Seite.
 */
data class Design(
    val name: String,
    val sellerConfig: Path,
    val closingLineAtPaymentTerm: Boolean,
    val pages: Map<String, Int>,
    val words: Map<Language, Words>,
)

/**
 * Die Beschriftungen eines Designs: Dass eine Angabe *fehlt*, zeigt sich nur an
 * ihrer Beschriftung, denn die IBAN etwa steht auch im Fuß.
 *
 * Geschrieben, wie `pdftotext` sie liest: helsinki sperrt die Spaltenköpfe, und
 * je nach Wort kommen die Buchstaben einzeln zurück (#213).
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

private fun englishWords(words: Words): Map<Language, Words> =
    mapOf(Language.BRITISH_ENGLISH to words, Language.AMERICAN_ENGLISH to words)

/**
 * Das Pflichtangaben-Netz: prüft am zurückgelesenen Blatt, ob dasteht, was nach
 * §14 Abs. 4 und 5 UStG und §35a GmbHG dastehen muss. Es kennt keine Schreibweise
 * eines Designs: Erwartet wird aus der gerechneten Rechnung, formatiert hier und
 * nicht mit `DateFormat`, und jede Schreibweise gilt, die ein Design wählen darf.
 *
 * Auf vier Testklassen verteilt, weil Gradle Klassen auf die Forks verteilt (#434).
 */
class PflichtangabenRun {
    val workDir: Path = createTempDirectory("rechnungsgenerator-pflichtangaben")
    private val run = ToolchainRun(workDir)
    val sellerConfig: Path = Path.of("src/test/resources/stammdaten.yaml")

    // Das Logo mit absolutem Pfad: Diese Stammdaten liegen im Temp-Verzeichnis (#198).
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

    val designs =
        listOf(
            Design(
                name = "helsinki",
                // Mit Logo, damit veraPDF es unter jeder Rechnungsart prüft.
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

        // Keine Lesezeichen (#321), je Design geprüft: Eine Vorlage kann hyperref
        // noch umstellen. ISO-8859-1, weil es jedes Byte auf ein Zeichen abbildet.
        val bytes = String(pdf.readBytes(), Charsets.ISO_8859_1)
        val sheet = Sheet(run.textOf(pdf))
        assertSoftly {
            withClue("Seitenzahl") { sheet.pages shouldBe (design.pages[file] ?: 1) }
            // Als Wahrheitswert: shouldContain druckte das ganze PDF in die Meldung.
            withClue("/PageMode/UseNone fehlt im PDF") { ("/PageMode/UseNone" in bytes) shouldBe true }
            withClue("/Outlines steht im PDF") { ("/Outlines" in bytes) shouldBe false }
            // #480: Was über den Rand steht, meldet das Kommando nur als Hinweis.
            output.filter { "über den Rand" in it } shouldBe emptyList()
            sheet.verify(calculated, design)
        }
        return Typeset(calculated, sheet)
    }

    /**
     * Die Zeichenprobe (#479): je Währung eine Zeile mit einem positiven und
     * einem negativen Betrag, gesetzt mit Grundlage, Schrift und Sprachdatei
     * des Designs – ein Lauf je Sprachdatei, gleich wie viele Währungen es
     * gibt. Die Präambel kommt vom Renderer, abgeschrieben prüfte die Probe
     * eine andere Vorlage.
     */
    fun typesetCurrencySample(
        design: Design,
        language: Language,
    ): List<String> {
        val input = workDir.resolve("zeichenprobe-${language.tag}.yaml")
        input.writeText("sprache: ${language.tag}\n" + Path.of("src/test/resources/rechnung.yaml").readText())
        val calculated = CalculatedInvoice(YamlLoader.loadInvoice(input), YamlLoader.loadSellerConfig(design.sellerConfig))
        val rendered = InvoiceRenderer.render(calculated.accepted()).sources

        val amounts = listOf(Money("1000.00"), Money("-200.00"))
        val body =
            Currency.entries.joinToString("\n") { currency ->
                val row = amounts.joinToString("\\quad") { "\\betrag{${it.value.toPlainString()}}" }
                "\\begingroup\\def\\waehrung{${currency.name}}$row\\endgroup\\par"
            }
        val tex =
            rendered.tex.substringBefore("\\begin{document}") +
                "\\begin{document}\n\\thispagestyle{empty}\n$body\n\\end{document}\n"
        val sources = PdfSources(tex, rendered.xmpMetadata, rendered.creationDate, rendered.files)
        // Ein eigenes Verzeichnis je Lauf: Die Sprachdatei heißt in jedem Design gleich.
        val pdf =
            LatexCompiler(LuaLatex(ExternalTool))
                .compile(createTempDirectory(workDir, "zeichenprobe"), "zeichenprobe-${design.name}-${language.tag}", sources)
                .pdf

        val lines =
            run
                .textOf(pdf)
                .lines()
                .filter { it.isNotBlank() }
                .map { Sheet.normalize(it) }
        lines shouldBe
            Currency.entries.map { currency ->
                val spelling = Spelling(language, currency)
                amounts.joinToString(" ") { spelling.withSymbol(it) }
            }
        return lines
    }
}

data class Typeset(
    val calculated: CalculatedInvoice,
    val sheet: Sheet,
)

/** Der Text von `pdftotext`; der schließt jede Seite mit einem Seitenvorschub ab. */
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

    fun pagesWith(vararg spellings: String): Set<Int> =
        (1..pages).filter { page -> spellings.any { normalize(it) in pageTexts[page - 1] } }.toSet()

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
        // \s kennt weder U+00A0 noch die schmalen Leerzeichen. Das Minuszeichen
        // nicht mit "-" gleichsetzen, sonst ginge ein Bindestrich durch (#320).
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

    // §14 Abs. 4 Nr. 1 UStG. Ohne Geschäftsführung ist der vollständige Name
    // die Person, nicht die Firmierung.
    shows("Rechnungssteller", config.companyName)
    if (config.managingDirector == null) shows("Name des Inhabers", config.ownerName)
    shows("Anschrift Rechnungssteller", config.address.street)
    shows("Ort Rechnungssteller", "${config.address.postalCode} ${config.address.city}")
    shows("Kunde", invoice.customer.name)
    shows("Anschrift Kunde", invoice.customer.address.street)
    shows("Ort Kunde", "${invoice.customer.address.postalCode} ${invoice.customer.address.city}")
    // BT-48, Pflicht bei AE und K (§14a Abs. 1 UStG).
    invoice.customer.vatId?.let { shows("USt-IdNr. des Kunden", it.value, it.formatted()) }

    // Nr. 2: eine von beiden genügt.
    shows(
        "Steuernummer oder USt-IdNr.",
        *listOfNotNull(config.vatId.value, config.vatId.formatted(), config.taxNumber?.value).toTypedArray(),
    )

    // Nr. 3 und 4
    shows("Rechnungsdatum", *spelling.date(invoice.issueDate))
    shows("Rechnungsnummer", invoice.number)

    // Nr. 5 und 6
    invoice.items.forEachIndexed { i, item ->
        shows("Bezeichnung der Position ${i + 1}", item.name)
        shows("Menge der Position ${i + 1}", spelling.quantity(item.quantity))
    }
    // Keine Pflichtangaben (#14), aber nur der Einzelpreis trägt mehr als zwei
    // Nachkommastellen (ADR-0021).
    calculated.lines.forEachIndexed { i, line ->
        shows("Einzelpreis der Position ${i + 1}", spelling.price(line.item.unitPrice))
        shows("Betrag der Position ${i + 1}", spelling.amount(line.net))
    }
    // Keine Pflichtangabe, weil schon im Entgelt berücksichtigt (Nr. 7); die
    // Spalte steht trotzdem genau dann, wenn eine Position einen Rabatt hat (#213).
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

    // Nr. 7 und 8, bei Befreiung der Hinweis statt Satz und Steuer (#218).
    // Befreit heißt: mit Grund, nicht Steuer null – Z zeigt Satz und Steuer.
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

    // Art. 230 MwStSystRL (#418); Zeichen und Stellung prüft der Sonderfall.
    calculated.vatTotalInEuro?.let { euro ->
        val conversion = checkNotNull(invoice.conversion)
        shows("Umsatzsteuer in Euro", spelling.amount(euro))
        shows("Umrechnungskurs", spelling.quantity(conversion.rate.value))
        shows("Monat des Kurses", spelling.month(conversion.month))
    }

    // §14 Abs. 5 Satz 2 UStG
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

    // Die Werte nicht über den InvoiceVariableResolver holen: Ein falsches
    // Datum von ihm sähe der Test sonst als richtig an.
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

    // §14 Abs. 4 Satz 1 Nr. 7 UStG; die Frist rechnet der Test selbst, wie
    // die Werte des Pflichtsatzes.
    val skonto = invoice.skonto.map { stufe -> spelling.rate(stufe.rate) to spelling.date(invoice.issueDate.plusDays(stufe.days.toLong())) }
    skonto.forEach { (rate, term) ->
        shows("Skontosatz", *rate)
        shows("Skontofrist", *term)
    }

    // An den Beträgen und nicht an `dueDate`: Ein Zahlungsziel, obwohl nichts
    // offen ist, sähe der Zweig oben als Forderung an.
    val settled = calculated.prepayments.isNotEmpty() && calculated.remainingAmount.isZero()
    val settledNote = settledNote(invoice.language)
    if (settled) {
        shows("Satz zum ausgeglichenen Restbetrag", settledNote)
        lacks("Beschriftung des Zahlungsziels", words.dueDateLabel)
        bankHeading?.let { lacks("Überschrift der Bankverbindung", it) }
    } else {
        lacks("Satz zum ausgeglichenen Restbetrag", settledNote)
    }

    // Die Folgeseiten (ADR-0020). Vom Register nur die Nummer: Das Gericht
    // heißt wie ein Ort und stünde meist schon mit der Anschrift da.
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

        // #318
        invoice.items
            .mapNotNull { pageWith(it.name) }
            .distinct()
            .forEach { showsOnPage(it, "Spaltenkopf", words.columnHeading) }
        pageWith(invoice.items.last().name)?.let {
            showsOnPage(it, "Gesamtbetrag bei der letzten Position", spelling.amount(calculated.grossTotal))
        }

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

        calculated.vatGroups.forEach { group ->
            val reason = group.exemptionReason ?: return@forEach
            val reasonPage = pageWith(reason)
            withClue("Befreiungsgrund steht auf keiner Seite vollständig: $reason") { (reasonPage != null) shouldBe true }
            if (reasonPage != null) showsOnPage(reasonPage, "Entgelt beim Befreiungsgrund", spelling.amount(group.base))
        }

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

// Abgeschrieben, nicht aus `DeductionBlocks` gelesen: Wird das Netz hier rot,
// ist das eine Frage wie bei den Vergleichsdateien.
private fun settledNote(language: Language): String =
    when (language) {
        Language.GERMAN -> {
            "Der Rechnungsbetrag ist durch die vereinnahmten Abschläge ausgeglichen; es verbleibt kein Restbetrag."
        }
        Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> {
            "The invoice amount is settled by the advance payments received; no balance remains."
        }
    }

// Abgeschrieben, nicht aus `TaxCategory` gelesen, sonst sähe verify einen
// falschen Wortlaut als richtig an (#427).
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

/**
 * Jede Schreibweise, die ein Design wählen darf, unabhängig von `DateFormat`.
 *
 * Das Minuszeichen ist U+2212, nicht der Bindestrich des JDK (#320). Beträge
 * ohne Währungszeichen, außer negativ im Englischen: `−€200.00` (#409); ganz
 * mit Zeichen in [withSymbol].
 */
private class Spelling(
    private val language: Language,
    currency: Currency,
) {
    // Samt Abstand, wie es vor der Zahl steht: Sonst ginge `PLN1,000.00` durch (#479).
    private val symbol =
        when (currency) {
            Currency.EUR -> {
                "€"
            }
            Currency.USD -> {
                "$"
            }
            Currency.GBP -> {
                "£"
            }
            Currency.PLN, Currency.CZK, Currency.RON, Currency.SEK, Currency.NOK, Currency.CHF -> {
                when (language) {
                    Language.GERMAN -> currency.name
                    Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> "${currency.name} "
                }
            }
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

    fun amount(money: Money): String =
        when (language) {
            Language.GERMAN -> {
                number(money.value, minFraction = 2)
            }
            Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> {
                if (money.isNegative()) withSymbol(money) else number(money.value, minFraction = 2)
            }
        }

    fun withSymbol(money: Money): String {
        val digits = number(money.value.abs(), minFraction = 2)
        val minus = if (money.isNegative()) "\u2212" else ""
        return when (language) {
            Language.GERMAN -> "$minus$digits $symbol"
            Language.BRITISH_ENGLISH, Language.AMERICAN_ENGLISH -> "$minus$symbol$digits"
        }
    }

    fun price(price: Price): String = number(price.value, minFraction = 2)

    fun quantity(value: BigDecimal): String = number(value, minFraction = 0)

    fun rate(percent: Percent): Array<String> {
        val formatted = number(percent.valueAsPercentage, minFraction = 0)
        return arrayOf("$formatted %", "$formatted%")
    }

    fun date(value: LocalDate): Array<String> = arrayOf(short.format(value), long.format(value))

    fun month(value: YearMonth): String = monthYear.format(value)

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

    /** Ganze Kalendermonate auch als Monate (§31 Abs. 4 UStDV, UStAE 14.8 Abs. 1 und 4). */
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
