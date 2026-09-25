package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceVariable
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.core.model.exampleInvoices
import de.fluchitconsulting.rechnungsgenerator.core.text.TextTemplate
import de.fluchitconsulting.rechnungsgenerator.core.text.TextValue
import de.fluchitconsulting.rechnungsgenerator.core.text.VariableResolver
import de.fluchitconsulting.rechnungsgenerator.core.values.Discount
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
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
import kotlin.io.path.readLines
import kotlin.io.path.writeText

/**
 * Ein Design, wie das Netz es sieht: die Stammdaten-Datei, die es wählt, und
 * die Wörter, an denen sich zeigt, dass etwas *fehlt*.
 *
 * Diese Wörter stehen in der Vorlage und nicht in der Rechnung, deshalb gehören
 * sie zum Design. Ob eine Angabe dasteht, lässt sich an ihrem Wert prüfen; ob
 * sie fehlt, nur an ihrer Beschriftung – die IBAN etwa steht im Fuß jedes
 * Dokuments, die Überschrift der Bankverbindung nicht.
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
 * einzeln zurück (#213).
 *
 * [continuationNote] und [pageNumber] sind die Wörter der Folgeseiten
 * (ADR-0020): der Satz am Ende jeder Seite, auf die eine weitere folgt, und
 * die Seitenzahl im Kopf jeder Folgeseite. Beides fehlt auf einer Rechnung
 * mit einer Seite.
 *
 * [greeting] steht zwischen Schlusssatz und Name; null, wenn das Design
 * keine setzt – prag schließt mit dem Schlusssatz.
 *
 * [closingLineAtPaymentTerm]: Das Design nennt die Zahlungsfrist als Satz
 * unter den Summen und hält den Schlusssatz daran fest, damit er nicht allein
 * auf der letzten Seite steht. In helsinki steht die Frist im Kopf, und den
 * Schlusssatz hält der Gruß.
 *
 * [columnHeading] ist ein Wort aus dem Kopf der Positionstabelle, so wie
 * `pdftotext` es liest. Es steht auf jeder Seite, die Positionen trägt.
 *
 * [pages] nennt je Beispieldatei, wie viele Seiten sie in diesem Design hat,
 * sobald es mehr als eine sind; jede andere hat eine. Nachgemessen und nicht
 * hergeleitet: "Mehr als eine" übersähe eine Seite mehr oder weniger (#337),
 * und eine gewöhnliche Rechnung, die still auf zwei Seiten wüchse, sähe nicht
 * mehr aus wie bisher (ADR-0020).
 */
private data class Design(
    val name: String,
    val sellerConfig: Path,
    val dueDateLabel: String,
    val bankHeading: String?,
    val discountHeading: String,
    val continuationNote: String,
    val pageNumber: (page: Int, total: Int) -> String,
    val greeting: String?,
    val closingLineAtPaymentTerm: Boolean,
    val columnHeading: String,
    val pages: Map<String, Int>,
)

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
 * Dieser Test fragt etwas anderes: ob auf dem Blatt steht, was dort stehen
 * muss – §14 Abs. 4 und 5 UStG, §35a GmbHG, der Pflichtsatz der Art. Deshalb
 * kennt er keine Schreibweise eines Designs:
 *
 *  - Die erwarteten Werte kommen aus der gerechneten Rechnung und den
 *    Stammdaten, nicht als abgeschriebene Zeichenketten.
 *  - Formatiert werden sie hier, nicht mit `GermanFormat` – das liefert LaTeX,
 *    und es ist die Schreibweise *eines* Designs.
 *  - Wo Designs verschieden schreiben dürfen, gilt jede der Schreibweisen:
 *    Datum kurz oder lang, Zeitraum in jeder der Formen, Beträge ohne €, weil
 *    ein Design das Zeichen in den Spaltenkopf stellen darf.
 *  - Leerraum zählt nicht: `pdftotext` bricht Zeilen, wo der Satz sie bricht,
 *    und ein geschütztes Leerzeichen ist nur ein anderes Leerzeichen.
 */
