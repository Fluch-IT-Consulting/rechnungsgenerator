package de.fluchitconsulting.rechnungsgenerator.core.serialization

import com.charleskorn.kaml.YamlException
import com.charleskorn.kaml.YamlInput
import com.charleskorn.kaml.YamlPath
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Ein Werttyp lehnt seine Eingabe ab. [stringSerializer] übersetzt das, damit `YamlLoader` Datei und Grund nennt (#52). */
open class InvalidValueException(message: String) : IllegalArgumentException(message)

/** Per Delegation, damit der Serializer ein benanntes Objekt bleibt. [name] muss je Typ verschieden sein. */
fun <T> stringSerializer(
    name: String,
    encode: (T) -> String,
    decode: (String) -> T,
): KSerializer<T> =
    object : KSerializer<T> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(encode(value))

        override fun deserialize(decoder: Decoder): T {
            val raw = decoder.decodeString()
            return try {
                decode(raw)
            } catch (e: InvalidValueException) {
                throw rejected(decoder, e)
            } catch (e: SerializationException) {
                throw rejected(decoder, e)
            }
        }
    }

/**
 * Ein abgelehnter Wert samt Fundstelle (#545). kaml reicht nur eine [YamlException] mit Pfad nach oben weiter; nur
 * hier ist der Pfad noch bekannt.
 */
internal class RejectedValueException(message: String, path: YamlPath, cause: Throwable) : YamlException(message, path, cause)

private fun rejected(decoder: Decoder, e: Exception): RejectedValueException =
    RejectedValueException(checkNotNull(e.message), (decoder as YamlInput).getCurrentPath(), e)

/**
 * Ersetzt die Meldung des BigDecimal-Konstruktors, die den Wert nicht nennt. `Identifiers.kt` übernimmt die Meldung
 * dagegen, weil dort schon Brauchbares steht.
 */
internal inline fun <T> parsedNumber(raw: String, was: String, build: (String) -> T): T =
    try {
        build(raw)
    } catch (e: NumberFormatException) {
        throw SerializationException("Kein gültiger $was: '$raw'", e)
    }
