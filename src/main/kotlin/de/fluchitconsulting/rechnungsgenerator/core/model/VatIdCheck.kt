package de.fluchitconsulting.rechnungsgenerator.core.model

import de.fluchitconsulting.rechnungsgenerator.core.serialization.enumSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Der dokumentierte Nachweis der Bestätigungsabfrage. Die Abfrage selbst macht das Programm bewusst nicht: Sie
 * setzt eine Registrierung beim BZSt voraus und muss ein aufbewahrbares Protokoll liefern.
 */
@Serializable
data class VatIdCheck(
    @SerialName("am") val date: IsoDate,
    @SerialName("verfahren") val method: CheckMethod,
    /** Aktenzeichen des Protokolls. */
    @SerialName("nachweis") val reference: String? = null,
)

@Serializable(with = CheckMethodSerializer::class)
enum class CheckMethod(val label: String) {
    /** VIES: nur gültig oder ungültig. */
    SIMPLE("einfach"),

    /** Gleicht auch Name und Anschrift ab; nur sie verschafft Vertrauensschutz. */
    QUALIFIED("qualifiziert"),
    ;

    override fun toString(): String = label
}

object CheckMethodSerializer :
    KSerializer<CheckMethod> by enumSerializer(
        name = "CheckMethod",
        values = CheckMethod.entries,
        unknown = "Unbekanntes Prüfverfahren",
        normalize = { it.trim().lowercase() },
        keyOf = CheckMethod::label,
    )
