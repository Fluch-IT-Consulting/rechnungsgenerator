package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import de.fluchitconsulting.rechnungsgenerator.toolchain.BuiltInPdfAValidator
import de.fluchitconsulting.rechnungsgenerator.toolchain.PdfAProfile
import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSName
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readBytes

/**
 * Was der Embedder aus einem PDF/A-1b macht – ohne LuaLaTeX.
 *
 * Drei der Zusagen hängen an Eigenheiten von Mustang, die es nirgends
 * verspricht (ADR-0023): dass das Dokument nach `setXML` fertig vorbereitet
 * ist, dass ein weiterer Aufruf das XML ein zweites Mal anhinge, und welche
 * Metadaten es von sich aus ersetzt. Ein Versionssprung von Mustang wird
 * zuerst hier gelesen.
 *
 * `rechnung-a1b.pdf` ist die Beispielrechnung, wie `erzeuge` sie ohne
 * `--zugferd` setzt, `rechnung-erwartet.xml` ihr XML.
 */
class EInvoiceEmbedderTest : FunSpec({

    val source = Path.of("src/test/resources/rechnung-a1b.pdf")
    val xml = Path.of("src/test/resources/rechnung-erwartet.xml").readBytes()
    val dir = createTempDirectory("einvoice-embedder-test")

    fun embedded(name: String): Path = dir.resolve(name).also { EInvoiceEmbedder.embed(source, xml, it) }

    test("das Ergebnis ist ein gültiges PDF/A-3u") {
        val result = BuiltInPdfAValidator.validate(embedded("gueltig.pdf"), PdfAProfile.PDFA_3U)

        withClue(result.describe()) { result.valid shouldBe true }
    }

    // Mit einer Pause dazwischen: Datumsangaben im PDF sind auf die Sekunde
    // genau, und zwei Läufe in derselben Sekunde ergäben dieselben Bytes auch
    // dann, wenn Mustang die Uhr einsetzte.
    test("dieselben Quellen ergeben dieselben Bytes, auch eine Sekunde später") {
        val first = embedded("erster-lauf.pdf").readBytes()
        Thread.sleep(1_100)
        val second = embedded("zweiter-lauf.pdf").readBytes()

        first.contentEquals(second) shouldBe true
    }

    test("das XML steht genau einmal darin, als factur-x.xml und Alternative der Rechnung") {
        Loader.loadPDF(embedded("anhang.pdf").toFile()).use { document ->
            val attachments = document.documentCatalog.names.embeddedFiles.names
            attachments.keys shouldBe setOf("factur-x.xml")
            document.documentCatalog.cosObject
                .getCOSArray(COSName.getPDFName("AF"))
                .size() shouldBe 1

            val attachment = attachments.getValue("factur-x.xml")
            attachment.cosObject.getNameAsString(COSName.getPDFName("AFRelationship")) shouldBe "Alternative"
            attachment.embeddedFile.toByteArray().contentEquals(xml) shouldBe true
        }
    }

    test("Metadaten und Daten stammen aus dem PDF/A-1b") {
        val (originalInfo, originalXmp) =
            Loader.loadPDF(source.toFile()).use {
                it.documentInformation to String(it.documentCatalog.metadata.toByteArray())
            }
        val documentId = Regex("<xmpMM:DocumentID>(.*?)</xmpMM:DocumentID>").find(originalXmp)!!.groupValues[1]

        Loader.loadPDF(embedded("metadaten.pdf").toFile()).use { document ->
            val info = document.documentInformation
            info.title shouldBe originalInfo.title
            info.author shouldBe originalInfo.author
            info.subject shouldBe originalInfo.subject
            info.creator shouldBe originalInfo.creator
            info.producer shouldBe originalInfo.producer
            info.creationDate shouldBe originalInfo.creationDate
            info.modificationDate shouldBe originalInfo.modificationDate
            document.documentCatalog.names.embeddedFiles.names
                .getValue("factur-x.xml")
                .embeddedFile.creationDate shouldBe originalInfo.creationDate

            val xmp = String(document.documentCatalog.metadata.toByteArray())
            xmp shouldContain "Rechnung R26-042 vom 14. August 2026"
            xmp shouldContain "de-DE"
            xmp shouldContain documentId
            xmp shouldContain "<xmp:CreateDate>2026-08-14T00:00:00+00:00</xmp:CreateDate>"
            xmp shouldContain "<pdfaid:part>3</pdfaid:part>"
            xmp shouldContain "<pdfaid:conformance>U</pdfaid:conformance>"
            xmp shouldContain "<fx:ConformanceLevel>EXTENDED</fx:ConformanceLevel>"
            xmp shouldNotContain "mustangproject"
        }
    }

    test("ein Eingang, der kein PDF ist, kommt als ToolchainException an") {
        val error =
            shouldThrow<ToolchainException> {
                EInvoiceEmbedder.embed(Path.of("src/test/resources/kein-pdf.pdf"), xml, dir.resolve("nichts.pdf"))
            }

        error.message!! shouldContain "kein-pdf.pdf"
    }
})
