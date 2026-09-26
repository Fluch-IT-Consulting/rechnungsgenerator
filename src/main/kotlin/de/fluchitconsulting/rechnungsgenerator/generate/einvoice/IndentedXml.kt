package de.fluchitconsulting.rechnungsgenerator.generate.einvoice

import java.io.StringWriter
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter

/**
 * Schreibt ein XML-Dokument mit zwei Leerzeichen Einrückung je Ebene.
 *
 * Auf `XMLStreamWriter` aus dem JDK, weil er maskiert und nur Wohlgeformtes
 * schreibt; eingerückt wird hier, weil er es nicht tut. Nicht DOM mit einem
 * `Transformer`: Wie der einrückt, hat sich zwischen JDK-Fassungen geändert,
 * und die Vergleichsdatei verlangt jedes Zeichen.
 *
 * Namen stehen mit Präfix (`ram:Name`); welcher Namensraum dazugehört, sagt
 * [namespaces], und das Wurzelelement erklärt sie alle.
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
    /** Ein Element mit Text, in einer Zeile. */
    fun element(
        name: String,
        text: String,
        vararg attributes: Pair<String, String>,
    ) {
        start(name, attributes)
        writer.writeCharacters(text)
        writer.writeEndElement()
    }

    /** Ein Element mit Elementen darin, eine Ebene tiefer eingerückt. */
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

    /** Ein Element ohne Inhalt, geschrieben als `<name/>`. */
    fun empty(name: String) {
        indent()
        val (prefix, localName) = split(name)
        writer.writeEmptyElement(prefix, localName, namespaceOf(prefix))
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
