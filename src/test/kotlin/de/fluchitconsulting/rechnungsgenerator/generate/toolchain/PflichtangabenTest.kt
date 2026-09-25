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
 * [bankUeberschrift] ist null, wenn das Design keinen eigenen Bankblock setzt:
 * prag führt die Bank nur im Fuß, als Angabe des Briefbogens (ADR-0011).
 * Dann gibt es keine Überschrift, deren Abwesenheit zu prüfen wäre; ob das
 * Dokument Zahlung fordert, zeigt dort allein das Zahlungsziel.
 *
 * Die Beschriftung des Zahlungsziels ist in prag der Anfang des Satzes, der
 * es als Frist nennt (#211), und keine eigene Kopfzeile.
 *
 * [rabattBeschriftung] ist der Kopf der Rabattspalte, so wie `pdftotext` ihn
 * liest: helsinki sperrt seine Spaltenköpfe, und dann kommt jeder Buchstabe
 * einzeln zurück (#213).
 *
 * [fortsetzungshinweis] und [seitenzahl] sind die Wörter der Folgeseiten
 * (ADR-0020): der Satz am Ende jeder Seite, auf die eine weitere folgt, und
 * die Seitenzahl im Kopf jeder Folgeseite. Beides fehlt auf einer Rechnung
 * mit einer Seite.
 *
 * [grussformel] steht zwischen Schlusssatz und Name; null, wenn das Design
 * keine setzt – prag schließt mit dem Schlusssatz.
 *
 * [schlusssatzBeiZahlungsfrist]: Das Design nennt die Zahlungsfrist als Satz
 * unter den Summen und hält den Schlusssatz daran fest, damit er nicht allein
 * auf der letzten Seite steht. In helsinki steht die Frist im Kopf, und den
 * Schlusssatz hält der Gruß.
 *
 * [spaltenkopf] ist ein Wort aus dem Kopf der Positionstabelle, so wie
 * `pdftotext` es liest. Es steht auf jeder Seite, die Positionen trägt.
 *
 * [seiten] nennt je Beispieldatei, wie viele Seiten sie in diesem Design hat,
 * sobald es mehr als eine sind; jede andere hat eine. Nachgemessen und nicht
 * hergeleitet: "Mehr als eine" übersähe eine Seite mehr oder weniger (#337),
 * und eine gewöhnliche Rechnung, die still auf zwei Seiten wüchse, sähe nicht
 * mehr aus wie bisher (ADR-0020).
 */
private data class Design(
    val name: String,
    val stammdaten: Path,
    val zahlungszielBeschriftung: String,
    val bankUeberschrift: String?,
    val rabattBeschriftung: String,
    val fortsetzungshinweis: String,
    val seitenzahl: (seite: Int, von: Int) -> String,
    val grussformel: String?,
    val schlusssatzBeiZahlungsfrist: Boolean,
    val spaltenkopf: String,
    val seiten: Map<String, Int>,
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
    val stammdaten = Path.of("src/test/resources/stammdaten.yaml")

    // Die Stammdaten des Beispiels, nur mit anderem Design – dieselben Angaben,
    // damit ein Unterschied im Befund am Design liegt und nicht an den Daten.
    // Dazu auf Wunsch das Beispiellogo, mit absolutem Pfad: Die Stammdaten
    // liegen hier im Temp-Verzeichnis, das Logo nicht (#198). Ohne
    // Geschäftsführung werden sie die eines Einzelunternehmens.
    fun stammdatenMit(
        design: String,
        logo: Boolean = false,
        geschaeftsfuehrung: Boolean = true,
    ): Path {
        val logoEintrag = if (logo) "logo: ${Path.of("src/test/resources/logo.tex").toAbsolutePath()}\n" else ""
        val lines = stammdaten.readLines().filter { geschaeftsfuehrung || !it.startsWith("geschaeftsfuehrer:") }
        val name = "stammdaten-$design" + (if (logo) "-logo" else "") + (if (geschaeftsfuehrung) "" else "-einzel")
        return workDir.resolve("$name.yaml").also {
            it.writeText(lines.joinToString("\n", postfix = "\n") + "design: $design\n" + logoEintrag)
        }
    }

    // Ein weiteres Design ist hier eine weitere Zeile.
    val designs =
        listOf(
            Design(
                name = "helsinki",
                stammdaten = stammdaten,
                zahlungszielBeschriftung = "Zahlbar ohne Abzug bis",
                bankUeberschrift = "Bankverbindung",
                rabattBeschriftung = "R A B AT T",
                fortsetzungshinweis = "Fortsetzung auf der nächsten Seite",
                seitenzahl = { seite, von -> "Seite $seite von $von" },
                grussformel = "Mit freundlichen Grüßen",
                schlusssatzBeiZahlungsfrist = false,
                spaltenkopf = "EINZELPREIS",
                seiten =
                    mapOf(
                        "viele-positionen" to 3,
                        "schluss-an-der-seitengrenze" to 3,
                        "befreiungsgrund-an-der-seitengrenze" to 3,
                    ),
            ),
            Design(
                name = "prag",
                // prag setzt das Logo; so sieht veraPDF es bei jeder Rechnungsart.
                stammdaten = stammdatenMit("prag", logo = true),
                zahlungszielBeschriftung = "Die Zahlung ist",
                bankUeberschrift = null,
                rabattBeschriftung = "Rabatt",
                fortsetzungshinweis = "Fortsetzung auf der nächsten Seite",
                seitenzahl = { seite, von -> "Seite $seite von $von" },
                grussformel = null,
                schlusssatzBeiZahlungsfrist = true,
                spaltenkopf = "Einzelpreis",
                seiten =
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
    fun setzeUndPruefe(
        file: String,
        design: Design,
        stammdaten: Path,
        zielname: String,
    ): Gesetzt {
        val eingabe = Path.of("src/test/resources/$file.yaml")
        val calculated = CalculatedInvoice(YamlLoader.loadInvoice(eingabe), YamlLoader.loadSellerConfig(stammdaten))

        val (pdf, ausgabe) = run.erzeuge(file, zielname = zielname, stammdaten = stammdaten)
        withClue(ausgabe.joinToString("\n")) { ausgabe shouldContain "PDF/A-1b: gültig" }

        val blatt = Blatt(run.textOf(pdf))
        assertSoftly {
            withClue("Seitenzahl") { blatt.seiten shouldBe (design.seiten[file] ?: 1) }
            blatt.pruefe(calculated, design)
        }
        return Gesetzt(calculated, blatt)
    }

    for (design in designs) {
        for ((art, file) in exampleInvoices) {
            test("$file im Design ${design.name}: gültiges PDF/A-1b mit allen Pflichtangaben") {
                val (calculated, _) = setzeUndPruefe(file, design, design.stammdaten, zielname = "${design.name}-$file")
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
                setzeUndPruefe("gemischte-steuersaetze", design, design.stammdaten, zielname = "${design.name}-gemischt")

            // 19 %, 7 %, steuerbefreit, Nullsatz – die vier Fälle nebeneinander.
            calculated.vatGroups.size shouldBe 4
        }
    }

    // Mehr Positionen, als auf eine Seite passen (#318, ADR-0020): Jede steht
    // auf dem Blatt, und das Blatt hat so viele Seiten, wie das Design für
    // diese Datei nennt. Was die Folgeseiten tragen, prüft `pruefe` an jeder
    // mehrseitigen Rechnung; hier brechen die Positionen über zwei Seiten.
    for (design in designs) {
        test("viele-positionen im Design ${design.name}: gültig, jede Position auf dem Blatt, über mehrere Seiten") {
            val (calculated, _) =
                setzeUndPruefe("viele-positionen", design, design.stammdaten, zielname = "${design.name}-viele")

            calculated.invoice.items.size shouldBe 40
        }
    }

    // Der Schluss an der Seitengrenze (ADR-0020): Die Positionen füllen die
    // zweite Seite so weit, dass sie ohne Zusammenhalt in helsinki zwischen
    // Schlusssatz und Grußformel bräche und in prag zwischen Zahlungsfrist
    // und Schlusssatz. Was dann beisammenstehen muss, prüft
    // `pruefe` an jeder mehrseitigen Rechnung; diese Datei ist die, bei der
    // es darauf ankommt.
    for (design in designs) {
        test("Schluss an der Seitengrenze im Design ${design.name}: der Schluss bricht nicht auseinander") {
            setzeUndPruefe("schluss-an-der-seitengrenze", design, design.stammdaten, zielname = "${design.name}-grenze")
        }
    }

    // Ein langer Befreiungsgrund an der Seitengrenze (ADR-0020, #335): Ohne
    // Zusammenhalt endete die zweite Seite nach der ersten Zeile des Hinweises.
    // Dass er ganz auf einer Seite steht, prüft `pruefe` an jeder mehrseitigen
    // Rechnung; diese Datei ist die, bei der es darauf ankommt.
    for (design in designs) {
        test("Befreiungsgrund an der Seitengrenze im Design ${design.name}: der Hinweis bricht nicht auseinander") {
            val (calculated, _) =
                setzeUndPruefe(
                    "befreiungsgrund-an-der-seitengrenze",
                    design,
                    design.stammdaten,
                    zielname = "${design.name}-befreiung",
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
            val einzelunternehmen = stammdatenMit(design.name, logo = false, geschaeftsfuehrung = false)

            val (calculated, _) = setzeUndPruefe("rechnung", design, einzelunternehmen, zielname = "${design.name}-einzel")

            calculated.config.managingDirector shouldBe null
            calculated.config.logo shouldBe null
        }
    }
})

/** Eine gesetzte Rechnung: das gerechnete Ergebnis und das zurückgelesene Blatt. */
private data class Gesetzt(
    val calculated: CalculatedInvoice,
    val blatt: Blatt,
)

/**
 * Der zurückgelesene Text, mit zusammengefasstem Leerraum – im Ganzen und je
 * Seite. `pdftotext` schließt jede Seite mit einem Seitenvorschub ab; daran
 * zählt der Test die Seiten, ohne ein weiteres Werkzeug.
 */
private class Blatt(
    text: String,
) {
    private val text = normalize(text)
    private val seitenText = text.split('\u000C').dropLastWhile { it.isBlank() }.map { normalize(it) }

    val seiten: Int get() = seitenText.size

    fun steht(
        angabe: String,
        vararg schreibweisen: String,
    ) = steht(text, angabe, schreibweisen)

    fun fehlt(
        angabe: String,
        vararg schreibweisen: String,
    ) = fehlt(text, angabe, schreibweisen)

    fun stehtAufSeite(
        seite: Int,
        angabe: String,
        vararg schreibweisen: String,
    ) = steht(seitenText[seite - 1], "$angabe auf Seite $seite", schreibweisen)

    /** Die erste Seite, auf der die Angabe vollständig steht, oder null. */
    fun seiteMit(angabe: String): Int? = seitenText.indexOfFirst { normalize(angabe) in it }.takeIf { it >= 0 }?.plus(1)

    fun fehltAufSeite(
        seite: Int,
        angabe: String,
        vararg schreibweisen: String,
    ) = fehlt(seitenText[seite - 1], "$angabe auf Seite $seite", schreibweisen)

    private fun steht(
        text: String,
        angabe: String,
        schreibweisen: Array<out String>,
    ) {
        withClue("$angabe: keine der Schreibweisen ${schreibweisen.toList()} im Text\n$text") {
            schreibweisen.any { normalize(it) in text } shouldBe true
        }
    }

    private fun fehlt(
        text: String,
        angabe: String,
        schreibweisen: Array<out String>,
    ) {
        withClue("$angabe: ${schreibweisen.toList()} steht im Text, sollte aber fehlen\n$text") {
            schreibweisen.none { normalize(it) in text } shouldBe true
        }
    }

    companion object {
        // \s kennt weder das geschützte (U+00A0) noch die schmalen Leerzeichen.
        // Das Minuszeichen bleibt, wie es ist: Gleichgesetzt mit "-" ließe das
        // Netz einen Bindestrich vor einer Zahl durch (#320).
        private val LEERRAUM = Regex("[\\s\\u00A0\\u2009\\u202F]+")

        fun normalize(s: String): String = s.replace(LEERRAUM, " ").trim()
    }
}

private fun Blatt.pruefe(
    calculated: CalculatedInvoice,
    design: Design,
) {
    val invoice = calculated.invoice
    val config = calculated.config

    // §14 Abs. 4 Nr. 1: Name und Anschrift beider Seiten. Der vollständige
    // Name des leistenden Unternehmers ist ohne Geschäftsführung die Person,
    // nicht die Firmierung.
    steht("Rechnungssteller", config.companyName)
    if (config.managingDirector == null) steht("Name des Inhabers", config.ownerName)
    steht("Anschrift Rechnungssteller", config.address.street)
    steht("Ort Rechnungssteller", "${config.address.postalCode} ${config.address.city}")
    steht("Kunde", invoice.customer.name)
    steht("Anschrift Kunde", invoice.customer.address.street)
    steht("Ort Kunde", "${invoice.customer.address.postalCode} ${invoice.customer.address.city}")

    // Nr. 2: Steuernummer oder USt-IdNr. – eine genügt.
    steht(
        "Steuernummer oder USt-IdNr.",
        *listOfNotNull(config.vatId.value, config.vatId.formatted(), config.taxNumber?.value).toTypedArray(),
    )

    // Nr. 3 und 4: Rechnungsdatum und Rechnungsnummer.
    steht("Rechnungsdatum", *datum(invoice.issueDate))
    steht("Rechnungsnummer", invoice.number)

    // Nr. 5 und 6: Menge und Art der Leistung, Zeitpunkt der Leistung und –
    // bei einer Abschlagsrechnung, wenn er feststeht – der Vereinnahmung.
    invoice.items.forEachIndexed { i, item ->
        steht("Bezeichnung der Position ${i + 1}", item.name)
        steht("Menge der Position ${i + 1}", menge(item.quantity))
    }
    // Der Rabatt ist keine Pflichtangabe: §14 Abs. 4 Nr. 7 verlangt nur
    // Minderungen, die nicht schon im Entgelt berücksichtigt sind, und der
    // Rabatt ist es. Geprüft wird trotzdem, dass die Spalte genau dann steht,
    // wenn eine Position einen Rabatt hat (#213) – sonst sähe man einen
    // Betrag, der nicht Menge × Einzelpreis ist, ohne Grund.
    calculated.lines.forEachIndexed { i, line ->
        when (val rabatt = line.item.discount) {
            null -> {}
            is Discount.Rate -> {
                steht("Rabatt der Position ${i + 1}", *satz(rabatt.rate))
                steht("Betrag der Position ${i + 1}", betrag(line.net))
            }
            is Discount.Amount -> {
                steht("Rabatt der Position ${i + 1}", betrag(rabatt.amount))
                steht("Betrag der Position ${i + 1}", betrag(line.net))
            }
        }
    }
    if (invoice.items.any { it.discount != null }) {
        steht("Kopf der Rabattspalte", design.rabattBeschriftung)
    } else {
        fehlt("Kopf der Rabattspalte", design.rabattBeschriftung)
    }
    steht("Leistungszeitraum", *zeitraum(invoice.servicePeriod.start, invoice.servicePeriod.end))
    invoice.receivedOn?.let { steht("Tag der Vereinnahmung", *datum(it)) }

    // Nr. 7 und 8: Entgelt je Steuersatz, dazu Satz und Steuerbetrag – oder,
    // bei einer Steuerbefreiung, der Hinweis darauf statt beider (Nr. 8
    // zweiter Halbsatz, #218). Das Entgelt verlangt Nr. 7 in jedem Fall; bei
    // einer befreiten Gruppe nennt es der Hinweis selbst. Ob eine Gruppe
    // befreit ist, sagt ihr Grund und nicht ihr Steuerbetrag von null: Die
    // Kategorie Z trägt keinen Grund und bleibt deshalb eine Gruppe, die Satz
    // und Steuerbetrag zeigen muss.
    calculated.vatGroups.forEach { gruppe ->
        val name = "Steuergruppe ${gruppe.category} ${gruppe.rate}"
        steht("$name: Entgelt", betrag(gruppe.base))
        val grund = gruppe.exemptionReason
        if (grund == null) {
            steht("$name: Satz", *satz(gruppe.rate))
            steht("$name: Steuerbetrag", betrag(gruppe.amount))
        } else {
            steht("$name: Hinweis auf die Steuerbefreiung", grund)
        }
    }

    // §14 Abs. 5 Satz 2: Die Schlussrechnung setzt Entgelt und Steuer der
    // vereinnahmten Abschläge ab.
    calculated.prepayments.forEach { abschlag ->
        val number = abschlag.prepayment.invoiceNumber
        steht("Abschlag $number", number)
        steht("Abschlag $number: Entgelt", betrag(abschlag.net))
        steht("Abschlag $number: Steuer", betrag(abschlag.vat))
    }

    // §35a GmbHG, soweit die Stammdaten die Angaben führen.
    config.registerCourt?.let { steht("Registergericht", it) }
    config.registerNumber?.let { steht("Handelsregisternummer", it) }
    config.managingDirector?.let { steht("Geschäftsführer", it) }

    // Der Pflichtsatz der Art, mit eingesetzten Werten.
    // Die Werte holt der Test selbst und nicht über den InvoiceVariableResolver:
    // Holte der das falsche Datum, sähe der Test sonst dasselbe falsche Datum.
    val pflichtsatz =
        invoice.type.mandatoryNote?.let { satz ->
            val werte =
                VariableResolver<InvoiceVariable> { variable ->
                    val bezug = checkNotNull(invoice.cancelled) { "<<$variable>> ohne Ursprungsrechnung" }
                    when (variable) {
                        InvoiceVariable.URSPRUNGSNUMMER -> TextValue.Text(bezug.invoiceNumber)
                        InvoiceVariable.URSPRUNGSDATUM -> TextValue.Date(bezug.issueDate)
                    }
                }
            listOf(satz.klartext(werte, KURZ), satz.klartext(werte, LANG))
        }
    pflichtsatz?.let { steht("Pflichtsatz", *it.toTypedArray()) }

    // Zahlungsziel und Bankverbindung genau dann, wenn das Dokument Zahlung
    // fordert. Die Abwesenheit zeigt sich an der Beschriftung: Ein Design, das
    // sie ohne Wert stehen ließe, fiele hier auf.
    val dueDate = calculated.dueDate
    val bankUeberschrift = design.bankUeberschrift
    if (dueDate != null) {
        steht("Zahlungsziel", *datum(dueDate), *frist(calculated.paymentTermDays))
        bankUeberschrift?.let { steht("Überschrift der Bankverbindung", it) }
        steht("IBAN", config.bank.iban.formatted())
        steht("BIC", config.bank.bic.value)
    } else {
        fehlt("Beschriftung des Zahlungsziels", design.zahlungszielBeschriftung)
        bankUeberschrift?.let { fehlt("Überschrift der Bankverbindung", it) }
    }

    // Die Folgeseiten (ADR-0020): Jede trägt Rechnungsnummer, Datum und
    // Seitenzahl, damit eine lose Seite zu ihrer Rechnung findet, und den Fuß
    // des Briefbogens mit Anschrift, Register, USt-IdNr. und Bankverbindung.
    // Vom Register prüft das Netz nur die Nummer: Das Registergericht heißt
    // wie ein Ort und stünde meist schon mit der Anschrift da. Jede Seite, auf
    // die eine folgt, sagt das am Ende. Eine Rechnung mit einer Seite trägt
    // nichts davon – „Seite 1 von 1“ sagte nichts.
    if (seiten == 1) {
        fehlt("Seitenzahl", design.seitenzahl(1, 1))
        fehlt("Fortsetzungshinweis", design.fortsetzungshinweis)
    } else {
        for (seite in 1..seiten) {
            if (seite < seiten) {
                stehtAufSeite(seite, "Fortsetzungshinweis", design.fortsetzungshinweis)
            } else {
                fehltAufSeite(seite, "Fortsetzungshinweis", design.fortsetzungshinweis)
            }
            stehtAufSeite(seite, "Rechnungssteller im Fuß", config.companyName)
            stehtAufSeite(seite, "USt-IdNr. im Fuß", config.vatId.value, config.vatId.formatted())
            stehtAufSeite(seite, "Anschrift im Fuß", config.address.street)
            stehtAufSeite(seite, "Ort im Fuß", "${config.address.postalCode} ${config.address.city}")
            config.registerNumber?.let { stehtAufSeite(seite, "Handelsregisternummer im Fuß", it) }
            stehtAufSeite(seite, "IBAN im Fuß", config.bank.iban.formatted())
            stehtAufSeite(seite, "BIC im Fuß", config.bank.bic.value)
            if (seite == 1) continue
            stehtAufSeite(seite, "Seitenzahl", design.seitenzahl(seite, seiten))
            stehtAufSeite(seite, "Rechnungsnummer", invoice.number)
            stehtAufSeite(seite, "Rechnungsdatum", *datum(invoice.issueDate))
        }

        // Jede Seite mit Positionen trägt den Spaltenkopf, und die Summen
        // stehen bei der letzten Position: Summen ohne das, was sie summieren,
        // waren der Befund aus #318.
        invoice.items
            .mapNotNull { seiteMit(it.name) }
            .distinct()
            .forEach { stehtAufSeite(it, "Spaltenkopf", design.spaltenkopf) }
        seiteMit(invoice.items.last().name)?.let {
            stehtAufSeite(it, "Gesamtbetrag bei der letzten Position", betrag(calculated.grossTotal))
        }

        // Schlusssatz, Grußformel und Name brechen nicht auseinander: Der Satz
        // steht ganz auf einer Seite, und Gruß und Name stehen auf derselben.
        val schlusssatz = invoice.note ?: config.closingLine
        val seite = seiteMit(schlusssatz)
        withClue("Schlusssatz steht auf keiner Seite vollständig: $schlusssatz") { (seite != null) shouldBe true }
        val grussformel = design.grussformel
        if (seite != null && grussformel != null) {
            stehtAufSeite(seite, "Grußformel beim Schlusssatz", grussformel)
            stehtAufSeite(seite, "Name beim Schlusssatz", config.ownerName)
        }
        // Ohne Skonto ist die Zahlungsfrist der Satz direkt vor dem Schlusssatz.
        if (seite != null && design.schlusssatzBeiZahlungsfrist && dueDate != null && invoice.skonto.isEmpty()) {
            stehtAufSeite(seite, "Zahlungsfrist beim Schlusssatz", design.zahlungszielBeschriftung)
        }

        // Kein Hinweis auf eine Steuerbefreiung bricht über zwei Seiten: Der
        // Grund steht ganz auf einer Seite, und das Entgelt, mit dem der Satz
        // beginnt, auf derselben.
        calculated.vatGroups.forEach { gruppe ->
            val grund = gruppe.exemptionReason ?: return@forEach
            val grundSeite = seiteMit(grund)
            withClue("Befreiungsgrund steht auf keiner Seite vollständig: $grund") { (grundSeite != null) shouldBe true }
            if (grundSeite != null) stehtAufSeite(grundSeite, "Entgelt beim Befreiungsgrund", betrag(gruppe.base))
        }

        // Ebenso wenig der Pflichtsatz oder die Zahlungsfrist: Der Pflichtsatz
        // steht ganz auf einer Seite, das Zahlungsziel auf der Seite seiner
        // Beschriftung – in helsinki ist das die Kopfzeile, in prag der Anfang
        // des Satzes. Skonto und den Satz zum ausgeglichenen Restbetrag
        // erreicht keine Beispieldatei (#342).
        pflichtsatz?.let { schreibweisen ->
            withClue("Pflichtsatz steht auf keiner Seite vollständig: ${schreibweisen.first()}") {
                schreibweisen.any { seiteMit(it) != null } shouldBe true
            }
        }
        if (dueDate != null) {
            val fristSeite = seiteMit(design.zahlungszielBeschriftung)
            withClue("Beschriftung des Zahlungsziels auf keiner Seite") { (fristSeite != null) shouldBe true }
            if (fristSeite != null) {
                stehtAufSeite(
                    fristSeite,
                    "Zahlungsziel bei seiner Beschriftung",
                    *datum(dueDate),
                    *frist(calculated.paymentTermDays),
                )
            }
        }
    }
}

// --- Schreibweisen ----------------------------------------------------------
// Bewusst unabhängig von GermanFormat, siehe oben. Jede Funktion liefert alle
// Schreibweisen, die ein Design wählen darf.

// Eine negative Zahl trägt das Minuszeichen U+2212, in jedem Design (#320).
// Hier eigens gesetzt und nicht aus GermanFormat übernommen: Es ist die
// Erwartung an das Blatt, und Locale.GERMANY brächte den Bindestrich mit.
private val SYMBOLE = DecimalFormatSymbols(Locale.GERMANY).apply { minusSign = '\u2212' }

private fun zahl(
    value: BigDecimal,
    minFraction: Int,
): String =
    DecimalFormat("#,##0", SYMBOLE)
        .apply {
            minimumFractionDigits = minFraction
            maximumFractionDigits = maxOf(value.stripTrailingZeros().scale(), minFraction)
        }.format(value)

private fun betrag(money: Money): String = zahl(money.value, minFraction = 2)

private fun menge(quantity: BigDecimal): String = zahl(quantity, minFraction = 0)

private fun satz(percent: Percent): Array<String> {
    val zahl = zahl(percent.valueAsPercentage, minFraction = 0)
    return arrayOf("$zahl %", "$zahl%")
}

private val KURZ = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)
private val LANG = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMANY)
private val MONAT = DateTimeFormatter.ofPattern("MMMM", Locale.GERMANY)
private val MONAT_JAHR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMANY)

private fun datum(date: LocalDate): Array<String> = arrayOf(KURZ.format(date), LANG.format(date))

/** Ein Wortlaut als Klartext, wie ihn pdftotext liest, mit einer Schreibweise des Datums. */
private fun <V> TextTemplate<V>.klartext(
    resolver: VariableResolver<V>,
    datum: DateTimeFormatter,
): String =
    map(
        text = { it },
        variable = { variable ->
            when (val value = resolver.resolve(variable)) {
                is TextValue.Text -> value.text
                is TextValue.Date -> datum.format(value.date)
            }
        },
    ).joinToString("")

/** Das Zahlungsziel als Frist ab Rechnungsdatum statt als Datum (#211). */
private fun frist(tage: Int): Array<String> =
    when (tage) {
        0 -> arrayOf("sofort fällig")
        1 -> arrayOf("1 Tag nach Rechnungsdatum")
        else -> arrayOf("$tage Tage nach Rechnungsdatum")
    }

/**
 * Ein Zeitraum auf den Tag genau oder – wo er ganze Kalendermonate füllt – in
 * Monaten (§31 Abs. 4 UStDV, UStAE 14.8 Abs. 1 und 4).
 */
private fun zeitraum(
    start: LocalDate,
    end: LocalDate,
): Array<String> {
    val tage = listOf("–", "-", "bis").map { "${KURZ.format(start)} $it ${KURZ.format(end)}" }
    val ganzeMonate = start.dayOfMonth == 1 && end == end.withDayOfMonth(end.lengthOfMonth())
    if (!ganzeMonate) return tage.toTypedArray()
    val monate =
        if (YearMonth.from(start) == YearMonth.from(end)) {
            listOf(MONAT_JAHR.format(start))
        } else {
            listOf(
                "${MONAT.format(start)} bis ${MONAT_JAHR.format(end)}",
                "${MONAT_JAHR.format(start)} bis ${MONAT_JAHR.format(end)}",
            )
        }
    return (tage + monate).toTypedArray()
}
