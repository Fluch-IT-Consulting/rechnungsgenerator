package de.fluchitconsulting.rechnungsgenerator.generate

import java.nio.file.Path

/**
 * Der Viewer fürs Testen: öffnet nichts, merkt sich, was er öffnen sollte, und
 * liefert ein vorbereitetes Ergebnis.
 *
 * [onOpen] läuft im Moment des Öffnens – so sieht ein Test, was bis dahin
 * schon ausgegeben war.
 */
class FakePdfViewer(
    private val result: ViewerResult = ViewerResult.Opened,
    private val onOpen: () -> Unit = {},
) : PdfViewer {
    val opened = mutableListOf<Path>()

    override fun open(pdf: Path): ViewerResult {
        // add statt +=: Path ist selbst ein Iterable<Path> (seine Namensteile),
        // und `opened += pdf` wäre damit mehrdeutig – ein Element oder alle
        // Teile des Pfads? Der Compiler lehnt es ab.
        opened.add(pdf)
        onOpen()
        return result
    }
}
