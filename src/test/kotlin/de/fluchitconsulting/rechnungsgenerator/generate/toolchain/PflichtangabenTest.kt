package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import de.fluchitconsulting.rechnungsgenerator.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.values.InvoiceType
import de.fluchitconsulting.rechnungsgenerator.values.Money
import de.fluchitconsulting.rechnungsgenerator.values.Percent
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

/**
 * Ein Design, wie das Netz es sieht: die Stammdaten-Datei, die es wählt, und
 * die Wörter, an denen sich zeigt, dass etwas *fehlt*.
 *
 * Diese Wörter stehen in der Vorlage und nicht in der Rechnung, deshalb gehören
 * sie zum Design. Ob eine Angabe dasteht, lässt sich an ihrem Wert prüfen; ob
 * sie fehlt, nur an ihrer Beschriftung – die IBAN etwa steht im Fuß jedes
 * Dokuments, die Überschrift der Bankverbindung nicht.
 */
private data class Design(
    val name: String,
    val stammdaten: Path,
    val zahlungszielBeschriftung: String,
    val bankUeberschrift: String,
)

/**
 * Das Netz unter jedem Design (#187): Für jede Rechnungsart entsteht das PDF
 * mit den echten Werkzeugen, veraPDF bestätigt PDF/A-1b, und `pdftotext` liest
 * zurück, ob die Pflichtangaben auf dem Blatt stehen.
 *
 * `rechnung-erwartet.tex` und die anderen Vergleichsdateien schützen den
 * Wortlaut eines Designs. Dieser Test fragt etwas anderes: ob auf dem Blatt
 * steht, was dort stehen muss – §14 Abs. 4 und 5 UStG, §35a GmbHG, der
 * Pflichtsatz der Art. Deshalb kennt er keine Schreibweise eines Designs:
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

    val run = ToolchainRun(createTempDirectory("rechnungsgenerator-pflichtangaben"))

    // Ein zweites Design ist hier eine zweite Zeile.
    val designs =
        listOf(
            Design(
                name = "balken",
                stammdaten = Path.of("src/test/resources/stammdaten.yaml"),
                zahlungszielBeschriftung = "Zahlbar ohne Abzug bis",
                bankUeberschrift = "Bankverbindung",
            ),
        )

    // Eine Datei je Rechnungsart. Dass keine fehlt, prüft der erste Test.
    val arten =
        mapOf(
            InvoiceType.INVOICE to "rechnung",
            InvoiceType.PARTIAL to "teilrechnung",
            InvoiceType.PREPAYMENT to "abschlagsrechnung",
            InvoiceType.FINAL to "schlussrechnung",
            InvoiceType.CANCELLATION to "stornorechnung",
        )

    test("jede Rechnungsart hat eine Beispieldatei") {
        arten.keys shouldBe InvoiceType.entries.toSet()
    }

    for (design in designs) {
        for ((art, datei) in arten) {
            test("$datei im Design ${design.name}: gültiges PDF/A-1b mit allen Pflichtangaben") {
                val eingabe = Path.of("src/test/resources/$datei.yaml")
                val calculated =
                    CalculatedInvoice(YamlLoader.loadInvoice(eingabe), YamlLoader.loadSellerConfig(design.stammdaten))
                calculated.invoice.type shouldBe art

                val (pdf, ausgabe) =
                    run.erzeuge(datei, zielname = "${design.name}-$datei", stammdaten = design.stammdaten)
                withClue(ausgabe.joinToString("\n")) { ausgabe shouldContain "PDF/A-1b: gültig" }

                val blatt = Blatt(run.textOf(pdf))
                assertSoftly { blatt.pruefe(calculated, design) }
            }
        }
    }
})

/** Der zurückgelesene Text, mit zusammengefasstem Leerraum. */
private class Blatt(
    text: String,
) {
    private val text = normalize(text)

    fun steht(
        angabe: String,
        vararg schreibweisen: String,
    ) {
        withClue("$angabe: keine der Schreibweisen ${schreibweisen.toList()} im Text\n$text") {
            schreibweisen.any { normalize(it) in text } shouldBe true
        }
    }

    fun fehlt(
        angabe: String,
        vararg schreibweisen: String,
    ) {
        withClue("$angabe: ${schreibweisen.toList()} steht im Text, sollte aber fehlen\n$text") {
            schreibweisen.none { normalize(it) in text } shouldBe true
        }
    }

    companion object {
        // \s kennt weder das geschützte (U+00A0) noch die schmalen Leerzeichen;
        // das Minuszeichen U+2212 gilt wie "-".
        private val LEERRAUM = Regex("[\\s\\u00A0\\u2009\\u202F]+")

        fun normalize(s: String): String = s.replace(LEERRAUM, " ").replace('−', '-').trim()
    }
}

