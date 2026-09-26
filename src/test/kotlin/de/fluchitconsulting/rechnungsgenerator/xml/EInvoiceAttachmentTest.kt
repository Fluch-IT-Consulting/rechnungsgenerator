package de.fluchitconsulting.rechnungsgenerator.xml

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.apache.pdfbox.Loader
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

/** Welche Anhänge eines PDF als XML einer E-Rechnung gelten, und mit welchen Bytes. */
class EInvoiceAttachmentTest : FunSpec({

    val dir = createTempDirectory("einvoice-attachment-test")
    val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rsm:CrossIndustryInvoice/>".toByteArray()

    fun attachmentsIn(pdf: Path) = Loader.loadPDF(pdf.toFile()).use { EInvoiceAttachment.allIn(it) }

    test("das XML steht unter jedem Namen, den eine Fassung vorsieht") {
        EInvoiceAttachment.NAMES.forEach { name ->
            val found = attachmentsIn(writePdf(dir.resolve("$name.pdf"), mapOf(name to xml))).single()

            found.name shouldBe name
            found.xml.contentEquals(xml) shouldBe true
        }
    }

    // Die Schreibweise zählt, weil sie ZUGFeRD 1 von 2.0 unterscheidet.
    test("andere Anhänge zählen nicht, auch kein bekannter Name in anderer Schreibweise") {
        val pdf = writePdf(dir.resolve("andere.pdf"), mapOf("rechnung.xml" to xml, "Factur-X.xml" to xml, "logo.pdf" to xml))

        attachmentsIn(pdf).shouldBeEmpty()
    }

    test("auch in einem Kindknoten des Namensbaums wird gesucht") {
        val pdf = writePdf(dir.resolve("kinder.pdf"), mapOf("factur-x.xml" to xml), inKids = true)

        attachmentsIn(pdf).map { it.name } shouldBe listOf("factur-x.xml")
    }

    test("zwei passende Anhänge kommen beide, entschieden wird anderswo") {
        val pdf = writePdf(dir.resolve("zwei.pdf"), mapOf("factur-x.xml" to xml, "xrechnung.xml" to xml))

        attachmentsIn(pdf).map { it.name }.sorted() shouldBe listOf("factur-x.xml", "xrechnung.xml")
    }

    test("das PDF/A-1b aus erzeuge trägt keinen") {
        attachmentsIn(Path.of("src/test/resources/rechnung-a1b.pdf")).shouldBeEmpty()
    }

    test("die Bytes kommen, wie sie eingebettet sind, auch in einer anderen Kodierung als UTF-8") {
        val latin1 = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><a>Größe</a>".toByteArray(Charsets.ISO_8859_1)
        val pdf = writePdf(dir.resolve("latin1.pdf"), mapOf("factur-x.xml" to latin1))

        attachmentsIn(pdf).single().xml.contentEquals(latin1) shouldBe true
    }
})
