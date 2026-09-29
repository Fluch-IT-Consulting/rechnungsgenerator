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

// Serializer für JDK-Typen, die wir nicht selbst annotieren können; die Typaliase binden sie an.

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
        decode = LocalDate::parse,
    )

object YearMonthSerializer :
    KSerializer<YearMonth> by stringSerializer(
        name = "YearMonth",
        encode = YearMonth::toString,
        // Übersetzt, weil die Meldung des JDK nicht sagt, was erwartet ist.
        decode = { raw ->
            try {
                YearMonth.parse(raw)
            } catch (e: DateTimeParseException) {
                throw SerializationException("Kein gültiger Monat: '$raw' (erwartet JJJJ-MM, etwa 2026-08)", e)
            }
        },
    )
