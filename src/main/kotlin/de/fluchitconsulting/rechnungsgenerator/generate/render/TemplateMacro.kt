package de.fluchitconsulting.rechnungsgenerator.generate.render

/**
 * Die Makros, die der Renderer aus der Vorlage aufruft – mit ihrer Stelligkeit.
 *
 * Zugegeben eine zweite Liste neben der Vorlage, aber nur der Namen und nicht
 * des Satzes: Wie eine Position aussieht, steht weiterhin ausschließlich in
 * den Vorlagen. Der Zweck ist die Prüfung. Vorher fiel ein vertippter
 * Makroname erst im LuaLaTeX-Lauf auf, also im Integrationstest; jetzt beim
 * Setzen der Vorlage, mit einer Meldung, die den Namen nennt – dasselbe, was
 * #30 für die Platzhalter eingeführt hat.
 *
 * Der Renderer ruft kein anderes Makro auf, auch keines von LaTeX selbst: Wo er
 * früher `\mbox` oder `\\[\positionsabstand]` schrieb, stehen heute `ZUSAMMEN`
 * und die Trenner (#76), wo er `\\` schrieb, `ZEILENWECHSEL` und das
 * Zeilenende in `ABSCHLUSSZEILE` (#147). Dass keines mehr hineinkommt,
 * prüft [Template.fillIn]. Einzige Ausnahme sind die Felder der
 * pdfx-Metadaten – sie gehören in die .xmpdata-Datei und nicht in diese
 * Vorlage.
 *
 * Die Vorlage darf mehr Makros definieren und nutzen, als hier stehen. Seit
 * #188 setzt sie Briefkopf, Fuß und Kopfangaben selbst aus einzelnen Werten
 * ([TemplateValue]) zusammen; `\kopfperson`, `\blocktrenner` und
 * `\ustidtrenner` ruft seitdem nur noch sie auf.
 */
enum class TemplateMacro(val macroName: String, val arity: Int) {
    POSITIONSTRENNER("positionstrenner", 0),
    SUMMENTRENNER("summentrenner", 0),
    ZEILENWECHSEL("zeilenwechsel", 0),
    ZUSAMMEN("zusammen", 1),

    // Nur für Sätze aus dem Quelltext: Allein hier setzt die Vorlage nach einem
    // Satzende den breiten Abstand, und in Eingabedaten bekäme ihn auch eine
    // Abkürzung wie "Dr." (#143).
    HINWEISABSATZ("hinweisabsatz", 1),
    ABSETZUNGSKOPF("absetzungskopf", 1),
    ABSETZUNGSTABELLE("absetzungstabelle", 1),
    ABSETZUNGSDETAIL("absetzungsdetail", 1),
    POSITIONSDETAIL("positionsdetail", 1),

    // Das erste Argument ist die laufende Nummer der Position, das sechste der
    // Rabatt – leer, wenn die Position keinen hat.
    POSITIONSZEILE("positionszeile", 7),
    SUMMENZEILE("summenzeile", 2),
    ABSCHLUSSZEILE("abschlusszeile", 2),

    // Die Zeilen des Summenblocks, ohne Beschriftung: Die setzt die Vorlage
    // (#217), "Nettobetrag" und "Zwischensumme (netto)" sagen fachlich
    // dasselbe. Die Steuerzeile bekommt den Satz, weil ihn jedes Design an
    // einer anderen Stelle seines Wortlauts nennt, und die Grundlage – leer,
    // wenn es nur eine Steuergruppe gibt (#218). Die Gesamtzeile beendet wie
    // ABSCHLUSSZEILE die Zeile davor selbst.
    NETTOZEILE("nettozeile", 1),
    STEUERZEILE("steuerzeile", 3),
    GESAMTZEILE("gesamtzeile", 1),

    // Ohne Argument: Den Inhalt setzt die Vorlage aus den Werten der Bank, der
    // Renderer entscheidet nur, ob der Block dasteht.
    BANKBLOCK("bankblock", 0),
}
