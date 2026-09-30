package de.fluchitconsulting.rechnungsgenerator.toolchain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldNotContain
import org.xml.sax.SAXParseException
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

/** Die Vorgabe des JDK setzte den Inhalt der Datei ins Dokument (nachgemessen unter JDK 26, #494). */
class XmlParserTest : FunSpec({

    val dir = createTempDirectory("xmlparser-test")
    val secret = dir.resolve("geheim.txt").also { it.writeText("GEHEIM-494") }
    val dtd = dir.resolve("fremd.dtd").also { it.writeText("<!ENTITY x \"GEHEIM-494\">") }

    test("eine externe Entität oder DTD wird abgewiesen, der Inhalt der Datei steht nirgends") {
        mapOf(
            "Entität" to """<!DOCTYPE r [<!ENTITY x SYSTEM "${secret.toUri()}">]><r>&x;</r>""",
            "DTD" to """<!DOCTYPE r SYSTEM "${dtd.toUri()}"><r>&x;</r>""",
        ).forEach { (kind, xml) ->
            listOf(false, true).forEach { namespaceAware ->
                withClue("$kind, namespaceAware = $namespaceAware") {
                    val error = shouldThrow<SAXParseException> { XmlParser.parse(xml.toByteArray(), namespaceAware) }

                    error.message!! shouldNotContain "GEHEIM-494"
                }
            }
        }
    }
})
