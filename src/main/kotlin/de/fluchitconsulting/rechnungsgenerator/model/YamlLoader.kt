package de.fluchitconsulting.rechnungsgenerator.model

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Wird geworfen, wenn eine Eingabedatei nicht zu lesen ist – mit der Datei und
 * dem Grund in der Meldung.
 *
 * Vorher übersetzte jedes Kommando die Fehler des Lesens selbst, und die beiden
 * Fassungen liefen auseinander (#52): `erzeuge` meldete einen Lesefehler als
 * "Die Datei konnte nicht geschrieben werden", weil sein IOException-Zweig für
 * das Ziel gedacht war und das Lesen mitfing; `abschlaege` fing ihn gar nicht,
 * und weil `Main` nur CliktError fängt, endete der Aufruf im Stacktrace.
 */
class InputFileException(message: String) : Exception(message)

// Dünne Schicht über kaml: liest YAML-Text bzw. -Dateien in unsere Datenklassen.
// kaml läuft im strikten Modus (Default): unbekannte Schlüssel sind ein Fehler –
// Tippfehler in der YAML-Datei fallen so sofort auf, statt still ignoriert zu werden.
object YamlLoader {
    private val yaml = Yaml.default

    fun parseSellerConfig(text: String): SellerConfig = yaml.decodeFromString(SellerConfig.serializer(), text)

    fun parseInvoice(text: String): Invoice = yaml.decodeFromString(Invoice.serializer(), text)

    fun loadSellerConfig(path: Path): SellerConfig = read(path, ::parseSellerConfig)

    fun loadInvoice(path: Path): Invoice = read(path, ::parseInvoice)

    /**
     * Liest eine Datei und macht aus jedem Fehler eine Meldung, die die Datei nennt.
     *
     * Beides landet hier, weil beides dasselbe ist, sobald jemand davorsitzt:
     * Diese Datei taugt nicht als Eingabe. Ob das Betriebssystem sie nicht
     * herausrückt, kaml einen Schlüssel nicht kennt oder eine IBAN ihre
     * Prüfsumme nicht besteht, steht im Grund dahinter. Die Wertprüfungen
     * kommen als [SerializationException] an, weil `Identifiers.kt` sie dazu
     * macht.
     *
     * Der Dateiname kommt von hier und nicht vom Aufrufer: kaml nennt nur den
     * Schlüssel, und `erzeuge` liest zwei Dateien.
     */
    private fun <T> read(path: Path, parse: (String) -> T): T {
        val text =
            try {
                path.readText()
            } catch (e: IOException) {
                throw InputFileException("Die Datei $path konnte nicht gelesen werden: ${reasonFor(e)}.")
            }
        return try {
            parse(text)
        } catch (e: SerializationException) {
            throw InputFileException("Die Datei $path konnte nicht gelesen werden:\n${e.message}")
        }
    }

    /**
     * Der Grund im Klartext. Die beiden häufigen Fälle tragen als Meldung nur
     * den Pfad, der dann zweimal dastünde und nichts erklärte.
     */
    private fun reasonFor(e: IOException): String =
        when (e) {
            // Die vollen Namen, weil kotlin.io gleichnamige Ausnahmen mitbringt.
            is java.nio.file.NoSuchFileException -> "es gibt sie nicht"
            is java.nio.file.AccessDeniedException -> "keine Leseberechtigung"
            else -> e.message ?: e.toString()
        }
}
