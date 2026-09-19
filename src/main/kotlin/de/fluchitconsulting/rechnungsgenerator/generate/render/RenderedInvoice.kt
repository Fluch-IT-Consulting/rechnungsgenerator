package de.fluchitconsulting.rechnungsgenerator.generate.render

/**
 * Was der Renderer aus einer Rechnung macht: den Quelltext und die Dateien, die
 * er unter ihrem Namen einbindet (#198). Beides zusammen sind die Quellen des
 * PDF; wer nur [tex] weitergibt, setzt ein anderes Dokument.
 *
 * Keine `data class`, aus demselben Grund wie beim Logo: Ihr `equals` verglich
 * die [ByteArray]s nach Identität.
 */
class RenderedInvoice(
    val tex: String,
    val files: Map<String, ByteArray>,
)
