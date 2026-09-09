package de.fluchitconsulting.rechnung.values

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Das Gerüst für einen Werttyp, der in der YAML als Zeichenkette steht.
 *
 * Zwölfmal stand hier dasselbe: ein [PrimitiveSerialDescriptor], ein
 * `encodeString`, ein `decodeString`. Interessant an einem Serializer sind nur
 * die beiden Umwandlungen – [encode] und [decode] –, alles andere ist Form.
 *
 * Benutzt wird er per Delegation, damit der Serializer ein benanntes Objekt
 * bleibt und `@Serializable(with = …)` weiter darauf zeigen kann:
 *
 * ```
 * object MoneySerializer : KSerializer<Money> by stringSerializer("Money", …)
 * ```
 *
 * [name] geht in den Deskriptor und muss je Typ verschieden sein – kotlinx.
 * serialization unterscheidet die Typen daran.
 */
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
 * Baut einen Zahlwert aus Text und ersetzt die technische Ausnahme durch eine,
 * die den Wert nennt.
 *
 * Der BigDecimal-Konstruktor wirft ein nacktes "Character n is neither a
 * decimal digit number, decimal point, nor ..." – ohne den Wert und ohne Bezug
 * zur Rechnung. Zeile und Spalte ergänzt kaml NICHT (nachgemessen); dafür
 * müsste der Decoder auf YamlInput geprüft werden, was den Serializer an ein
 * konkretes Format bände.
 *
 * Für die Kennungen (IBAN, BIC, USt-IdNr.) gibt es in `Identifiers.kt` ein
 * Gegenstück, das die Meldung der Ausnahme **übernimmt** statt sie zu ersetzen:
 * Dort steht schon etwas Brauchbares drin ("Prüfsumme falsch"), hier nicht.
 */
internal inline fun <T> parsedNumber(raw: String, was: String, build: (String) -> T): T =
    try {
        build(raw)
    } catch (e: NumberFormatException) {
        throw SerializationException("Kein gültiger $was: '$raw'", e)
    }
