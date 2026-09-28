package de.fluchitconsulting.rechnungsgenerator.core.model

import de.fluchitconsulting.rechnungsgenerator.core.serialization.parsedNumber
import de.fluchitconsulting.rechnungsgenerator.core.serialization.stringSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

// Serializer für JDK-Typen, die kotlinx.serialization nicht von Haus aus kennt und
// die wir – anders als Money und Percent – nicht selbst annotieren können.
// Die Typaliase binden sie an und geben dem Wert zugleich einen fachlichen Namen.

/** Eine Menge: dimensionslos (86,5 Std., 1 pauschal), deshalb kein eigener Werttyp. */
typealias Quantity =
    @Serializable(with = BigDecimalSerializer::class)
    BigDecimal

typealias IsoDate =
    @Serializable(with = LocalDateSerializer::class)
    LocalDate

typealias IsoMonth =
    @Serializable(with = YearMonthSerializer::class)
    YearMonth

// Das Gerüst darunter kommt aus stringSerializer – hier steht nur noch, wie aus
// dem Wert Text wird und zurück.
object BigDecimalSerializer :
    KSerializer<BigDecimal> by stringSerializer(
        name = "BigDecimal",
        encode = { it.toPlainString() },
        decode = { parsedNumber(it, "Zahlwert", ::BigDecimal) },
    )

object LocalDateSerializer :
    KSerializer<LocalDate> by stringSerializer(
        name = "LocalDate",
        encode = LocalDate::toString,
        // LocalDate.parse erwartet ISO-8601 (JJJJ-MM-TT) – genau das YAML-Datumsformat.
        decode = LocalDate::parse,
    )

object YearMonthSerializer :
    KSerializer<YearMonth> by stringSerializer(
        name = "YearMonth",
        encode = YearMonth::toString,
        // JJJJ-MM wie das Datum ohne Tag. Übersetzt, weil die Ausnahme des JDK
        // "Text '2026-8' could not be parsed at index 5" sagt und nicht, was
        // erwartet ist.
        decode = { raw ->
            try {
                YearMonth.parse(raw)
            } catch (e: DateTimeParseException) {
                throw SerializationException("Kein gültiger Monat: '$raw' (erwartet JJJJ-MM, etwa 2026-08)", e)
            }
        },
    )
