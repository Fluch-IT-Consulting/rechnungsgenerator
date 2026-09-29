package de.fluchitconsulting.rechnungsgenerator.xml

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification

/**
 * Über PDFBox und nicht Mustangs `ZUGFeRDInvoiceImporter`: Der verschluckt Lesefehler und nimmt von mehreren
 * Anhängen still den letzten (#380). Keine `data class` wegen des [ByteArray].
 */
class EInvoiceAttachment(
    val name: String,
    val xml: ByteArray,
) {
    companion object {
        /** Die Namen je Fassung, genau verglichen: ZUGFeRD 2.0 und 1 unterscheiden sich nur in der Schreibweise. */
        val NAMES = listOf("factur-x.xml", "zugferd-invoice.xml", "xrechnung.xml", "ZUGFeRD-invoice.xml")

        /** Mehr als einer ist kein Fall zum Aussuchen: Welcher gilt, sagt das PDF nicht. */
        fun allIn(document: PDDocument): List<EInvoiceAttachment> {
            val tree = document.documentCatalog.names?.embeddedFiles ?: return emptyList()
            return specifications(tree)
                .filter { it.filename in NAMES }
                // Ohne eingebettete Datei ein leeres XML, das der Prüfer ablehnt; übergangen hieße es fälschlich „kein Anhang“.
                .map { EInvoiceAttachment(it.filename, (it.embeddedFile ?: it.embeddedFileUnicode)?.toByteArray() ?: ByteArray(0)) }
        }

        /** Ein Namensbaum hängt seine Einträge an sich selbst oder an Kinder, je nach Größe. */
        private fun specifications(node: PDNameTreeNode<PDComplexFileSpecification>): List<PDComplexFileSpecification> =
            node.names.orEmpty().values + node.kids.orEmpty().flatMap { specifications(it) }
    }
}
