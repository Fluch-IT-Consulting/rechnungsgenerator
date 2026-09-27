package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import de.fluchitconsulting.rechnungsgenerator.core.accepted.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.accepted.Verdict
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.InvoiceVariable
import de.fluchitconsulting.rechnungsgenerator.core.model.TaxCategory
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.core.model.exampleInvoices
import de.fluchitconsulting.rechnungsgenerator.core.rules.Rule
import de.fluchitconsulting.rechnungsgenerator.core.text.TextTemplate
import de.fluchitconsulting.rechnungsgenerator.core.text.TextValue
import de.fluchitconsulting.rechnungsgenerator.core.text.VariableResolver
import de.fluchitconsulting.rechnungsgenerator.core.values.Discount
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import de.fluchitconsulting.rechnungsgenerator.core.values.Money
import de.fluchitconsulting.rechnungsgenerator.core.values.Percent
import de.fluchitconsulting.rechnungsgenerator.core.values.Price
import de.fluchitconsulting.rechnungsgenerator.generate.einvoice.CiiWriter
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
import kotlin.io.path.readText
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
private data class Design(
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
private data class Words(
    val dueDateLabel: String,
    val bankHeading: String?,
    val discountHeading: String,
    val continuationNote: String,
    val pageNumber: (page: Int, total: Int) -> String,
    val greeting: String?,
    val columnHeading: String,
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
 *  - Formatiert werden sie hier, nicht mit `DateFormat` – das liefert LaTeX,
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
                        Language.BRITISH_ENGLISH to
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
                        Language.BRITISH_ENGLISH to
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

    test("jedes Design steht im Netz, in jeder Sprache") {
        designs.map { it.name } shouldBe
            de.fluchitconsulting.rechnungsgenerator.core.values.Design.entries
                .map { it.label }
        designs.forEach { design -> withClue(design.name) { design.words.keys shouldBe Language.entries.toSet() } }
    }

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

        val sheet = Sheet(run.textOf(pdf))
        assertSoftly {
            withClue("Seitenzahl") { sheet.pages shouldBe (design.pages[file] ?: 1) }
            sheet.verify(calculated, design)
        }
        return Typeset(calculated, sheet)
    }

    for (design in designs) {
        for ((invoiceType, file) in exampleInvoices) {
            test("$file im Design ${design.name}: gültiges PDF/A-1b mit allen Pflichtangaben") {
                val (calculated, _) = typesetAndCheck(file, design, design.sellerConfig, targetName = "${design.name}-$file")
                calculated.invoice.type shouldBe invoiceType
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

        // Dieselbe Datei auf Englisch (#405): Die Beispiele je Art laufen dort
        // unten auch, aber keines ist ausgeglichen.
        test("ausgeglichene Schlussrechnung auf Englisch im Design ${design.name}: gültig, mit dem englischen Satz") {
            val input = workDir.resolve("schlussrechnung-ausgeglichen-englisch.yaml")
            input.writeText("sprache: en-GB\n" + Path.of("src/test/resources/schlussrechnung-ausgeglichen.yaml").readText())

            val (calculated, _) =
                typesetAndCheck(
                    "schlussrechnung-ausgeglichen",
                    design,
                    design.sellerConfig,
                    targetName = "${design.name}-ausgeglichen-englisch",
                    input = input,
                )

            calculated.invoice.language shouldBe Language.BRITISH_ENGLISH
        }
    }

    // Eine Rechnung ins Ausland (#374): Unter dem Ort steht das Land,
    // ausgeschrieben und in Versalien nach DIN 5008, nicht der Code aus der
    // Datei. Der Name steht hier wörtlich und kommt nicht aus dem JDK – sonst
    // prüfte das Netz die Umsetzung gegen sich selbst.
    for (design in designs) {
        test("Auslandsrechnung im Design ${design.name}: gültig, mit dem Land ausgeschrieben unter der Anschrift") {
            val (calculated, sheet) =
                typesetAndCheck("auslandsrechnung", design, design.sellerConfig, targetName = "${design.name}-ausland")

            calculated.isDomestic shouldBe false
            sheet.shows("Land Kunde", "1010 Wien ÖSTERREICH")
        }
    }

    // Eine englische Rechnung (#377) an einen Unternehmer im Vereinigten
    // Königreich, Kategorie O: der häufigste Fall. verify prüft sie mit den
    // Wörtern des Designs und den Schreibweisen der Sprache; das Land und die
    // Einheiten stehen hier wörtlich, aus demselben Grund wie ÖSTERREICH oben.
    for (design in designs) {
        test("englische Rechnung im Design ${design.name}: gültig, mit allen Pflichtangaben auf Englisch") {
            val (calculated, sheet) =
                typesetAndCheck("englische-rechnung", design, design.sellerConfig, targetName = "${design.name}-englisch")

            calculated.invoice.language shouldBe Language.BRITISH_ENGLISH
            sheet.shows("Land Kunde", "EC1A 1BB London UNITED KINGDOM")
            sheet.shows("Einheit, eine Stunde", "1 hr")
            sheet.shows("Einheit, ein Tag", "1 day")
            sheet.shows("Einheit, zwei Tage", "2 days")
            sheet.shows("Einheit, ein halber Tag", "0.5 days")
            sheet.shows("negativer Betrag", "\u2212€200.00")
            sheet.lacks("Deutsch auf dem Blatt", "Rechnung", "Leistungszeitraum", "Umsatzsteuer", "Seite")
        }
    }

    // Reverse Charge auf Englisch (#406), an einen Unternehmer in Österreich:
    // neben O der häufigste Fall einer englischen Rechnung. Der Grund steht
    // hier wörtlich, aus demselben Grund wie ÖSTERREICH oben; dass er
    // überhaupt dasteht und die USt-IdNr. des Kunden auch, prüft verify.
    for (design in designs) {
        test("Reverse Charge auf Englisch im Design ${design.name}: gültig, mit Grund und USt-IdNr. des Kunden") {
            val (calculated, sheet) =
                typesetAndCheck("reverse-charge", design, design.sellerConfig, targetName = "${design.name}-reverse-charge")

            calculated.vatGroups.map { it.category } shouldBe listOf(TaxCategory.REVERSE_CHARGE)
            sheet.shows("Hinweis auf die Steuerschuldnerschaft", "No VAT is charged on €5,000.00: Reverse charge.")
            sheet.lacks("Deutsch auf dem Blatt", "Steuerschuldnerschaft", "Rechnung", "Umsatzsteuer")
        }
    }

    // Jedes Beispiel auf Englisch (#377): Es bricht mit
    // SPRACHE_NOCH_NICHT_MOEGLICH ab, oder es entsteht ein PDF, samt E-Rechnung.
    // Ein Drittes gibt es nicht – die Regel und die werfenden Zweige der Sätze
    // stehen an verschiedenen Stellen, und dieser Test hält sie zusammen.
    // Nimmt die Regel etwas an, dessen Satz noch wirft, bricht er hier mit
    // der Ausnahme ab; lehnt sie etwas ab, das gesetzt werden könnte, ist
    // das ein Befund, den man sieht.
    for ((_, file) in exampleInvoices) {
        test("$file auf Englisch: Abbruch SPRACHE_NOCH_NICHT_MOEGLICH oder ein PDF") {
            val input = workDir.resolve("$file-englisch.yaml")
            input.writeText("sprache: en-GB\n" + Path.of("src/test/resources/$file.yaml").readText())
            val calculated = CalculatedInvoice(YamlLoader.loadInvoice(input), YamlLoader.loadSellerConfig(sellerConfig))

            when (val verdict = AcceptedInvoice.check(calculated)) {
                is Verdict.Rejected -> {
                    verdict.aborts.map { it.rule }.toSet() shouldBe setOf(Rule.SPRACHE_NOCH_NICHT_MOEGLICH)
                }
                is Verdict.Accepted -> {
                    CiiWriter.write(verdict.invoice)
                    designs.forEach { design ->
                        typesetAndCheck(file, design, design.sellerConfig, targetName = "${design.name}-$file-englisch", input = input)
                    }
                }
            }
        }
    }

    // Die Schreibweise der Zahlen (ADR-0021): Die Beispiele je Art tragen
    // Einzelpreise mit zwei Nachkommastellen und Mengen unter 1.000. Wie ein
    // Design längere Zahlen gruppiert, zeigte sich an keinem davon.
    for (design in designs) {
        test("Schreibweise der Zahlen im Design ${design.name}: jeder Einzelpreis mit allen Nachkommastellen") {
            val (calculated, _) =
                typesetAndCheck("schreibweise-der-zahlen", design, design.sellerConfig, targetName = "${design.name}-zahlen")

            calculated.invoice.items.maxOf {
                it.unitPrice.value
                    .stripTrailingZeros()
                    .scale()
            } shouldBe 5
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

    // Die Stammdaten oben führen eine Geschäftsführung, und jedes Design läuft
    // dort mit Logo. Zwei Zweige der Vorlagen erreichten so nie ein echtes PDF:
    // der Name des Inhabers, der nur ohne Geschäftsführung dasteht, und das
    // Blatt ohne Logo, der Fall ohne Eintrag. Beide hängen an den Stammdaten und
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
    val words = design.words.getValue(invoice.language)
    val spelling = Spelling(invoice.language)

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
        Language.GERMAN -> "Der Rechnungsbetrag ist durch die vereinnahmten Abschläge ausgeglichen; es verbleibt kein Restbetrag."
        Language.BRITISH_ENGLISH -> "The invoice amount is settled by the advance payments received; no balance remains."
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
 * Beträge ohne €, weil ein Design das Zeichen in den Spaltenkopf stellen
 * darf. Im Englischen steht es aber zwischen Minus und Zahl, `−€200.00`; ohne
 * € stünde `−200.00` nirgends auf dem Blatt. Ein negativer Betrag nennt es
 * dort deshalb mit.
 */
private class Spelling(
    private val language: Language,
) {
    private val symbols =
        when (language) {
            Language.GERMAN -> DecimalFormatSymbols(Locale.GERMANY)
            Language.BRITISH_ENGLISH -> DecimalFormatSymbols(Locale.UK)
        }.apply { minusSign = '\u2212' }

    val short: DateTimeFormatter =
        when (language) {
            Language.GERMAN -> DateTimeFormatter.ofPattern("dd.MM.yyyy", language.locale)
            Language.BRITISH_ENGLISH -> DateTimeFormatter.ofPattern("yyyy-MM-dd", language.locale)
        }
    val long: DateTimeFormatter =
        when (language) {
            Language.GERMAN -> DateTimeFormatter.ofPattern("d. MMMM yyyy", language.locale)
            Language.BRITISH_ENGLISH -> DateTimeFormatter.ofPattern("d MMMM yyyy", language.locale)
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
            Language.GERMAN -> digits
            Language.BRITISH_ENGLISH -> if (money.isNegative()) "\u2212€" + digits.removePrefix("\u2212") else digits
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
            Language.BRITISH_ENGLISH -> {
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
                Language.BRITISH_ENGLISH -> "to"
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