class PflichtangabenTest : FunSpec({
    tags(Toolchain)

    val workDir = createTempDirectory("rechnungsgenerator-pflichtangaben")
    val run = ToolchainRun(workDir)
    val sellerConfig = Path.of("src/test/resources/stammdaten.yaml")

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

    // Ein weiteres Design ist hier eine weitere Zeile.
    val designs =
        listOf(
            Design(
                name = "helsinki",
                sellerConfig = sellerConfig,
                dueDateLabel = "Zahlbar ohne Abzug bis",
                bankHeading = "Bankverbindung",
                discountHeading = "R A B AT T",
                continuationNote = "Fortsetzung auf der nächsten Seite",
                pageNumber = { page, total -> "Seite $page von $total" },
                greeting = "Mit freundlichen Grüßen",
                closingLineAtPaymentTerm = false,
                columnHeading = "EINZELPREIS",
                pages =
                    mapOf(
                        "viele-positionen" to 3,
                        "schluss-an-der-seitengrenze" to 3,
                        "befreiungsgrund-an-der-seitengrenze" to 3,
                    ),
            ),
            Design(
                name = "prag",
                // prag setzt das Logo; so sieht veraPDF es bei jeder Rechnungsart.
                sellerConfig = sellerConfigWith("prag", logo = true),
                dueDateLabel = "Die Zahlung ist",
                bankHeading = null,
                discountHeading = "Rabatt",
                continuationNote = "Fortsetzung auf der nächsten Seite",
                pageNumber = { page, total -> "Seite $page von $total" },
                greeting = null,
                closingLineAtPaymentTerm = true,
                columnHeading = "Einzelpreis",
                pages =
                    mapOf(
                        "viele-positionen" to 3,
                        "schluss-an-der-seitengrenze" to 3,
                        "befreiungsgrund-an-der-seitengrenze" to 3,
                    ),
            ),
        )

    test("jedes Design steht im Netz") {
        designs.map { it.name } shouldBe
            de.fluchitconsulting.rechnungsgenerator.core.values.Design.entries
                .map { it.label }
    }

    // Setzt eine Beispieldatei mit den echten Werkzeugen und prüft das Blatt.
    fun typesetAndCheck(
        file: String,
        design: Design,
        sellerConfig: Path,
        targetName: String,
    ): Typeset {
        val input = Path.of("src/test/resources/$file.yaml")
        val calculated = CalculatedInvoice(YamlLoader.loadInvoice(input), YamlLoader.loadSellerConfig(sellerConfig))

        val (pdf, output) = run.generate(file, targetName = targetName, sellerConfig = sellerConfig)
        withClue(output.joinToString("\n")) { output shouldContain "PDF/A-1b: gültig" }

        val sheet = Sheet(run.textOf(pdf))
        assertSoftly {
            withClue("Seitenzahl") { sheet.pages shouldBe (design.pages[file] ?: 1) }
            sheet.verify(calculated, design)
        }
        return Typeset(calculated, sheet)
    }

    for (design in designs) {
        for ((art, file) in exampleInvoices) {
            test("$file im Design ${design.name}: gültiges PDF/A-1b mit allen Pflichtangaben") {
                val (calculated, _) = typesetAndCheck(file, design, design.sellerConfig, targetName = "${design.name}-$file")
                calculated.invoice.type shouldBe art
            }
        }
    }

    // Mehrere Steuersätze (#218): §14 Abs. 4 Nr. 7 verlangt das Entgelt je
    // Satz. In den Beispielen je Art hat jeder Satz genau eine Position, und
    // deren Betrag stünde zufällig als Positionsbetrag da – hier je Satz zwei,
    // damit das Entgelt nur über den Summenblock auf das Blatt kommen kann.
    // Dazu eine befreite Position (Hinweis statt Satz und Steuerbetrag) und
    // eine mit Nullsatz (Satz und Steuerbetrag, keine Befreiung).
    for (design in designs) {
        test("gemischte Steuersätze im Design ${design.name}: gültig, mit dem Entgelt je Steuersatz") {
            val (calculated, _) =
                typesetAndCheck("gemischte-steuersaetze", design, design.sellerConfig, targetName = "${design.name}-gemischt")

            // 19 %, 7 %, steuerbefreit, Nullsatz – die vier Fälle nebeneinander.
            calculated.vatGroups.size shouldBe 4
        }
    }

    // Skonto (#342): §14 Abs. 4 Satz 1 Nr. 7 verlangt die im Voraus
    // vereinbarte Minderung. Keine Rechnung einer Art trägt eines, deshalb
    // eine eigene Datei, mit zwei Stufen.
    for (design in designs) {
        test("Skonto im Design ${design.name}: gültig, mit Satz und Frist jeder Stufe") {
            val (calculated, _) = typesetAndCheck("skonto", design, design.sellerConfig, targetName = "${design.name}-skonto")

            calculated.invoice.skonto.size shouldBe 2
        }
    }

    // Eine Schlussrechnung, deren Abschläge den Gesamtbetrag genau decken
    // (#342): Die Schlussrechnung der Arten hat einen Restbetrag, und ohne
    // diese Datei entstünden der Satz statt der Null und der Wegfall von
    // Zahlungsziel und Bankverbindung in keinem PDF.
    for (design in designs) {
        test("ausgeglichene Schlussrechnung im Design ${design.name}: gültig, ohne Zahlungsaufforderung") {
            val (calculated, _) =
                typesetAndCheck(
                    "schlussrechnung-ausgeglichen",
                    design,
                    design.sellerConfig,
                    targetName = "${design.name}-ausgeglichen",
                )

            calculated.prepayments.size shouldBe 2
            calculated.remainingAmount.isZero() shouldBe true
        }
    }

    // Mehr Positionen, als auf eine Seite passen (#318, ADR-0020): Jede steht
    // auf dem Blatt, und das Blatt hat so viele Seiten, wie das Design für
    // diese Datei nennt. Was die Folgeseiten tragen, prüft `verify` an jeder
    // mehrseitigen Rechnung; hier brechen die Positionen über zwei Seiten.
    for (design in designs) {
        test("viele-positionen im Design ${design.name}: gültig, jede Position auf dem Blatt, über mehrere Seiten") {
            val (calculated, _) =
                typesetAndCheck("viele-positionen", design, design.sellerConfig, targetName = "${design.name}-viele")

            calculated.invoice.items.size shouldBe 40
        }
    }

    // Der Schluss an der Seitengrenze (ADR-0020): Die Positionen füllen die
    // zweite Seite so weit, dass sie ohne Zusammenhalt in helsinki zwischen
    // Schlusssatz und Grußformel bräche und in prag zwischen Zahlungsfrist
    // und Schlusssatz. Was dann beisammenstehen muss, prüft
    // `verify` an jeder mehrseitigen Rechnung; diese Datei ist die, bei der
    // es darauf ankommt.
    for (design in designs) {
        test("Schluss an der Seitengrenze im Design ${design.name}: der Schluss bricht nicht auseinander") {
            typesetAndCheck("schluss-an-der-seitengrenze", design, design.sellerConfig, targetName = "${design.name}-grenze")
        }
    }

    // Ein langer Befreiungsgrund an der Seitengrenze (ADR-0020, #335): Ohne
    // Zusammenhalt endete die zweite Seite nach der ersten Zeile des Hinweises.
    // Dass er ganz auf einer Seite steht, prüft `verify` an jeder mehrseitigen
    // Rechnung; diese Datei ist die, bei der es darauf ankommt.
    for (design in designs) {
        test("Befreiungsgrund an der Seitengrenze im Design ${design.name}: der Hinweis bricht nicht auseinander") {
            val (calculated, _) =
                typesetAndCheck(
                    "befreiungsgrund-an-der-seitengrenze",
                    design,
                    design.sellerConfig,
                    targetName = "${design.name}-befreiung",
                )

            calculated.vatGroups.count { it.exemptionReason != null } shouldBe 1
        }
    }

    // Die Stammdaten oben führen eine Geschäftsführung, und prag läuft dort
    // mit Logo. Zwei Zweige der Vorlagen erreichten so nie ein echtes PDF: der
    // Name des Inhabers, der nur ohne Geschäftsführung dasteht, und prag
    // ohne Logo, der Fall ohne Eintrag. Beide hängen an den Stammdaten und
    // nicht an der Rechnungsart, deshalb genügt die gewöhnliche Rechnung.
    for (design in designs) {
        test("rechnung im Design ${design.name} für ein Einzelunternehmen ohne Logo: gültig, mit dem Namen des Inhabers") {
            val soleProprietorship = sellerConfigWith(design.name, logo = false, withManagingDirector = false)

            val (calculated, _) = typesetAndCheck("rechnung", design, soleProprietorship, targetName = "${design.name}-einzel")

            calculated.config.managingDirector shouldBe null
            calculated.config.logo shouldBe null
        }
    }
})

