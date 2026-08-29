package de.fluchitconsulting.rechnung.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
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

// `object` = Singleton-Deklaration: genau eine Instanz, ideal für zustandslose Helfer.
object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

    // Einzeilige Funktionen schreibt man in Kotlin als "expression body" mit `=` statt Block.
    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())

    override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
}

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())

    // LocalDate.parse erwartet ISO-8601 (JJJJ-MM-TT) – genau das YAML-Datumsformat.
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
