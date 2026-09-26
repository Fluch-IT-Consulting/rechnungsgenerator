package de.fluchitconsulting.rechnungsgenerator.xml

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDMetadata
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile
import java.nio.file.Path

/** Ein XMP, das sich als PDF/A mit Teil [part] und Stufe [conformance] ausgibt. */
fun xmpClaiming(
    part: Int,
    conformance: String,
) = """
    <x:xmpmeta xmlns:x="adobe:ns:meta/">
      <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description rdf:about="" xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/"
          pdfaid:part="$part" pdfaid:conformance="$conformance"/>
      </rdf:RDF>
    </x:xmpmeta>
    """.trimIndent()

/**
 * Schreibt nach [target] ein PDF mit einer leeren Seite, den [attachments]
 * unter ihren Namen und auf Wunsch einem [xmp]. Sonst hat es nichts, was ein
 * PDF/A verlangt – für die Fälle, in denen es auf die Anhänge ankommt oder
 * darauf, dass ein PDF sich als etwas ausgibt, das es nicht ist.
 *
 * Mit [inKids] stehen die Anhänge nicht an der Wurzel des Namensbaums,
 * sondern in einem Kindknoten, wie bei einem Baum mit vielen Einträgen.
 */
fun writePdf(
    target: Path,
    attachments: Map<String, ByteArray> = emptyMap(),
    xmp: String? = null,
    inKids: Boolean = false,
): Path {
    PDDocument().use { document ->
        document.addPage(PDPage())
        xmp?.let { document.documentCatalog.metadata = PDMetadata(document, it.byteInputStream()) }
        if (attachments.isNotEmpty()) {
            val specifications =
                attachments.mapValues { (name, bytes) ->
                    PDComplexFileSpecification().apply {
                        file = name
                        fileUnicode = name
                        embeddedFile = PDEmbeddedFile(document, bytes.inputStream())
                    }
                }
            val leaf = PDEmbeddedFilesNameTreeNode().apply { names = specifications }
            val tree = if (inKids) PDEmbeddedFilesNameTreeNode().apply { kids = listOf(leaf) } else leaf
            document.documentCatalog.names = PDDocumentNameDictionary(document.documentCatalog).apply { embeddedFiles = tree }
        }
        document.save(target.toFile())
    }
    return target
}
