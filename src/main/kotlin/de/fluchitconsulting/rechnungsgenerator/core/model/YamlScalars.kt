package de.fluchitconsulting.rechnungsgenerator.core.model

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlPath
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.YamlTaggedNode

/** Die Werte unter [node], ohne die Schlüssel. Ein Schlüssel ohne Wert ist YAML-null und kein Wert. */
internal fun scalarsOf(node: YamlNode): Sequence<YamlScalar> =
    when (node) {
        is YamlScalar -> {
            sequenceOf(node)
        }
        is YamlMap -> {
            node.entries.values
                .asSequence()
                .flatMap(::scalarsOf)
        }
        is YamlList -> {
            node.items.asSequence().flatMap(::scalarsOf)
        }
        is YamlTaggedNode -> {
            scalarsOf(node.innerNode)
        }
        is YamlNull -> {
            emptySequence()
        }
    }

/** Feld, Zeile und Spalte, etwa `positionen[0].bezeichnung (Zeile 17, Spalte 18)`. */
internal fun YamlScalar.location(): String = path.location()

internal fun YamlPath.location(): String = "${toHumanReadableString()} (Zeile ${endLocation.line}, Spalte ${endLocation.column})"
