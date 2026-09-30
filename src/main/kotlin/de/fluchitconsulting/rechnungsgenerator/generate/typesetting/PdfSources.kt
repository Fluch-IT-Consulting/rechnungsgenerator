package de.fluchitconsulting.rechnungsgenerator.generate.typesetting

import de.fluchitconsulting.rechnungsgenerator.toolchain.validation.PdfAProfile
import java.time.LocalDate

/**
 * Alles, woraus ein PDF entsteht (#202): Dieselben Quellen ergeben unter demselben Jobnamen dasselbe PDF. Deshalb
 * gehört [creationDate] dazu, und der Jobname bleibt beim Kommando, obwohl pdfx aus ihm die DocumentID bildet (#297).
 * Keine `data class`: Ihr `equals` verglich die [ByteArray]s nach Identität.
 */
class PdfSources(
    val tex: String,
    val xmpMetadata: String,
    val creationDate: LocalDate,
    val files: Map<String, ByteArray> = emptyMap(),
    val einvoice: ByteArray? = null,
) {
    val pdfaProfile: PdfAProfile get() = if (einvoice == null) PdfAProfile.PDFA_1B else PdfAProfile.PDFA_3U

    fun withEInvoice(xml: ByteArray) = PdfSources(tex, xmpMetadata, creationDate, files, xml)
}
