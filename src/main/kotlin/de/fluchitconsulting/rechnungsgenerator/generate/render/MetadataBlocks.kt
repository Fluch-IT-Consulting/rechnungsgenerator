package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.accepted.AcceptedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.Address
import de.fluchitconsulting.rechnungsgenerator.core.rules.InvoiceKind
import de.fluchitconsulting.rechnungsgenerator.core.values.CountryCode
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import java.util.Locale

/**
 * Die Angaben um den Empfänger, die mehr sind als der Pfad zu einem Wert: die
 * Anschrift als Block und die Vereinnahmung mit ihrem `when` über die
 * Rechnungsart (#128). Den Bezug einer Stornorechnung nennt der Pflichtsatz
 * (#141).
 *
 * Eine **einzelne Angabe** steht dagegen direkt im `when` von
 * [InvoiceRenderer.values] – auch mit Formatierschritt (#244). Dort stehen
 * `formatted()` und `DateFormat.date` bei USTID, IBAN und
 * RECHNUNGSDATUM; eine Zwischenstufe daneben, die nur weiterreicht, hieße
 * beim Lesen von `values()` einen Sprung in diese Datei, nur um zu erfahren,
 * dass hier keine Entscheidung steht.
 */
internal object MetadataBlocks {
    /**
     * Name und Anschrift des Kunden als ein Block: Die Landzeile hängt an
     * [CalculatedInvoice.isDomestic], und die Reihenfolge einer Anschrift ist
     * Postkonvention, kein Satzbild.
     *
     * Aus demselben Grund steht der Ansprechpartner (BT-56) hier und nicht als
     * eigener Platzhalter (#196): Er gehört unter den Namen und vor die Straße,
     * in jedem Design.
     */
    fun recipient(calculatedInvoice: CalculatedInvoice): Latex {
        val customer = calculatedInvoice.invoice.customer
        return buildList {
            add(customer.name.escapeLatex())
            customer.contactPoint?.let { add(it.escapeLatex()) }
            addAll(addressLines(customer.address, showCountry = !calculatedInvoice.isDomestic, calculatedInvoice.invoice.language))
        }.joinToLatex(Latex.macro(TemplateMacro.ZEILENWECHSEL))
    }

    /**
     * Die Anschrift, Zeile für Zeile, mit dem Land nur bei [showCountry].
     *
     * Ob es eine Inlandsrechnung ist, entscheidet nicht diese Funktion, sondern
     * [CalculatedInvoice.isDomestic] – dort steht auch, warum "nicht Deutschland"
     * die falsche Frage ist. Hier wird nur gesetzt (#120).
     */
    private fun addressLines(
        address: Address,
        showCountry: Boolean,
        language: Language,
    ): List<Latex> =
        buildList {
            add(address.street.escapeLatex())
            add("${address.postalCode} ${address.city}".escapeLatex())
            // Auf einer Inlandsrechnung wäre die Landzeile überflüssig.
            if (showCountry) add(Latex.macro(TemplateMacro.LANDZEILE, listOf(countryName(address.countryCode, language).escapeLatex())))
        }

    /**
     * Der Name des Landes in der Sprache der Rechnung, so wie das JDK ihn
     * führt – und der Code selbst, wo es keinen führt (#374). Sonst stünde auf
     * der typischen englischen Rechnung „VEREINIGTES KÖNIGREICH“ über der
     * Anschrift (#377).
     *
     * Aus dem JDK und nicht aus einer eigenen Liste: [CountryCode] prüft
     * bewusst nur die Form, weil ein Verzeichnis der Staaten still veraltet.
     * Das JDK bringt eines mit, das mit ihm gepflegt wird.
     *
     * Gefragt wird nur für einen Staat nach ISO 3166, den das JDK in
     * [Locale.getISOCountries] führt. Für jeden anderen Code liefert
     * [Locale.getDisplayCountry] nicht verlässlich etwas Erkennbares – meist
     * den Code zurück, für `ZZ` aber "Unbekannte Region", und das stünde dann
     * als Land auf dem Blatt. Kein Abbruch: Die Form ist geprüft, und ein
     * Code, den der Generator nicht ausschreiben kann, ist auf dem Blatt ein
     * Schönheitsfehler und keine falsche Angabe. Auch `EL` bleibt so stehen,
     * statt als Griechenland gelesen zu werden – in einer Anschrift verlangt
     * EN 16931 ohnehin `GR` (BT-55).
     *
     * Der Preis: Der Name folgt den Länderdaten des JDK, auf dem das Programm
     * läuft. Benennt ein neueres eines ein Land um, ergibt dieselbe
     * Rechnungsdatei dort ein anderes Blatt.
     */
    private fun countryName(code: CountryCode, language: Language): String =
        if (code.value in ISO_COUNTRIES) Locale.of("", code.value).getDisplayCountry(language.locale) else code.value

    private val ISO_COUNTRIES: Set<String> = Locale.getISOCountries().toSet()

    /**
     * BT-7: Steht der Zeitpunkt der Vereinnahmung fest, gehört er zu den
     * Pflichtangaben. Leer auf jeder anderen Art und solange er nicht
     * feststeht.
     *
     * Ohne `else`: Kommt eine Variante dazu, übersetzt diese Stelle erst, wenn
     * entschieden ist, ob sie eine Vereinnahmung nennt (#128).
     */
    fun receivedOn(acceptedInvoice: AcceptedInvoice): Latex =
        when (val kind = acceptedInvoice.kind) {
            is InvoiceKind.PrepaymentInvoice -> {
                kind.receivedOn?.let { DateFormat(acceptedInvoice.calculated.invoice.language).date(it) }
                    ?: Latex.EMPTY
            }
            is InvoiceKind.Ordinary, is InvoiceKind.FinalInvoice, is InvoiceKind.Cancellation -> {
                Latex.EMPTY
            }
        }
}
