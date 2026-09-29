package de.fluchitconsulting.rechnungsgenerator.xml

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDMetadata
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile
import java.nio.file.Path

fun xmpClaiming(
    part: Int,
    conformance: String?,
) = """
    <x:xmpmeta xmlns:x="adobe:ns:meta/">
      <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description rdf:about="" xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/"
          pdfaid:part="$part"${conformance?.let { " pdfaid:conformance=\"$it\"" }.orEmpty()}/>
      </rdf:RDF>
    </x:xmpmeta>
    """.trimIndent()

/**
 * Hat sonst nichts, was ein PDF/A verlangt. Mit [inKids] stehen die Anhänge in
 * einem Kindknoten des Namensbaums, wie bei einem Baum mit vielen Einträgen.
 */
fun writePdf(
    target: Path,
    attachments: Map<String, ByteArray> = emptyMap(),
    xmp: String? = null,
    inKids: Boolean = false,
    embeddedUnder: EmbeddedUnder = EmbeddedUnder.F,
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
                        when (embeddedUnder) {
                            EmbeddedUnder.F -> {
                                embeddedFile = PDEmbeddedFile(document, bytes.inputStream())
                            }
                            EmbeddedUnder.UF -> {
                                embeddedFileUnicode = PDEmbeddedFile(document, bytes.inputStream())
                            }
                            EmbeddedUnder.NONE -> {}
                        }
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

enum class EmbeddedUnder { F, UF, NONE }
