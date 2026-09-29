package de.fluchitconsulting.rechnungsgenerator.setup

/**
 * `# ` leitet Text ein, `#` direkt vor einem Schlüssel einen freiwilligen Eintrag; ein Test nimmt sie alle weg und
 * hält die Vorlage gegen `SellerConfig`. Pflichtfelder stehen leer da: Beispielwerte ergäben bei einer vergessenen
 * Zeile ein gültiges PDF mit erfundener Bankverbindung.
 */
object SellerConfigTemplate {
    private const val PATH = "/stammdaten-vorlage.yaml"

    val text: String =
        checkNotNull(SellerConfigTemplate::class.java.getResource(PATH)) {
            "$PATH fehlt – das Programm wurde ohne die Vorlage der Stammdaten gebaut"
        }.readText()
}
