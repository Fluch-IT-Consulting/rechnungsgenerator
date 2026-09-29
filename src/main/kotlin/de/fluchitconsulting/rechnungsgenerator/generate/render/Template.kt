package de.fluchitconsulting.rechnungsgenerator.generate.render

import de.fluchitconsulting.rechnungsgenerator.core.model.Design
import de.fluchitconsulting.rechnungsgenerator.core.values.Currency
import de.fluchitconsulting.rechnungsgenerator.core.values.Language

internal val PLACEHOLDER = Regex("<<([A-Z_]+)>>")

/**
 * Eine geprüfte Vorlage samt Sprachdatei (ADR-0017, ADR-0025). Gebaut wird nur über [of]; jede Prüfung ist
 * ein [check], denn keine Rechnungsdatei kann sie auslösen – laufen Vorlage und Renderer auseinander, ist das
 * ein Fehler im Programm.
 */
class Template private constructor(
    private val source: String,
    private val languageFile: String,
) {
    fun uses(value: TemplateValue): Boolean = value.placeholder in source

    val files: Map<String, ByteArray>
        get() =
            mapOf(
                Foundation.FILE_NAME to Foundation.BUILT_IN.content,
                LANGUAGE_FILE_NAME to languageFile.toByteArray(),
            )

    /**
     * Setzt die Werte in einem Durchgang ein: Ein Wert mit `<<…>>` darin ist Inhalt, nacheinander ersetzt fände
     * der nächste Schritt ihn wieder. Die Lambda-Variante von `Regex.replace` setzt wörtlich ein, `\` und `$1`
     * sind dort keine Rückverweise.
     *
     * Ein Platzhalter ohne Wert stünde sonst gesetzt in einem gültigen PDF/A (#30). Nur diese Richtung: Einen
     * Wert zu übergehen, ist einem Design erlaubt (#188).
     */
    fun fillIn(values: Map<TemplateValue, Latex>): String {
        checkLineBreaks(values)

        val byName = values.mapKeys { (key, _) -> key.name }

        // Geprüft wird die Vorlage, nicht das Ergebnis: Dort kann ein Platzhalter als Inhalt stehen.
        PLACEHOLDER.findAll(source).firstOrNull { it.groupValues[1] !in byName }?.let {
            error("Die Vorlage enthält ${it.value}, aber der Renderer liefert dafür keinen Wert.")
        }

        return PLACEHOLDER.replace(source) { byName.getValue(it.groupValues[1]).tex }
    }

    /**
     * Zeilenwechsel stehen als Makro in der Vorlage (ADR-0017). Geprüft wird das Ergebnis, damit kein Weg an der
     * Regel vorbeiführt: Eine Zeile, die mit `[` beginnt, bräche nach einem rohen `\\` den Satz ab.
     */
    private fun checkLineBreaks(values: Map<TemplateValue, Latex>) {
        values.forEach { (key, value) ->
            check("\\\\" !in value.tex) {
                "Der Renderer liefert für ${key.placeholder} ein rohes \\\\. Zeilenwechsel stehen in der Vorlage, " +
                    "der Renderer ruft sie als Makro aus TemplateMacro auf."
            }
        }
    }

    companion object {
        const val LANGUAGE_FILE_NAME = "sprache.tex"

        const val LANGUAGE_FILE_USAGE = "\\input{sprache}"

        private val NEWCOMMAND = Regex("""\\newcommand\{\\([a-zA-Z]+)}(?:\[(\d+)])?""")

        fun of(design: Design, language: Language): Template =
            of(
                resource("/templates/${design.label}.tex", "Vorlage"),
                resource("/templates/${languageFileOf(design, language)}", "Sprachdatei"),
            )

        fun languageFileOf(design: Design, language: Language): String = "${design.label}-${language.tag.lowercase()}.tex"

        private fun resource(path: String, what: String): String =
            checkNotNull(Template::class.java.getResource(path)) { "$what $path fehlt" }.readText()

        /** Erst die Kommentarzeilen streichen, dann prüfen: Geprüft wird nur, was TeX liest. */
        fun of(source: String, languageFile: String): Template {
            val withoutComments = withoutCommentLines(source)
            val languageWithoutComments = withoutCommentLines(languageFile)
            checkFoundation(withoutComments)
            checkLanguageFile(withoutComments, languageWithoutComments)
            checkMacros(withoutComments + "\n" + languageWithoutComments)
            checkPlaceholders(withoutComments)
            return Template(withoutComments, languageFile)
        }

        // Ein fehlendes Währungszeichen setzte still nichts: `\csname` macht aus einem unbekannten Namen `\relax` (#409).
        private fun checkLanguageFile(source: String, languageFile: String) {
            check(source.lines().any { it.trim() == LANGUAGE_FILE_USAGE }) {
                "Die Vorlage lädt ihre Sprachdatei nicht. Sie braucht eine Zeile $LANGUAGE_FILE_USAGE."
            }
            PLACEHOLDER.find(languageFile)?.let {
                error("Die Sprachdatei enthält ${it.value}. Werte der Rechnung setzt die Vorlage, die Sprachdatei nur Wörter.")
            }
            val defined = NEWCOMMAND.findAll(languageFile).map { it.groupValues[1] }.toSet()
            Currency.entries.forEach { currency ->
                check(currencySymbolOf(currency) in defined) {
                    "Die Sprachdatei kennt kein Zeichen für $currency. Sie braucht \\newcommand{\\${currencySymbolOf(currency)}}."
                }
            }
        }

        private fun currencySymbolOf(currency: Currency): String = "zeichen${currency.name}"

        // Ohne Grundlage übersetzte eine Vorlage weiter, nur das Rundungsverbot von siunitx fehlte still (ADR-0022).
        private fun checkFoundation(source: String) {
            val afterClass =
                source
                    .lines()
                    .dropWhile { !it.startsWith("\\documentclass") }
                    .drop(1)
                    .firstOrNull { it.isNotBlank() }
            check(afterClass?.trim() == Foundation.USAGE) {
                "Die Vorlage lädt die Grundlage nicht direkt nach \\documentclass. Die erste Zeile danach muss " +
                    "${Foundation.USAGE} sein, denn die Grundlage lädt pdfx."
            }
        }

        private fun checkMacros(source: String) {
            val defined =
                NEWCOMMAND
                    .findAll(source)
                    .associate { it.groupValues[1] to (it.groupValues[2].toIntOrNull() ?: 0) }

            TemplateMacro.entries.forEach { macro ->
                val arity =
                    checkNotNull(defined[macro.macroName]) {
                        "Der Renderer ruft \\${macro.macroName} auf, aber die Vorlage definiert es nicht."
                    }
                check(arity == macro.arity) {
                    "\\${macro.macroName} nimmt in der Vorlage $arity Argumente, im Renderer ${macro.arity}."
                }
            }
        }

        private fun checkPlaceholders(source: String) {
            TemplateValue.entries.filter { it.required }.forEach { key ->
                check(key.placeholder in source) {
                    "Die Vorlage setzt ${key.placeholder} nicht, und das darf keine Vorlage weglassen."
                }
            }
            check(TemplateValue.ZAHLUNGSANGABE.any { it.placeholder in source }) {
                "Die Vorlage sagt nichts über die Zahlung: Sie setzt keinen von " +
                    TemplateValue.ZAHLUNGSANGABE.joinToString(", ") { it.placeholder } +
                    ", und einen davon muss jede Vorlage setzen."
            }
        }
    }
}

/**
 * Streicht die Zeilen, die mit `%` beginnen: Sonst machte jede geänderte Kommentarzeile die Vergleichsdateien
 * rot. Nachgestellte `%` bleiben – in einer Makrodefinition unterdrücken sie den Zwischenraum, und `\%` ist gar
 * keiner.
 */
internal fun withoutCommentLines(source: String): String =
    source
        .lines()
        .filterNot { it.trimStart().startsWith("%") }
        .joinToString("\n")
