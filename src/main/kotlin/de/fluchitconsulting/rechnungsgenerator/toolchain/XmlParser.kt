package de.fluchitconsulting.rechnungsgenerator.toolchain

import org.w3c.dom.Document
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException
import java.io.ByteArrayInputStream
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Jedes XML des Programms geht hier durch: Ohne eigenen [ErrorHandler] druckte der Parser des JDK `[Fatal Error]`
 * auf stderr, auch wenn die Ausnahme gefangen wird (#395). Deutsch je Parser und nicht über `Locale.setDefault`,
 * das auch veraPDF und Mustang träfe (#425).
 */
object XmlParser {
    /** Eigenschaft des Xerces im JDK; die API von `javax.xml` kennt keine Sprache je Parser. */
    private const val LOCALE = "http://apache.org/xml/properties/locale"

    /** Wirft [SAXParseException] mit Zeile, Spalte und deutschem Grund, wenn [xml] nicht wohlgeformt ist. */
    fun parse(
        xml: ByteArray,
        namespaceAware: Boolean = false,
    ): Document =
        DocumentBuilderFactory
            .newInstance()
            .apply {
                isNamespaceAware = namespaceAware
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                setAttribute(LOCALE, Locale.GERMAN)
            }.newDocumentBuilder()
            .apply { setErrorHandler(Silent) }
            .parse(ByteArrayInputStream(xml))

    private object Silent : ErrorHandler {
        override fun warning(exception: SAXParseException) = Unit

        override fun error(exception: SAXParseException) = Unit

        override fun fatalError(exception: SAXParseException): Unit = throw exception
    }
}
