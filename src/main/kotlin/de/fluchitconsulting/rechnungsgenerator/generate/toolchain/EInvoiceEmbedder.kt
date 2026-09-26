package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
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
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Macht aus einem PDF/A-1b ein PDF/A-3u mit dem XML der E-Rechnung als
 * Anhang `factur-x.xml` (ADR-0023).
 *
 * Den Anhang samt `/AF`, `/AFRelationship` und dem XMP-Schema `fx:` baut
 * Mustang. Dabei setzt es von sich aus das aktuelle Datum ein und schreibt das
 * XMP neu, und so gingen Titel, Beschreibung, Sprache und das Datum der
 * Rechnung verloren. Deshalb wird vor dem Speichern übernommen, was LuaLaTeX
 * ins PDF/A-1b geschrieben hat: das XMP, ergänzt um Mustangs Angaben zu
 * ZUGFeRD, das Info-Dictionary und dessen Datum am Anhang. Erfunden wird
 * nichts, und dieselben Quellen ergeben weiter dasselbe PDF (ADR-0005, #202).
 */
object EInvoiceEmbedder {
    private const val RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    private const val PDFAID = "http://www.aiim.org/pdfa/ns/id/"
    private const val PDFA_EXTENSION = "http://www.aiim.org/pdfa/ns/extension/"
    private const val PDFA_SCHEMA = "http://www.aiim.org/pdfa/ns/schema#"
    private const val FX = "urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#"

    /**
     * Mustang reicht das fertige Dokument nicht heraus; das Feld ist
     * `protected`. Die Unterklasse ist der schmalste Weg daran, und dass
     * Mustang das nirgends zusagt, halten die Tests des Embedders fest.
     */
    private class Exporter : ZUGFeRDExporterFromA1() {
        fun document(): PDDocument = doc
    }

    /**
     * Schreibt [target] aus [pdf] und [xml]. Scheitert Mustang oder PDFBox am
     * Lesen oder Schreiben, wird daraus eine [ToolchainException], wie es der
     * [Typesetter] zusagt (#300).
     */
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
            // use, weil export() das Dokument nur nach gelungenem Speichern
            // schließt; scheitert es vorher, bliebe es samt Dateihandle offen
            // (#390). Ein zweites Schließen nach export() ist folgenlos.
            Exporter().use { exporter ->
                exporter.setProfile("EXTENDED")
                // Fassung 2 ausdrücklich: Sie bestimmt den Namensraum fx: und den
                // Namen factur-x.xml, auf die mergeXmp und die Tests bauen.
                exporter.setZUGFeRDVersion(2)
                exporter.setConformanceLevel(PDFAConformanceLevel.UNICODE)
                exporter.load(pdf.toString())
                // setXML bereitet das Dokument schon vollständig vor, es ruft
                // selbst prepare(). Ein zweiter Aufruf hinge das XML ein zweites
                // Mal an, und veraPDF bemerkte das nicht.
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

    /**
     * Das XMP des PDF/A-1b, als PDF/A-3u erklärt und ergänzt um Mustangs
     * Beschreibung mit den `fx:`-Angaben samt deren Schema-Erklärung, ohne die
     * ein PDF/A das Schema nicht kennte.
     */
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

    private fun parse(bytes: ByteArray): Document =
        DocumentBuilderFactory
            .newInstance()
            .apply {
                isNamespaceAware = true
                // Externe Entitäten abschalten, wie beim Bericht von veraPDF.
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }.newDocumentBuilder()
            .parse(ByteArrayInputStream(bytes))

    private fun NodeList.elements(): List<Element> = (0 until length).map { item(it) as Element }

    private fun Element.childElements(): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()
}
