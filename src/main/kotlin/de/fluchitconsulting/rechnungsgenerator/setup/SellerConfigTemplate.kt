package de.fluchitconsulting.rechnungsgenerator.setup

/**
 * Die Vorlage der Stammdaten, die `einrichten` anlegt.
 *
 * Sie liegt als Ressource neben den Klassen und wird mit `checkNotNull`
 * gelesen, wie `Template.of` die Vorlagen der Designs: Fehlt sie, ist das
 * Programm falsch gebaut und nicht falsch bedient.
 *
 * Zwei Arten von `#` stehen darin, und der Unterschied ist Teil der Vorlage:
 * `# ` mit Leerzeichen leitet erklärenden Text ein, `#` direkt vor einem
 * Schlüssel (`#telefon:`) einen freiwilligen Eintrag. Wer ihn will, nimmt das
 * `#` weg – und ein Test nimmt sie alle weg und hält die Vorlage damit gegen
 * `SellerConfig`: Ein neues Feld ohne Eintrag wird rot, ein Eintrag ohne Feld
 * ebenso. Ohne dieses Netz wäre die Vorlage eine zweite Abschrift des Schemas,
 * die still veraltet.
 *
 * Pflichtfelder stehen leer da und nicht mit Beispielwerten gefüllt: Eine
 * vergessene Zeile ergäbe sonst ein gültiges PDF mit der Bankverbindung einer
 * erfundenen Firma. Leer scheitern sie laut, mit Datei und Schlüssel.
 */
object SellerConfigTemplate {
    private const val PATH = "/stammdaten-vorlage.yaml"

    /** Der Text der Vorlage, so wie er in die Datei geht. */
    val text: String =
        checkNotNull(SellerConfigTemplate::class.java.getResource(PATH)) {
            "$PATH fehlt – das Programm wurde ohne die Vorlage der Stammdaten gebaut"
        }.readText()
}
