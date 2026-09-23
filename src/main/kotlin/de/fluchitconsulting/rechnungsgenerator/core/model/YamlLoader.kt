package de.fluchitconsulting.rechnungsgenerator.core.model

import com.charleskorn.kaml.Yaml
import de.fluchitconsulting.rechnungsgenerator.core.values.Logo
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.io.path.readText

/**
 * Wird geworfen, wenn eine Eingabedatei nicht zu lesen ist – mit der Datei und
 * dem Grund in der Meldung.
 */
class InputFileException(message: String) : Exception(message)

// Dünne Schicht über kaml: liest YAML-Text bzw. -Dateien in unsere Datenklassen.
// kaml läuft im strikten Modus (Default): unbekannte Schlüssel sind ein Fehler –
// Tippfehler in der YAML-Datei fallen so sofort auf, statt still ignoriert zu werden.
object YamlLoader {
    private val yaml = Yaml.default

    /**
     * Stammdaten aus Text. Ein Logo lehnt sie ab: Ohne Datei gibt es kein
     * Verzeichnis, gegen das sein Pfad gälte.
     */
    fun parseSellerConfig(text: String): SellerConfig {
        val config = decodeSellerConfig(text)
        if (config.logoFile != null) {
            throw SerializationException("logo: Ein Logo lässt sich nur mit loadSellerConfig lesen, das den Ort der Stammdaten kennt.")
        }
        return config
    }

    fun parseInvoice(text: String): Invoice = yaml.decodeFromString(Invoice.serializer(), text)

    fun loadSellerConfig(path: Path): SellerConfig {
        val config = read(path, ::decodeSellerConfig)
        val logoFile = config.logoFile ?: return config
        return config.copy(logo = readLogo(path, logoFile))
    }

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

    private fun decodeSellerConfig(text: String): SellerConfig = yaml.decodeFromString(SellerConfig.serializer(), text)

    /**
     * Liest das Logo, das die Stammdaten unter [sellerConfig] nennen. Ein
     * relativer Pfad gilt ab ihrem Verzeichnis, nicht ab dem Arbeitsverzeichnis:
     * Die Stammdaten liegen meist anderswo als der Ort, von dem aus man
     * `erzeuge` aufruft. `resolve` lässt einen absoluten Pfad, wie er ist.
     *
     * Jede Meldung nennt beide Dateien – wer nur "logo.tex fehlt" liest, weiß
     * nicht, welche Stammdaten danach gefragt haben.
     */
    private fun readLogo(
        sellerConfig: Path,
        logoFile: String,
    ): Logo {
        val source =
            sellerConfig
                .toAbsolutePath()
                .parent
                .resolve(logoFile)
                .normalize()
        val named = "Die Stammdaten $sellerConfig nennen unter logo die Datei $source"
        if (!Logo.isAllowed(source)) {
            throw InputFileException("$named. ${Logo.unsupported(source)}")
        }
        val content =
            try {
                source.readBytes()
            } catch (e: IOException) {
                throw InputFileException("$named, und sie konnte nicht gelesen werden: ${reasonFor(e)}.")
            }
        return Logo(source, content)
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
