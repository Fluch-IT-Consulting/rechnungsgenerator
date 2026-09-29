package de.fluchitconsulting.rechnungsgenerator.generate.render

/**
 * Die Makros der Vorlage, die der Renderer aufruft, mit ihrer Stelligkeit; geprüft beim Bau der Vorlage
 * (ADR-0017). Ein anderes ruft er nicht auf, auch keines von LaTeX selbst. Die Vorlage darf mehr definieren.
 */
enum class TemplateMacro(val macroName: String, val arity: Int) {
    POSITIONSTRENNER("positionstrenner", 0),
    SUMMENTRENNER("summentrenner", 0),
    ZEILENWECHSEL("zeilenwechsel", 0),
    ZUSAMMEN("zusammen", 1),

    // Ob das Land dasteht, entscheidet der Renderer, die Versalien nach DIN 5008 setzt die Vorlage (#374).
    LANDZEILE("landzeile", 1),

    // Nur für Sätze aus dem Quelltext: Hier setzt die Vorlage den breiten Satzabstand, den in Eingabedaten
    // auch „Dr.“ bekäme (#143).
    HINWEISABSATZ("hinweisabsatz", 1),

    // Nicht HINWEISABSATZ, weil der Befreiungsgrund aus den Eingabedaten kommt.
    STEUERHINWEIS("steuerhinweis", 1),
    ABSETZUNGSKOPF("absetzungskopf", 1),
    ABSETZUNGSTABELLE("absetzungstabelle", 1),
    ABSETZUNGSDETAIL("absetzungsdetail", 1),
    POSITIONSDETAIL("positionsdetail", 1),

    // Nummer, Beschreibung, Menge, Einheit, Einzelpreis, Rabatt (leer ohne), Betrag.
    POSITIONSZEILE("positionszeile", 7),
    SUMMENZEILE("summenzeile", 2),
    ABSCHLUSSZEILE("abschlusszeile", 2),

    // Ohne Beschriftung, die setzt die Vorlage (#217). Die Grundlage der Steuerzeile ist bei nur einer Gruppe
    // leer (#218). Die Gesamtzeile beendet wie ABSCHLUSSZEILE die Zeile davor selbst.
    NETTOZEILE("nettozeile", 1),
    STEUERZEILE("steuerzeile", 3),
    GESAMTZEILE("gesamtzeile", 1),

    // Ohne Argument: Den Inhalt setzt die Vorlage, der Renderer entscheidet nur, ob der Block dasteht.
    BANKBLOCK("bankblock", 0),

    // Auch für Einzelpreise, den Unterschied tragen die Ziffern (ADR-0021).
    BETRAG("betrag", 1),

    // Ein eigenes Makro statt der Währung als Argument, die sonst an jedem Betrag stünde (ADR-0026).
    EUROBETRAG("eurobetrag", 1),
    MENGE("menge", 1),
    PROZENT("prozent", 1),
}
