package de.fluchitconsulting.rechnungsgenerator.xml

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification

/**
 * Ein Anhang, der nach seinem Namen das XML einer E-Rechnung trägt, samt den
 * Bytes, wie sie eingebettet sind.
 *
 * Gelesen über PDFBox und nicht über Mustangs `ZUGFeRDInvoiceImporter`: Der
 * verschluckt einen Fehler beim Lesen, nimmt von mehreren Anhängen still den
 * letzten und liest eine Datei, die kein PDF ist, als XML (#380). Die Bytes
 * holt er mit demselben `toByteArray()` wie hier.
 *
 * Keine `data class`: Ihr `equals` verglich das [ByteArray] nach Identität.
 */
class EInvoiceAttachment(
    val name: String,
    val xml: ByteArray,
) {
    companion object {
        /**
         * Die Namen, unter denen die Fassungen das XML anhängen: Factur-X und
         * ZUGFeRD ab 2.1, ZUGFeRD 2.0, das Profil XRECHNUNG und ZUGFeRD 1.
         * Verglichen wird genau, auch in der Schreibweise – 2.0 und 1
         * unterscheiden sich nur darin.
         */
        val NAMES = listOf("factur-x.xml", "zugferd-invoice.xml", "xrechnung.xml", "ZUGFeRD-invoice.xml")

        /**
         * Alle Anhänge von [document] mit einem der [NAMES]. Mehr als einer ist
         * kein Fall zum Aussuchen: Welcher gilt, sagt das PDF nicht, und
         * entscheiden muss der Aufrufer.
         */
        fun allIn(document: PDDocument): List<EInvoiceAttachment> {
            val tree = document.documentCatalog.names?.embeddedFiles ?: return emptyList()
            return specifications(tree)
                .filter { it.filename in NAMES }
                // Ein Eintrag ohne eingebettete Datei ergibt ein leeres XML,
                // und das lehnt der Prüfer ab. Übergangen hieße er "kein
                // Anhang", und das stimmte nicht.
                .map { EInvoiceAttachment(it.filename, (it.embeddedFile ?: it.embeddedFileUnicode)?.toByteArray() ?: ByteArray(0)) }
        }

        /** Ein Namensbaum hängt seine Einträge an sich selbst oder an Kinder, je nach Größe. */
        private fun specifications(node: PDNameTreeNode<PDComplexFileSpecification>): List<PDComplexFileSpecification> =
            node.names.orEmpty().values + node.kids.orEmpty().flatMap { specifications(it) }
    }
}
