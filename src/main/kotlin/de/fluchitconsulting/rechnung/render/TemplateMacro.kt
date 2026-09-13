package de.fluchitconsulting.rechnung.render

/**
 * Die Makros, die der Renderer aus der Vorlage aufruft – mit ihrer Stelligkeit.
 *
 * Zugegeben eine zweite Liste neben der Vorlage, aber nur der Namen und nicht
 * des Satzes: Wie eine Position aussieht, steht weiterhin ausschließlich in
 * `invoice.tex`. Der Zweck ist die Prüfung. Vorher fiel ein vertippter
 * Makroname erst im LuaLaTeX-Lauf auf, also im Integrationstest; jetzt beim
 * Setzen der Vorlage, mit einer Meldung, die den Namen nennt – dasselbe, was
 * #30 für die Platzhalter eingeführt hat.
 *
 * Der Renderer ruft kein anderes Makro auf, auch keines von LaTeX selbst: Wo er
 * früher `\mbox` oder `\\[\positionsabstand]` schrieb, stehen heute `ZUSAMMEN`
 * und die Trenner (#76). Einzige Ausnahme sind die Felder der pdfx-Metadaten –
 * sie gehören in die .xmpdata-Datei und nicht in diese Vorlage.
 */
enum class TemplateMacro(val macroName: String, val arity: Int) {
    POSITIONSTRENNER("positionstrenner", 0),
    SUMMENTRENNER("summentrenner", 0),
    BLOCKTRENNER("blocktrenner", 0),
    USTIDTRENNER("ustidtrenner", 0),
    ZUSAMMEN("zusammen", 1),
    HINWEISABSATZ("hinweisabsatz", 1),
    ABSETZUNGSKOPF("absetzungskopf", 1),
    ABSETZUNGSTABELLE("absetzungstabelle", 1),
    ABSETZUNGSDETAIL("absetzungsdetail", 1),
    POSITIONSDETAIL("positionsdetail", 1),
    POSITIONSZEILE("positionszeile", 5),
    SUMMENZEILE("summenzeile", 2),
    ABSCHLUSSZEILE("abschlusszeile", 2),
    BANKBLOCK("bankblock", 1),
}