/** Eine gesetzte Rechnung: das gerechnete Ergebnis und das zurückgelesene Blatt. */
private data class Typeset(
    val calculated: CalculatedInvoice,
    val sheet: Sheet,
)

/**
 * Der zurückgelesene Text, mit zusammengefasstem Leerraum – im Ganzen und je
 * Seite. `pdftotext` schließt jede Seite mit einem Seitenvorschub ab; daran
 * zählt der Test die Seiten, ohne ein weiteres Werkzeug.
 */
private class Sheet(
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

    // Nr. 2: Steuernummer oder USt-IdNr. – eine genügt.
    shows(
        "Steuernummer oder USt-IdNr.",
        *listOfNotNull(config.vatId.value, config.vatId.formatted(), config.taxNumber?.value).toTypedArray(),
    )

    // Nr. 3 und 4: Rechnungsdatum und Rechnungsnummer.
    shows("Rechnungsdatum", *date(invoice.issueDate))
    shows("Rechnungsnummer", invoice.number)

    // Nr. 5 und 6: Menge und Art der Leistung, Zeitpunkt der Leistung und –
    // bei einer Abschlagsrechnung, wenn er feststeht – der Vereinnahmung.
    invoice.items.forEachIndexed { i, item ->
        shows("Bezeichnung der Position ${i + 1}", item.name)
        shows("Menge der Position ${i + 1}", quantity(item.quantity))
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
                shows("Rabatt der Position ${i + 1}", *rate(discount.rate))
                shows("Betrag der Position ${i + 1}", amount(line.net))
            }
            is Discount.Amount -> {
                shows("Rabatt der Position ${i + 1}", amount(discount.amount))
                shows("Betrag der Position ${i + 1}", amount(line.net))
            }
        }
    }
    if (invoice.items.any { it.discount != null }) {
        shows("Kopf der Rabattspalte", design.discountHeading)
    } else {
        lacks("Kopf der Rabattspalte", design.discountHeading)
    }
    shows("Leistungszeitraum", *zeitraum(invoice.servicePeriod.start, invoice.servicePeriod.end))
    invoice.receivedOn?.let { shows("Tag der Vereinnahmung", *date(it)) }

    // Nr. 7 und 8: Entgelt je Steuersatz, dazu Satz und Steuerbetrag – oder,
    // bei einer Steuerbefreiung, der Hinweis darauf statt beider (Nr. 8
    // zweiter Halbsatz, #218). Das Entgelt verlangt Nr. 7 in jedem Fall; bei
    // einer befreiten Gruppe nennt es der Hinweis selbst. Ob eine Gruppe
    // befreit ist, sagt ihr Grund und nicht ihr Steuerbetrag von null: Die
    // Kategorie Z trägt keinen Grund und bleibt deshalb eine Gruppe, die Satz
    // und Steuerbetrag zeigen muss.
    calculated.vatGroups.forEach { group ->
        val name = "Steuergruppe ${group.category} ${group.rate}"
        shows("$name: Entgelt", amount(group.base))
        val reason = group.exemptionReason
        if (reason == null) {
            shows("$name: Satz", *rate(group.rate))
            shows("$name: Steuerbetrag", amount(group.amount))
        } else {
            shows("$name: Hinweis auf die Steuerbefreiung", reason)
        }
    }

    // §14 Abs. 5 Satz 2: Die Schlussrechnung setzt Entgelt und Steuer der
    // vereinnahmten Abschläge ab.
    calculated.prepayments.forEach { abschlag ->
        val number = abschlag.prepayment.invoiceNumber
        shows("Abschlag $number", number)
        shows("Abschlag $number: Entgelt", amount(abschlag.net))
        shows("Abschlag $number: Steuer", amount(abschlag.vat))
    }

    // §35a GmbHG, soweit die Stammdaten die Angaben führen.
    config.registerCourt?.let { shows("Registergericht", it) }
    config.registerNumber?.let { shows("Handelsregisternummer", it) }
    config.managingDirector?.let { shows("Geschäftsführer", it) }

    // Der Pflichtsatz der Art, mit eingesetzten Werten.
    // Die Werte holt der Test selbst und nicht über den InvoiceVariableResolver:
    // Holte der das falsche Datum, sähe der Test sonst dasselbe falsche Datum.
    val mandatoryNote =
        invoice.type.mandatoryNote?.let { template ->
            val values =
                VariableResolver<InvoiceVariable> { variable ->
                    val original = checkNotNull(invoice.cancelled) { "<<$variable>> ohne Ursprungsrechnung" }
                    when (variable) {
                        InvoiceVariable.URSPRUNGSNUMMER -> TextValue.Text(original.invoiceNumber)
                        InvoiceVariable.URSPRUNGSDATUM -> TextValue.Date(original.issueDate)
                    }
                }
            listOf(template.plainText(values, SHORT), template.plainText(values, LONG))
        }
    mandatoryNote?.let { shows("Pflichtsatz", *it.toTypedArray()) }

    // Zahlungsziel und Bankverbindung genau dann, wenn das Dokument Zahlung
    // fordert. Die Abwesenheit zeigt sich an der Beschriftung: Ein Design, das
    // sie ohne Wert stehen ließe, fiele hier auf.
    val dueDate = calculated.dueDate
    val bankHeading = design.bankHeading
    if (dueDate != null) {
        shows("Zahlungsziel", *date(dueDate), *term(calculated.paymentTermDays))
        bankHeading?.let { shows("Überschrift der Bankverbindung", it) }
        shows("IBAN", config.bank.iban.formatted())
        shows("BIC", config.bank.bic.value)
    } else {
        lacks("Beschriftung des Zahlungsziels", design.dueDateLabel)
        bankHeading?.let { lacks("Überschrift der Bankverbindung", it) }
    }

    // §14 Abs. 4 Satz 1 Nr. 7: das im Voraus vereinbarte Skonto, je Stufe mit
    // Satz und Frist. Die Frist rechnet der Test selbst, aus demselben Grund
    // wie die Werte des Pflichtsatzes.
    val skonto = invoice.skonto.map { stufe -> rate(stufe.rate) to date(invoice.issueDate.plusDays(stufe.days.toLong())) }
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
    if (settled) {
        shows("Satz zum ausgeglichenen Restbetrag", SETTLED_NOTE)
        lacks("Beschriftung des Zahlungsziels", design.dueDateLabel)
        bankHeading?.let { lacks("Überschrift der Bankverbindung", it) }
    } else {
        lacks("Satz zum ausgeglichenen Restbetrag", SETTLED_NOTE)
    }

    // Die Folgeseiten (ADR-0020): Jede trägt Rechnungsnummer, Datum und
    // Seitenzahl, damit eine lose Seite zu ihrer Rechnung findet, und den Fuß
    // des Briefbogens mit Anschrift, Register, USt-IdNr. und Bankverbindung.
    // Vom Register prüft das Netz nur die Nummer: Das Registergericht heißt
    // wie ein Ort und stünde meist schon mit der Anschrift da. Jede Seite, auf
    // die eine folgt, sagt das am Ende. Eine Rechnung mit einer Seite trägt
    // nichts davon – „Seite 1 von 1“ sagte nichts.
    if (pages == 1) {
        lacks("Seitenzahl", design.pageNumber(1, 1))
        lacks("Fortsetzungshinweis", design.continuationNote)
    } else {
        for (page in 1..pages) {
            if (page < pages) {
                showsOnPage(page, "Fortsetzungshinweis", design.continuationNote)
            } else {
                lacksOnPage(page, "Fortsetzungshinweis", design.continuationNote)
            }
            showsOnPage(page, "Rechnungssteller im Fuß", config.companyName)
            showsOnPage(page, "USt-IdNr. im Fuß", config.vatId.value, config.vatId.formatted())
            showsOnPage(page, "Anschrift im Fuß", config.address.street)
            showsOnPage(page, "Ort im Fuß", "${config.address.postalCode} ${config.address.city}")
            config.registerNumber?.let { showsOnPage(page, "Handelsregisternummer im Fuß", it) }
            showsOnPage(page, "IBAN im Fuß", config.bank.iban.formatted())
            showsOnPage(page, "BIC im Fuß", config.bank.bic.value)
            if (page == 1) continue
            showsOnPage(page, "Seitenzahl", design.pageNumber(page, pages))
            showsOnPage(page, "Rechnungsnummer", invoice.number)
            showsOnPage(page, "Rechnungsdatum", *date(invoice.issueDate))
        }

        // Jede Seite mit Positionen trägt den Spaltenkopf, und die Summen
        // stehen bei der letzten Position: Summen ohne das, was sie summieren,
        // waren der Befund aus #318.
        invoice.items
            .mapNotNull { pageWith(it.name) }
            .distinct()
            .forEach { showsOnPage(it, "Spaltenkopf", design.columnHeading) }
        pageWith(invoice.items.last().name)?.let {
            showsOnPage(it, "Gesamtbetrag bei der letzten Position", amount(calculated.grossTotal))
        }

        // Schlusssatz, Grußformel und Name brechen nicht auseinander: Der Satz
        // steht ganz auf einer Seite, und Gruß und Name stehen auf derselben.
        val closingLine = invoice.note ?: config.closingLine
        val page = pageWith(closingLine)
        withClue("Schlusssatz steht auf keiner Seite vollständig: $closingLine") { (page != null) shouldBe true }
        val greeting = design.greeting
        if (page != null && greeting != null) {
            showsOnPage(page, "Grußformel beim Schlusssatz", greeting)
            showsOnPage(page, "Name beim Schlusssatz", config.ownerName)
        }
        // Ohne Skonto ist die Zahlungsfrist der Satz direkt vor dem Schlusssatz.
        if (page != null && design.closingLineAtPaymentTerm && dueDate != null && invoice.skonto.isEmpty()) {
            showsOnPage(page, "Zahlungsfrist beim Schlusssatz", design.dueDateLabel)
        }

        // Kein Hinweis auf eine Steuerbefreiung bricht über zwei Seiten: Der
        // Grund steht ganz auf einer Seite, und das Entgelt, mit dem der Satz
        // beginnt, auf derselben.
        calculated.vatGroups.forEach { group ->
            val reason = group.exemptionReason ?: return@forEach
            val reasonPage = pageWith(reason)
            withClue("Befreiungsgrund steht auf keiner Seite vollständig: $reason") { (reasonPage != null) shouldBe true }
            if (reasonPage != null) showsOnPage(reasonPage, "Entgelt beim Befreiungsgrund", amount(group.base))
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
                (pageWith(SETTLED_NOTE) != null) shouldBe true
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
            val dueDatePage = pageWith(design.dueDateLabel)
            withClue("Beschriftung des Zahlungsziels auf keiner Seite") { (dueDatePage != null) shouldBe true }
            if (dueDatePage != null) {
                showsOnPage(
                    dueDatePage,
                    "Zahlungsziel bei seiner Beschriftung",
                    *date(dueDate),
                    *term(calculated.paymentTermDays),
                )
            }
        }
    }
}