private fun Blatt.pruefe(
    calculated: CalculatedInvoice,
    design: Design,
) {
    val invoice = calculated.invoice
    val config = calculated.config

    // §14 Abs. 4 Nr. 1: Name und Anschrift beider Seiten.
    steht("Rechnungssteller", config.companyName)
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
    steht("Leistungszeitraum", *zeitraum(invoice.servicePeriod.start, invoice.servicePeriod.end))
    invoice.receivedOn?.let { steht("Tag der Vereinnahmung", *datum(it)) }

    // Nr. 7 und 8: Entgelt je Steuersatz, Satz und Steuerbetrag.
    calculated.vatGroups.forEach { gruppe ->
        val name = "Steuergruppe ${gruppe.category} ${gruppe.rate}"
        steht("$name: Entgelt", betrag(gruppe.base))
        steht("$name: Satz", *satz(gruppe.rate))
        steht("$name: Steuerbetrag", betrag(gruppe.amount))
    }

    // §14 Abs. 5 Satz 2: Die Schlussrechnung setzt Entgelt und Steuer der
    // vereinnahmten Abschläge ab.
    calculated.prepayments.forEach { abschlag ->
        val nummer = abschlag.prepayment.invoiceNumber
        steht("Abschlag $nummer", nummer)
        steht("Abschlag $nummer: Entgelt", betrag(abschlag.net))
        steht("Abschlag $nummer: Steuer", betrag(abschlag.vat))
    }

    // §35a GmbHG, soweit die Stammdaten die Angaben führen.
    config.registerCourt?.let { steht("Registergericht", it) }
    config.registerNumber?.let { steht("Handelsregisternummer", it) }
    config.managingDirector?.let { steht("Geschäftsführer", it) }

    // Der Pflichtsatz der Art, mit eingesetzten Werten.
    invoice.type.mandatoryNote?.let { satz ->
        val bezug = invoice.cancelled
        val fassungen =
            if (bezug == null) {
                listOf(satz)
            } else {
                datum(bezug.issueDate).map {
                    satz.replace("<<URSPRUNGSNUMMER>>", bezug.invoiceNumber).replace("<<URSPRUNGSDATUM>>", it)
                }
            }
        steht("Pflichtsatz", *fassungen.toTypedArray())
    }

    // Zahlungsziel und Bankverbindung genau dann, wenn das Dokument Zahlung
    // fordert. Die Abwesenheit zeigt sich an der Beschriftung: Ein Design, das
    // sie ohne Wert stehen ließe, fiele hier auf.
    val dueDate = calculated.dueDate
    if (dueDate != null) {
        steht("Zahlungsziel", *datum(dueDate))
        steht("Überschrift der Bankverbindung", design.bankUeberschrift)
        steht("IBAN", config.bank.iban.formatted())
        steht("BIC", config.bank.bic.value)
    } else {
        fehlt("Beschriftung des Zahlungsziels", design.zahlungszielBeschriftung)
        fehlt("Überschrift der Bankverbindung", design.bankUeberschrift)
    }
}

// --- Schreibweisen ----------------------------------------------------------
// Bewusst unabhängig von GermanFormat, siehe oben. Jede Funktion liefert alle
// Schreibweisen, die ein Design wählen darf.

private val SYMBOLE = DecimalFormatSymbols(Locale.GERMANY)

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
