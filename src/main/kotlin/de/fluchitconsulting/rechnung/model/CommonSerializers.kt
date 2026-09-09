package de.fluchitconsulting.rechnung.model

import de.fluchitconsulting.rechnung.values.parsedNumber
import de.fluchitconsulting.rechnung.values.stringSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDate

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

// `object` = Singleton-Deklaration: genau eine Instanz, ideal für zustandslose
// Helfer. Das Gerüst darunter kommt aus stringSerializer – hier steht nur noch,
// wie aus dem Wert Text wird und zurück.
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
