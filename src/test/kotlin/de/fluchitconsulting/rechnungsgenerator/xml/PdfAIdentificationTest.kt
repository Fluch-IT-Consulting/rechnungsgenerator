package de.fluchitconsulting.rechnungsgenerator.xml

import de.fluchitconsulting.rechnungsgenerator.generate.typesetting.EInvoiceEmbedder
import de.fluchitconsulting.rechnungsgenerator.toolchain.capturingStandardError
import de.fluchitconsulting.rechnungsgenerator.toolchain.validation.PdfAProfile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.common.PDMetadata
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readBytes

/** Die zwei echten PDF stammen aus `erzeuge`, die übrigen Fälle bauen ihr XMP selbst. */
class PdfAIdentificationTest : FunSpec({

    val a1b = Path.of("src/test/resources/rechnung-a1b.pdf")

    fun identificationOf(pdf: Path) = Loader.loadPDF(pdf.toFile()).use { PdfAIdentification.of(it) }

    fun identificationOf(xmp: String?) =
        PDDocument().use { document ->
            if (xmp != null) document.documentCatalog.metadata = PDMetadata(document, xmp.byteInputStream())
            PdfAIdentification.of(document)
        }

    fun xmpWith(description: String) =
        """
        <x:xmpmeta xmlns:x="adobe:ns:meta/">
          <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
            $description
          </rdf:RDF>
        </x:xmpmeta>
        """.trimIndent()

    test("das PDF aus erzeuge gibt sich als PDF/A-1b aus") {
        val identification = identificationOf(a1b)!!

        identification.label shouldBe "PDF/A-1b"
        identification.profile shouldBe PdfAProfile.PDFA_1B
    }

    test("mit E-Rechnung gibt es sich als PDF/A-3u aus") {
        val xml = Path.of("src/test/resources/rechnung-erwartet.xml").readBytes()
        val pdf = createTempDirectory("pdfa-identification-test").resolve("e-rechnung.pdf")
        EInvoiceEmbedder.embed(a1b, xml, pdf)

        identificationOf(pdf)!!.profile shouldBe PdfAProfile.PDFA_3U
    }

    test("die Angabe gilt auch als Attribut der rdf:Description") {
        val xmp =
            xmpWith(
                """<rdf:Description rdf:about="" xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/" """ +
                    """pdfaid:part="3" pdfaid:conformance="B"/>""",
            )

        identificationOf(xmp)!!.profile shouldBe PdfAProfile.PDFA_3B
    }

    // Verlangt sind Großbuchstaben, aber darüber urteilt der Prüfer.
    test("die Stufe zählt ohne Rücksicht auf die Schreibweise") {
        val xmp =
            xmpWith(
                """<rdf:Description rdf:about="" xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/">""" +
                    "<pdfaid:part>3</pdfaid:part><pdfaid:conformance>a</pdfaid:conformance></rdf:Description>",
            )

        identificationOf(xmp)!!.profile shouldBe PdfAProfile.PDFA_3A
    }

    test("ein Profil, das der Generator nicht kennt, steht mit Namen da, aber ohne Profil zum Prüfen") {
        val xmp =
            xmpWith(
                """<rdf:Description rdf:about="" xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/" """ +
                    """pdfaid:part="2" pdfaid:conformance="B"/>""",
            )

        val identification = identificationOf(xmp)!!

        identification.label shouldBe "PDF/A-2b"
        identification.profile.shouldBeNull()
    }

    test("ohne XMP gibt es sich nicht als PDF/A aus") {
        identificationOf(xmp = null).shouldBeNull()
    }

    test("ein XMP ohne pdfaid:part gibt es nicht als PDF/A aus") {
        val xmp =
            xmpWith(
                """<rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/">""" +
                    "<dc:format>application/pdf</dc:format></rdf:Description>",
            )

        identificationOf(xmp).shouldBeNull()
    }

    test("ein XMP, das kein XML ist, gibt es nicht als PDF/A aus und schreibt nichts auf stderr") {
        val (identification, stderr) = capturingStandardError { identificationOf("<x:xmpmeta pdfaid:part=\"3\">") }

        identification.shouldBeNull()
        stderr shouldBe ""
    }
})
