package de.fluchitconsulting.rechnungsgenerator.generate

import java.nio.file.Path

/** [onOpen] läuft im Moment des Öffnens; so sieht ein Test, was bis dahin ausgegeben war. */
class FakePdfViewer(
    private val result: ViewerResult = ViewerResult.Opened,
    private val onOpen: () -> Unit = {},
) : PdfViewer {
    val opened = mutableListOf<Path>()

    override fun open(pdf: Path): ViewerResult {
        // add statt +=: Path ist selbst ein Iterable<Path>.
        opened.add(pdf)
        onOpen()
        return result
    }
}
