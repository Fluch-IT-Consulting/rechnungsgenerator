package de.fluchitconsulting.rechnungsgenerator.core.model

import com.charleskorn.kaml.YamlNode

/**
 * Leere oder nur aus Leerraum bestehende Werte einer YAML-Datei (#504), geprüft wie [ControlCharacters] am
 * Knotenbaum: Ein neues Feld braucht keine eigene Zeile. Ein Schlüssel ganz ohne Wert ist YAML-null und nicht
 * betroffen; darauf baut das leere `vereinnahmt-am:` im Block von `abschlaege`.
 */
internal object BlankValues {
    fun findIn(root: YamlNode): List<String> =
        scalarsOf(root)
            .filter { it.content.isBlank() }
            .map { it.location() }
            .toList()
}
