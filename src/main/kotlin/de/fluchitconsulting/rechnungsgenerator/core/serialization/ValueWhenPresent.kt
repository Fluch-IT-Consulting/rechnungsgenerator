package de.fluchitconsulting.rechnungsgenerator.core.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Ein optionaler Schlüssel, der einen Wert braucht, sobald er dasteht; nur an der Eigenschaft sieht ein Serializer
 * den leeren Wert. `decodeNotNullMark` und `encodeNull` sind experimentell; ändern sie sich, wird der
 * `YamlLoaderTest` zu `akzentfarbe` rot (#388).
 */
@OptIn(ExperimentalSerializationApi::class)
fun <T : Any> valueWhenPresent(
    serializer: KSerializer<T>,
    empty: String,
): KSerializer<T?> =
    object : KSerializer<T?> {
        override val descriptor: SerialDescriptor = serializer.descriptor.nullable

        override fun serialize(encoder: Encoder, value: T?) {
            if (value == null) encoder.encodeNull() else serializer.serialize(encoder, value)
        }

        override fun deserialize(decoder: Decoder): T? =
            if (decoder.decodeNotNullMark()) serializer.deserialize(decoder) else throw SerializationException(empty)
    }
