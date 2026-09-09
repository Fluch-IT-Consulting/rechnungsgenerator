package de.fluchitconsulting.rechnung.model

import de.fluchitconsulting.rechnung.values.enumSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Der Nachweis, dass die USt-IdNr. des Kunden geprüft wurde.
 *
 * Warum das im Datenmodell steht und nicht bloß im Kopf des Rechnungsstellers:
 * Vor einer Rechnung ohne Umsatzsteuer muss die Nummer des Empfängers bestätigt
 * sein, und die Bestätigung muss dokumentiert werden – sonst nützt sie im
 * Zweifel nichts. Ein Feld, das beim Erzeugen der Rechnung vorhanden sein muss,
 * ist die einzige Form von Erinnerung, die man nicht übersehen kann.
 *
 * Die Abfrage selbst macht dieses Programm bewusst NICHT. Die qualifizierte
 * Bestätigungsabfrage beim BZSt setzt eine Registrierung voraus und liefert ein
 * Protokoll, das aufzubewahren ist; ein nebenbei abgesetzter Online-Aufruf
 * erzeugt keinen Nachweis, den man in zehn Jahren noch vorlegen kann.
 */
@Serializable
data class VatIdCheck(
    @SerialName("am") val date: IsoDate,
    @SerialName("verfahren") val method: CheckMethod,
    /** Aktenzeichen des Protokolls, damit der Nachweis wiederauffindbar ist. */
    @SerialName("nachweis") val reference: String? = null,
)

@Serializable(with = CheckMethodSerializer::class)
enum class CheckMethod(val label: String) {
    /** VIES-Abfrage: sagt nur gültig oder ungültig. */
    SIMPLE("einfach"),

    /**
     * Qualifizierte Bestätigungsabfrage beim BZSt: gleicht zusätzlich Name und
     * Anschrift ab. Nur sie verschafft Vertrauensschutz.
     */
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