/**
 * Der Satz, der bei ausgeglichenem Restbetrag statt einer Zahl dasteht.
 * Abgeschrieben und nicht aus `DeductionBlocks` gelesen: Ändert sich der
 * Wortlaut, wird das Netz rot, und das ist eine Frage wie bei den
 * Vergleichsdateien. Er ist ein Satz des Programms und keiner eines Designs,
 * deshalb steht er hier und nicht in [Design].
 */
private const val SETTLED_NOTE =
    "Der Rechnungsbetrag ist durch die vereinnahmten Abschläge ausgeglichen; es verbleibt kein Restbetrag."

// --- Schreibweisen ----------------------------------------------------------
// Bewusst unabhängig von GermanFormat, siehe oben. Jede Funktion liefert alle
// Schreibweisen, die ein Design wählen darf.

// Eine negative Zahl trägt das Minuszeichen U+2212, in jedem Design (#320).
// Hier eigens gesetzt und nicht aus GermanFormat übernommen: Es ist die
// Erwartung an das Blatt, und Locale.GERMANY brächte den Bindestrich mit.
private val SYMBOLS = DecimalFormatSymbols(Locale.GERMANY).apply { minusSign = '\u2212' }

private fun number(
    value: BigDecimal,
    minFraction: Int,
): String =
    DecimalFormat("#,##0", SYMBOLS)
        .apply {
            minimumFractionDigits = minFraction
            maximumFractionDigits = maxOf(value.stripTrailingZeros().scale(), minFraction)
        }.format(value)

