package de.fluchitconsulting.rechnung

import com.charleskorn.kaml.Yaml
import de.fluchitconsulting.rechnung.model.Invoice
import de.fluchitconsulting.rechnung.model.SellerConfig
import java.nio.file.Path
import kotlin.io.path.readText

// Dünne Schicht über kaml: liest YAML-Text bzw. -Dateien in unsere Datenklassen.
// kaml läuft im strikten Modus (Default): unbekannte Schlüssel sind ein Fehler –
// Tippfehler in der YAML-Datei fallen so sofort auf, statt still ignoriert zu werden.
object YamlLoader {
    private val yaml = Yaml.default

    fun parseSellerConfig(text: String): SellerConfig = yaml.decodeFromString(SellerConfig.serializer(), text)

    fun parseInvoice(text: String): Invoice = yaml.decodeFromString(Invoice.serializer(), text)

    fun loadSellerConfig(path: Path): SellerConfig = parseSellerConfig(path.readText())

    fun loadInvoice(path: Path): Invoice = parseInvoice(path.readText())
}
