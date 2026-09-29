package de.fluchitconsulting.rechnungsgenerator.generate.render

/**
 * Die Grundlage jeder Vorlage (ADR-0022), als Datei neben der `.tex`: eingefügt, stünde sie in jeder
 * Vergleichsdatei (#204). Kein Makro aus [TemplateMacro] – die definiert die Vorlage, damit ihr Volltext jede
 * Definition enthält, die der Renderer aufruft.
 */
class Foundation private constructor(
    val content: ByteArray,
) {
    companion object {
        const val FILE_NAME = "grundlage.sty"

        const val USAGE = "\\usepackage{grundlage}"

        private val DEFINITION = Regex("""\\(?:(?:new|renew|provide)command\*?\{\\([a-zA-Z]+)}|def\\([a-zA-Z]+))""")

        val BUILT_IN: Foundation by lazy {
            val path = "/templates/$FILE_NAME"
            of(checkNotNull(Foundation::class.java.getResource(path)) { "Grundlage $path fehlt" }.readText())
        }

        /** Geprüft wird, was TeX liest, also ohne Kommentarzeilen. */
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
