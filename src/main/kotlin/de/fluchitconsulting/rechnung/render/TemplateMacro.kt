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
 * Nicht hier stehen die Makros von LaTeX selbst (`textbf`, `mbox`) und die von
 * pdfx für die Metadaten: Die kommen nicht aus dieser Vorlage, und die Prüfung
 * könnte sie dort nicht finden.
 */
enum class TemplateMacro(val macroName: String, val arity: Int) {
    RUBRIK("rubrik", 1),
    HINWEISABSATZ("hinweisabsatz", 1),
    ABSETZUNGSKOPF("absetzungskopf", 1),
    ABSETZUNGSTABELLE("absetzungstabelle", 1),
    ABSETZUNGSDETAIL("absetzungsdetail", 1),
    POSITIONSDETAIL("positionsdetail", 1),
    POSITIONSZEILE("positionszeile", 5),
    SUMMENZEILE("summenzeile", 2),
    ABSCHLUSSZEILE("abschlusszeile", 2),
}
