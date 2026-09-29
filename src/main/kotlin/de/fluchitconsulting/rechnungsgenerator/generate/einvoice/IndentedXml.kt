package de.fluchitconsulting.rechnungsgenerator.generate.einvoice

import java.io.StringWriter
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter

/**
 * Auf `XMLStreamWriter`, weil er maskiert; eingerückt wird hier. Nicht DOM mit `Transformer`: Dessen Einrückung
 * änderte sich zwischen JDK-Fassungen, und die Vergleichsdatei verlangt jedes Zeichen.
 */
internal fun indentedXml(
    root: String,
    namespaces: Map<String, String>,
    content: XmlElements.() -> Unit,
): String {
    val text = StringWriter()
    val writer = XMLOutputFactory.newInstance().createXMLStreamWriter(text)
    writer.writeStartDocument("UTF-8", "1.0")
    writer.writeCharacters("\n")
    val (prefix, localName) = root.split(':', limit = 2)
    writer.writeStartElement(prefix, localName, checkNotNull(namespaces[prefix]) { "Namensraum $prefix fehlt" })
    namespaces.forEach { (p, uri) -> writer.writeNamespace(p, uri) }
    XmlElements(writer, namespaces, depth = 1).content()
    writer.writeCharacters("\n")
    writer.writeEndElement()
    writer.writeEndDocument()
    writer.close()
    return "$text\n"
}

/** Die Elemente einer Ebene. Ein Element enthält Text oder Elemente, nie beides. */
internal class XmlElements(
    private val writer: XMLStreamWriter,
    private val namespaces: Map<String, String>,
    private val depth: Int,
) {
    fun element(
        name: String,
        text: String,
        vararg attributes: Pair<String, String>,
    ) {
        start(name, attributes)
        writer.writeCharacters(text)
        writer.writeEndElement()
    }

    fun element(
        name: String,
        vararg attributes: Pair<String, String>,
        children: XmlElements.() -> Unit,
    ) {
        start(name, attributes)
        XmlElements(writer, namespaces, depth + 1).children()
        indent()
        writer.writeEndElement()
    }

    private fun start(
        name: String,
        attributes: Array<out Pair<String, String>>,
    ) {
        indent()
        val (prefix, localName) = split(name)
        writer.writeStartElement(prefix, localName, namespaceOf(prefix))
        attributes.forEach { (attribute, value) -> writer.writeAttribute(attribute, value) }
    }

    private fun indent() = writer.writeCharacters("\n" + "  ".repeat(depth))

    private fun split(name: String): Pair<String, String> =
        name.split(':', limit = 2).let { (prefix, localName) -> prefix to localName }

    private fun namespaceOf(prefix: String): String = checkNotNull(namespaces[prefix]) { "Namensraum $prefix fehlt" }
}
