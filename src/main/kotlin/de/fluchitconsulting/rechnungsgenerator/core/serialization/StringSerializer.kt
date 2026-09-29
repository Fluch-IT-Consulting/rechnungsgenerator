package de.fluchitconsulting.rechnungsgenerator.core.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Per Delegation, damit der Serializer ein benanntes Objekt bleibt. [name] muss je Typ verschieden sein. */
fun <T> stringSerializer(
    name: String,
    encode: (T) -> String,
    decode: (String) -> T,
): KSerializer<T> =
    object : KSerializer<T> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(encode(value))

        override fun deserialize(decoder: Decoder): T = decode(decoder.decodeString())
    }

/**
 * Ersetzt die Meldung des BigDecimal-Konstruktors, die den Wert nicht nennt; Zeile und Spalte ergänzt kaml nicht
 * (nachgemessen). `Identifiers.kt` übernimmt die Meldung dagegen, weil dort schon Brauchbares steht.
 */
internal inline fun <T> parsedNumber(raw: String, was: String, build: (String) -> T): T =
    try {
        build(raw)
    } catch (e: NumberFormatException) {
        throw SerializationException("Kein gültiger $was: '$raw'", e)
    }
