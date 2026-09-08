package de.fluchitconsulting.rechnung.values

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Die Rechnungsart – EN 16931 BT-3, Codeliste UNTDID 1001.
 *
 * Sie trennt die vier Dokumente, die dieses Programm kennt (die Begriffe stehen
 * in `CONTEXT.md`). Der Unterschied ist keine Formsache: Wer einen Abschlag wie
 * eine gewöhnliche Rechnung abrechnet und später noch einmal die Gesamtleistung,
 * weist die Umsatzsteuer zweimal aus und schuldet sie nach §14c Abs. 1 UStG
 * auch zweimal.
 *
 * [label] ist die Schreibweise in der YAML, [code] die Kennung fürs spätere
 * ZUGFeRD-XML.
 */
@Serializable(with = InvoiceTypeSerializer::class)
enum class InvoiceType(val label: String, val code: String) {
    INVOICE("rechnung", "380"),

    // Der Code heißt in der Liste "Prepayment invoice"; das Wort dieses
    // Projekts ist trotzdem die Abschlagsrechnung – "Anzahlung" steht in
    // CONTEXT.md unter Vermeiden.
    PREPAYMENT("abschlagsrechnung", "386"),

    // Für die Schlussrechnung nennt keine Quelle einen eigenen Code, deshalb
    // derselbe wie bei der gewöhnlichen Rechnung: Sie *ist* die Rechnung über
    // die Gesamtleistung, kenntlich an der Absetzung der Abschläge, nicht am
    // Dokumenttyp. Nicht 384 ("Corrected invoice" – eine Schlussrechnung
    // korrigiert nichts) und nicht die Bau-Codes 875/876/877, die für andere
    // Leistungen eine Falschangabe wären. Ein neutraler Code ist besser als
    // einer, der etwas Falsches behauptet.
    FINAL("schlussrechnung", "380"),

    // Ändert nur den Code und nichts an der Rechnung. Aufgenommen, damit die
    // Unterscheidung zur Abschlagsrechnung im Modell sichtbar ist statt nur im
    // Kopf des Rechnungsstellers: Nicht die Höhe trennt beide, sondern ob die
    // Leistung erbracht ist.
    PARTIAL("teilrechnung", "326"),
    ;

    override fun toString(): String = label

    companion object {
        private val BY_LABEL = entries.associateBy { it.label.lowercase() }

        /** Löst die YAML-Schreibweise auf; unbekannte Arten nennen die zulässigen. */
        fun fromLabel(label: String): InvoiceType =
            BY_LABEL[label.trim().lowercase()]
                ?: throw SerializationException(
                    "Unbekannte Rechnungsart '$label'. Zulässig sind: ${entries.joinToString { it.label }}",
                )
    }
}

object InvoiceTypeSerializer : KSerializer<InvoiceType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("InvoiceType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: InvoiceType) = encoder.encodeString(value.label)

    override fun deserialize(decoder: Decoder): InvoiceType = InvoiceType.fromLabel(decoder.decodeString())
}
