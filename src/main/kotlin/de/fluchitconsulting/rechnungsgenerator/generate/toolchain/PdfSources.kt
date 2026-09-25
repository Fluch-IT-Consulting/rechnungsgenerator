package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import java.time.LocalDate

/**
 * Alles, woraus ein PDF entsteht – der Eingabewert der Naht (#202).
 *
 * "Dieselben Quellen ergeben dasselbe PDF" gilt damit wörtlich für einen Wert:
 * Was hier steht, geht in die Bytes des Dokuments ein, und was nicht hier
 * steht, tut es nicht – bis auf den Jobnamen, siehe unten. [creationDate]
 * gehört deshalb dazu, obwohl es weder gesetzt noch eingebunden wird – über
 * `SOURCE_DATE_EPOCH` bestimmt es das Erstellungsdatum im PDF. Es kommt von außen statt von der Uhr, damit ein
 * zweiter Lauf dasselbe Dokument ergibt.
 *
 * [files] sind weitere Quellen, die [tex] unter ihrem Namen einbindet – etwa
 * `grundlage.sty` und `logo.tex` (ADR-0022, #198). Bytes und nicht Text,
 * weil ein Bild später auch welche ist.
 *
 * Nicht dabei: Arbeitsverzeichnis und Jobname. Beide sagen, *wohin* gesetzt
 * wird, nicht *woraus*, und sie bleiben Sache des Kommandos – es legt das
 * Verzeichnis an und räumt es auf. Der Jobname geht trotzdem in die Bytes
 * ein: pdfx bildet aus ihm die DocumentID im XMP (#297). Gleich ist das PDF
 * also bei denselben Quellen unter demselben Jobnamen; das Kommando nimmt
 * dafür immer `fileBaseName`.
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