private fun amount(money: Money): String = number(money.value, minFraction = 2)

private fun quantity(value: BigDecimal): String = number(value, minFraction = 0)

private fun rate(percent: Percent): Array<String> {
    val formatted = number(percent.valueAsPercentage, minFraction = 0)
    return arrayOf("$formatted %", "$formatted%")
}

private val SHORT = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)
private val LONG = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMANY)
private val MONTH = DateTimeFormatter.ofPattern("MMMM", Locale.GERMANY)
private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMANY)

private fun date(value: LocalDate): Array<String> = arrayOf(SHORT.format(value), LONG.format(value))

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

/** Das Zahlungsziel als Frist ab Rechnungsdatum statt als Datum (#211). */
private fun term(days: Int): Array<String> =
    when (days) {
        0 -> arrayOf("sofort fällig")
        1 -> arrayOf("1 Tag nach Rechnungsdatum")
        else -> arrayOf("$days Tage nach Rechnungsdatum")
    }

/**
 * Ein Zeitraum auf den Tag genau oder – wo er ganze Kalendermonate füllt – in
 * Monaten (§31 Abs. 4 UStDV, UStAE 14.8 Abs. 1 und 4).
 */
private fun zeitraum(
    start: LocalDate,
    end: LocalDate,
): Array<String> {
    val days = listOf("–", "-", "bis").map { "${SHORT.format(start)} $it ${SHORT.format(end)}" }
    val wholeMonths = start.dayOfMonth == 1 && end == end.withDayOfMonth(end.lengthOfMonth())
    if (!wholeMonths) return days.toTypedArray()
    val months =
        if (YearMonth.from(start) == YearMonth.from(end)) {
            listOf(MONTH_YEAR.format(start))
        } else {
            listOf(
                "${MONTH.format(start)} bis ${MONTH_YEAR.format(end)}",
                "${MONTH_YEAR.format(start)} bis ${MONTH_YEAR.format(end)}",
            )
        }
    return (days + months).toTypedArray()
}
