package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.PdfSources

/**
 * Was der Renderer aus einer Rechnung macht: die [sources] – Quelltext,
 * Metadaten, eingebundene Dateien und das Erstellungsdatum – und die Auskunft,
 * ob das Blatt das Logo zeigt.
 *
 * Die Quellen sind der Eingabetyp der Werkzeuge (#202) und nicht noch einmal
 * nachgebaut: Sonst bliebe zwischen Renderer und Naht eine Abbildung liegen,
 * die jeder neue Wert anfassen muss. Dass der Renderer sie kennt, ist die
 * einzige Kante von `generate.render` nach `generate.toolchain`.
 *
 * [includesLogo] sagt, ob ein Fehler im Satz am Logo liegen kann. Ausdrücklich
 * und nicht aus den Dateien geschlossen: Das ginge nur, solange das Logo die
 * einzige ist (#203).
 *
 * Keine `data class`, aus demselben Grund wie beim Logo: Ihr `equals` verglich
 * die `ByteArray`s nach Identität.
 */
class RenderedInvoice(
    val sources: PdfSources,
    val includesLogo: Boolean,
)
