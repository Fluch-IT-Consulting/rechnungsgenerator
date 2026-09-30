package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.generate.typesetting.PdfSources

/** Keine `data class`: Ihr `equals` verglich die `ByteArray`s nach Identität. */
class RenderedInvoice(
    val sources: PdfSources,
    val includesLogo: Boolean,
)
