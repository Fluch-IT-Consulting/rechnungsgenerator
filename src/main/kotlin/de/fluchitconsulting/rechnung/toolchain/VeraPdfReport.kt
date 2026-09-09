package de.fluchitconsulting.rechnung.toolchain

import org.w3c.dom.Element
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Liest den Bericht, den veraPDF ausgibt.
 *
 * Wie [LatexLog] ein eigenes Modul, damit die Auswertung ohne einen echten
 * Programmlauf zu prüfen ist: Vorher führte der einzige Weg zu diesen Zeilen
 * über ein absichtlich nicht konformes PDF, das erst erzeugt und dann geprüft
 * werden musste.
 */
object VeraPdfReport {
    /**
     * Schneidet weg, was vor dem XML steht.
     *
     * veraPDF ist selbst eine Java-Anwendung und schreibt JVM-Warnungen auf
     * stderr ("has been mutated reflectively ..."). Da der Aufruf stderr und
     * stdout zusammenführt, stehen die vor dem Bericht – der Parser bricht dann
     * mit "Content is not allowed in prolog" ab. Deshalb wird ab dem Prolog
     * gelesen.
     */
    fun extractXml(output: String): String {
        val start = output.indexOf("<?xml")
        if (start < 0) throw ToolchainException("verapdf lieferte keinen XML-Bericht:\n${output.take(800)}")
        return output.substring(start)
    }

    /** Die verletzten Regeln aus dem Bericht; bestandene Regeln stehen dort auch drin. */
    fun parseViolations(xml: String): List<PdfAViolation> {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                // Externe Entitäten abschalten: Standard-Hygiene beim XML-Parsen,
                // auch wenn die Eingabe hier aus dem eigenen Prozess stammt.
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }
        val document = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val rules = document.getElementsByTagName("rule")
        return (0 until rules.length)
            .map { rules.item(it) as Element }
            .filter { it.getAttribute("status") == "failed" }
            .map { rule ->
                PdfAViolation(
                    clause = rule.getAttribute("clause"),
                    testNumber = rule.getAttribute("testNumber"),
                    description = rule.childText("description"),
                    errorMessage = rule.childText("errorMessage"),
                )
            }
    }

    // Kleine private Extension: holt den Text des ersten Kindelements mit diesem
    // Namen. Erspart an vier Stellen dieselbe Null- und Längenprüfung.
    private fun Element.childText(tag: String): String =
        getElementsByTagName(tag)
            .item(0)
            ?.textContent
            ?.trim()
            .orEmpty()
}
