package de.fluchitconsulting.rechnungsgenerator.values

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Das Design der Rechnung, gewählt in den Stammdaten (#189, ADR-0011).
 *
 * Eine geschlossene Menge wie [InvoiceType]: Jedes Design ist eine Vorlage im
 * Programm, die der Renderer mit denselben Werten füllt. Mitbringen lässt sich
 * keines – eine fremde Vorlage bestünde zwar die Prüfungen beim Setzen, aber
 * niemand hätte gesehen, ob sie jede Rechnungsart vollständig setzt.
 *
 * [label] ist die Schreibweise in der YAML und zugleich der Name der Vorlage.
 */
@Serializable(with = DesignSerializer::class)
enum class Design(
    val label: String,
) {
    // Das Satzbild des Lebenslaufs und der Webseite des Autors: Titelbalken in
    // der Akzentfarbe, TeX Gyre Heros, ruhiger Textfluss.
    BALKEN("balken"),

    // Klassisch-tabellarisch: Fira Sans, schwarz-weiß, Positionen und Summen
    // in einem Gitter, Fuß in drei Spalten.
    RASTER("raster"),
    ;

    override fun toString(): String = label
}

object DesignSerializer :
    KSerializer<Design> by enumSerializer(
        name = "Design",
        values = Design.entries,
        unknown = "Unbekanntes Design",
        normalize = { it.trim().lowercase() },
        keyOf = Design::label,
    )
