package de.fluchitconsulting.rechnungsgenerator.core.model

import de.fluchitconsulting.rechnungsgenerator.core.serialization.enumSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/** Gewählt in den Stammdaten (ADR-0011). [label] ist zugleich der Name der Vorlage. */
@Serializable(with = DesignSerializer::class)
enum class Design(
    val label: String,
) {
    HELSINKI("helsinki"),

    PRAG("prag"),
    ;

    override fun toString(): String = label
}

object DesignSerializer :
    KSerializer<Design> by enumSerializer(
        name = "Design",
        values = Design.entries,
        unknown = "Unbekanntes Design",
        normalize = { it.trim().lowercase() },
        keyOf = Design::label,
    )
