package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import java.time.LocalDate

/**
 * Alles, woraus ein PDF entsteht – der Eingabewert der Naht (#202).
 *
 * "Dieselben Quellen ergeben dasselbe PDF" gilt damit wörtlich für einen Wert:
 * Was hier steht, geht in die Bytes des Dokuments ein, und was nicht hier
 * steht, tut es nicht. [creationDate] gehört deshalb dazu, obwohl es weder
 * gesetzt noch eingebunden wird – über `SOURCE_DATE_EPOCH` bestimmt es das
 * Erstellungsdatum im PDF. Es kommt von außen statt von der Uhr, damit ein
 * zweiter Lauf dasselbe Dokument ergibt.
 *
 * [files] sind weitere Quellen, die [tex] unter ihrem Namen einbindet – etwa
 * `logo.tex` (#198). Bytes und nicht Text, weil ein Bild später auch welche
 * ist.
 *
 * Nicht dabei: Arbeitsverzeichnis und Jobname. Beide sagen, *wohin* gesetzt
 * wird, nicht *woraus*, und sie bleiben Sache des Kommandos – es legt das
 * Verzeichnis an, räumt es auf und kennt die Zieldatei.
 *
 * Keine `data class`, aus demselben Grund wie bei `RenderedInvoice`: Ihr
 * `equals` verglich die [ByteArray]s nach Identität.
 */
class PdfSources(
    val tex: String,
    val xmpMetadata: String,
    val creationDate: LocalDate,
    val files: Map<String, ByteArray> = emptyMap(),
)
