package de.fluchitconsulting.rechnungsgenerator.toolchain

import org.w3c.dom.Document
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException
import java.io.ByteArrayInputStream
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Liest XML, ohne auf stderr zu schreiben und ohne nach draußen zu greifen.
 *
 * Ohne eigenen [ErrorHandler] legt der Parser des JDK einen an, der jeden
 * Fehler als Zeile `[Fatal Error] …` auf `System.err` druckt – an Clikt
 * vorbei, vor der eigenen Meldung und auch dann, wenn der Aufrufer die
 * Ausnahme fängt (#395). Deshalb geht jedes XML, das dieses Programm liest,
 * hier durch; externe DTDs und Schemata lädt keines davon.
 *
 * Den Grund eines Fehlers schreibt der Parser in der Sprache der JVM, und er
 * landet in einer Meldung des Programms – die ist deutsch, auf jedem Rechner
 * (#425). Die Sprache gilt deshalb für diesen Parser und nicht über
 * `Locale.setDefault`, das in derselben JVM auch veraPDF und Mustang träfe.
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

    /**
     * Warnungen und behebbare Fehler übergeht der Parser weiter wie mit dem
     * Handler des JDK, nur ohne Ausgabe; einen fatalen wirft er ohnehin.
     */
    private object Silent : ErrorHandler {
        override fun warning(exception: SAXParseException) = Unit

        override fun error(exception: SAXParseException) = Unit

        override fun fatalError(exception: SAXParseException): Unit = throw exception
    }
}
