package de.fluchitconsulting.rechnungsgenerator.core.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Das Gerüst für einen optionalen Schlüssel, der einen Wert braucht, sobald er
 * dasteht.
 *
 * Gewöhnlich heißt ein Schlüssel ohne Wert dasselbe wie keiner: Der Typ ist
 * nullbar, und `null` kommt still an. Mit diesem Serializer an der
 * Eigenschaft greift ihr Vorgabewert nur, wenn der Schlüssel fehlt; steht er
 * ohne Wert da, wirft das Lesen [empty] als Meldung.
 *
 * Benutzt wird er per Delegation wie [stringSerializer], an der Eigenschaft
 * mit `@Serializable(with = …)`: Nur dort, und nicht am Typ, kann ein
 * Serializer den leeren Wert überhaupt sehen.
 *
 * `decodeNotNullMark` und `encodeNull` gelten in kotlinx.serialization als
 * experimentell und dürfen sich ohne Übergangsfrist ändern. Einen stabilen
 * Weg, "steht ohne Wert da" von "fehlt" zu unterscheiden, gibt es nicht, und
 * genau diese Unterscheidung ist der Zweck. Ändert ein Update sie, wird der
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
