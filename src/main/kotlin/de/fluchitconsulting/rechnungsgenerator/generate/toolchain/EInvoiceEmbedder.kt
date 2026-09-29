package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import de.fluchitconsulting.rechnungsgenerator.toolchain.XmlParser
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import org.apache.pdfbox.pdmodel.common.PDMetadata
import org.mustangproject.ZUGFeRD.PDFAConformanceLevel
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA1
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Path
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Macht aus dem PDF/A-1b ein PDF/A-3u mit `factur-x.xml` (ADR-0023). Mustang setzt dabei die Uhr ein und schreibt
 * das XMP neu; deshalb werden XMP, Info-Dictionary und Datum aus dem PDF/A-1b übernommen, damit dieselben Quellen
 * dasselbe PDF ergeben.
 */
object EInvoiceEmbedder {
    private const val RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    private const val PDFAID = "http://www.aiim.org/pdfa/ns/id/"
    private const val PDFA_EXTENSION = "http://www.aiim.org/pdfa/ns/extension/"
    private const val PDFA_SCHEMA = "http://www.aiim.org/pdfa/ns/schema#"
    private const val FX = "urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#"

    /** `doc` ist `protected`; die Unterklasse ist der schmalste Weg daran. Zugesagt ist das nicht, die Tests halten es. */
    private class Exporter : ZUGFeRDExporterFromA1() {
        fun document(): PDDocument = doc
    }

    /** Jede IOException wird zur [ToolchainException], wie der [Typesetter] zusagt (#300). */
    fun embed(
        pdf: Path,
        xml: ByteArray,
        target: Path,
    ) {
        try {
            val (originalInfo, originalXmp) =
                Loader.loadPDF(pdf.toFile()).use { original ->
                    COSDictionary(original.documentInformation.cosObject) to
                        original.documentCatalog.metadata.toByteArray()
                }
            // use, weil export() nur nach gelungenem Speichern schließt (#390).
            Exporter().use { exporter ->
                exporter.setProfile("EXTENDED")
                // Fassung 2 bestimmt den Namensraum fx: und den Namen factur-x.xml, auf die mergeXmp baut.
                exporter.setZUGFeRDVersion(2)
                exporter.setConformanceLevel(PDFAConformanceLevel.UNICODE)
                exporter.load(pdf.toString())
                // setXML ruft selbst prepare(); ein zweiter Aufruf hinge das XML unbemerkt zweimal an.
                exporter.setXML(xml)

                val document = exporter.document()
                val xmp = mergeXmp(originalXmp, document.documentCatalog.metadata.toByteArray())
                document.documentCatalog.metadata = PDMetadata(document, ByteArrayInputStream(xmp))
                val info = PDDocumentInformation(originalInfo)
                document.documentInformation = info
                document.documentCatalog.names.embeddedFiles.names.values.forEach { attachment ->
                    attachment.embeddedFile.creationDate = info.creationDate
                    attachment.embeddedFile.modDate = info.modificationDate
                }
                exporter.export(target.toString())
            }
        } catch (e: IOException) {
            throw ToolchainException("Die E-Rechnung ließ sich nicht in $pdf einbetten: ${e.message}", e)
        }
    }

    /** Das XMP des PDF/A-1b als PDF/A-3u, ergänzt um Mustangs `fx:`-Angaben samt Schema, ohne das PDF/A sie nicht kennte. */
    private fun mergeXmp(
        original: ByteArray,
        mustang: ByteArray,
    ): ByteArray {
        val target = parse(original)
        val source = parse(mustang)

        target.getElementsByTagNameNS(PDFAID, "part").item(0).textContent = "3"
        target.getElementsByTagNameNS(PDFAID, "conformance").item(0).textContent = "U"

        val rdf = target.getElementsByTagNameNS(RDF, "RDF").item(0)
        source
            .getElementsByTagNameNS(RDF, "Description")
            .elements()
            .filter { description -> description.childElements().any { it.namespaceURI == FX } }
            .forEach { rdf.appendChild(target.importNode(it, true)) }

        val schemas =
            (target.getElementsByTagNameNS(PDFA_EXTENSION, "schemas").item(0) as Element)
                .getElementsByTagNameNS(RDF, "Bag")
                .item(0)
        source
            .getElementsByTagNameNS(PDFA_SCHEMA, "namespaceURI")
            .elements()
            .filter { it.textContent.trim() == FX }
            .forEach { schemas.appendChild(target.importNode(it.parentNode, true)) }

        return ByteArrayOutputStream()
            .also { out ->
                TransformerFactory
                    .newInstance()
                    .newTransformer()
                    .apply {
                        setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
                        setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                    }.transform(DOMSource(target), StreamResult(out))
            }.toByteArray()
    }

    private fun parse(bytes: ByteArray): Document = XmlParser.parse(bytes, namespaceAware = true)

    private fun NodeList.elements(): List<Element> = (0 until length).map { item(it) as Element }

    private fun Element.childElements(): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()
}
