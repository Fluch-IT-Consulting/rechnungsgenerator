package de.fluchitconsulting.rechnung.render

/**
 * Maskiert Text, der aus den YAML-Dateien stammt, für den LaTeX-Satz.
 *
 * Ohne das würde ein Kundenname wie "Meier & Söhne" den Lauf abbrechen (`&` ist
 * in LaTeX der Spaltentrenner) und "50 % Anzahlung" den Rest der Zeile
 * verschlucken (`%` leitet einen Kommentar ein). Jeder Wert, der ins Template
 * eingesetzt wird, geht durch diese Funktion.
 */
fun String.escapeLatex(): String =
    buildString(length) {
        for (char in this@escapeLatex) {
            when (char) {
                // Der Backslash muss zuerst behandelt werden – er ist das
                // Fluchtzeichen selbst und darf nicht doppelt maskiert werden.
                '\\' -> append("\\textbackslash{}")
                '&', '%', '$', '#', '_', '{', '}' -> append('\\').append(char)
                // Diese beiden sind in LaTeX Akzent-Befehle, keine Zeichen.
                '~' -> append("\\textasciitilde{}")
                '^' -> append("\\textasciicircum{}")
                else -> append(char)
            }
        }
    }
