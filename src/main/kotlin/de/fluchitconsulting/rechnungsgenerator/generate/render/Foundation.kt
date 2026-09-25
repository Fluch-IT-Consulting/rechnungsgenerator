package de.fluchitconsulting.rechnungsgenerator.generate.render

/**
 * Die Grundlage jeder Vorlage, `templates/grundlage.sty` (ADR-0022): was ein
 * Design nicht anders setzen darf, und nichts sonst.
 *
 * Eine Datei neben der `.tex` und kein Teil von ihr. In den Quelltext
 * eingefügt, stünde sie in jeder Volltext-Vergleichsdatei wieder, und jede
 * Änderung an ihr träfe alle zugleich (#204).
 *
 * Gebaut wird sie wie die [Template] über [of], und dort gelten ihre zwei
 * Regeln: kein Platzhalter, denn sie ist für jede Rechnung dieselbe Datei, und
 * kein Makro aus [TemplateMacro], denn die definiert die Vorlage selbst – sonst
 * fehlte im Volltext einer Vorlage eine Definition, die der Renderer aufruft.
 */
class Foundation private constructor(
    val content: ByteArray,
) {
    companion object {
        /** Der Name, unter dem sie neben der `.tex` liegt. */
        const val FILE_NAME = "grundlage.sty"

        /** Die Zeile, mit der eine Vorlage sie lädt. */
        const val USAGE = "\\usepackage{grundlage}"

        // Eine Makro-Definition: \newcommand, \renewcommand oder \providecommand,
        // auch mit Stern, oder \def.
        private val DEFINITION = Regex("""\\(?:(?:new|renew|provide)command\*?\{\\([a-zA-Z]+)}|def\\([a-zA-Z]+))""")

        /** Die Grundlage, die das Programm mitbringt. */
        val BUILT_IN: Foundation by lazy {
            val path = "/templates/$FILE_NAME"
            of(checkNotNull(Foundation::class.java.getResource(path)) { "Grundlage $path fehlt" }.readText())
        }

        /**
         * Baut eine Grundlage aus Quelltext und prüft ihre zwei Regeln – an
         * dem, was TeX liest: Ein Platzhalter oder eine Definition in einer
         * Kommentarzeile zählt nicht.
         */
        fun of(source: String): Foundation {
            val read = withoutCommentLines(source)

            PLACEHOLDER.find(read)?.let {
                error(
                    "Die Grundlage enthält ${it.value}. Sie ist für jede Rechnung dieselbe; was an der Rechnung hängt, fragt die Vorlage.",
                )
            }
            val macroNames = TemplateMacro.entries.map { it.macroName }.toSet()
            DEFINITION.findAll(read).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.firstOrNull { it in macroNames }?.let {
                error("Die Grundlage definiert \\$it. Makros, die der Renderer aufruft, definiert die Vorlage selbst.")
            }
            return Foundation(source.toByteArray())
        }
    }
}
