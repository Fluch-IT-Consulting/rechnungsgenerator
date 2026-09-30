package de.fluchitconsulting.rechnungsgenerator.core.model

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.io.path.readText

/** Eine Eingabedatei ist nicht zu lesen; die Meldung nennt Datei und Grund. */
class InputFileException(message: String) : Exception(message)

// kaml im strikten Modus: Ein unbekannter Schlüssel ist ein Fehler, ein Tippfehler fällt so auf.
object YamlLoader {
    private val yaml = Yaml.default

    /** Ohne Logo: Ohne Datei gibt es kein Verzeichnis, gegen das sein Pfad gälte. */
    fun parseSellerConfig(text: String): SellerConfig {
        val config = decodeSellerConfig(text)
        if (config.logoFile != null) {
            throw SerializationException("logo: Ein Logo lässt sich nur mit loadSellerConfig lesen, das den Ort der Stammdaten kennt.")
        }
        return config
    }

    fun parseInvoice(text: String): Invoice = decode(Invoice.serializer(), text)

    fun loadSellerConfig(path: Path): SellerConfig {
        val config = read(path, ::decodeSellerConfig)
        val logoFile = config.logoFile ?: return config
        return config.copy(logo = readLogo(path, logoFile))
    }

    fun loadInvoice(path: Path): Invoice = read(path, ::parseInvoice)

    /** Jeder Fehler nennt die Datei: kaml nennt nur den Schlüssel, und `erzeuge` liest zwei Dateien. */
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

    private fun decodeSellerConfig(text: String): SellerConfig = decode(SellerConfig.serializer(), text)

    /** `decodeFromString` in zwei Schritten: Dazwischen sehen [ControlCharacters] und [BlankValues] den Knotenbaum. */
    private fun <T> decode(
        deserializer: DeserializationStrategy<T>,
        text: String,
    ): T {
        val root = yaml.parseToYamlNode(text)
        val found = ControlCharacters.findIn(root)
        if (found.isNotEmpty()) {
            throw SerializationException(
                "Steuerzeichen gehören weder ins PDF noch in die E-Rechnung:\n" +
                    found.joinToString("\n") { "  $it" } +
                    "\nSie entstehen in doppelten Anführungszeichen aus Escapes wie \\x0B oder \\r. " +
                    "Tabulator und Zeilenumbruch sind erlaubt.",
            )
        }
        val blank = BlankValues.findIn(root)
        if (blank.isNotEmpty()) {
            throw SerializationException(
                "Ein leerer Wert nennt nichts:\n" +
                    blank.joinToString("\n") { "  $it" } +
                    "\nIst nichts gemeint, entfällt der Schlüssel ganz; ein Pflichtfeld braucht einen Wert.",
            )
        }
        return yaml.decodeFromYamlNode(deserializer, root)
    }

    /** Relativ zu den Stammdaten, nicht zum Arbeitsverzeichnis. Jede Meldung nennt beide Dateien. */
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

    /** Die zwei häufigen Fälle tragen als Meldung nur den Pfad. */
    private fun reasonFor(e: IOException): String =
        when (e) {
            // Die vollen Namen, weil kotlin.io gleichnamige Ausnahmen mitbringt.
            is java.nio.file.NoSuchFileException -> "es gibt sie nicht"
            is java.nio.file.AccessDeniedException -> "keine Leseberechtigung"
            else -> e.message ?: e.toString()
        }
}
