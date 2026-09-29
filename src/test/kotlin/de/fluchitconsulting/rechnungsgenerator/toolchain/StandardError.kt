package de.fluchitconsulting.rechnungsgenerator.toolchain

import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Verlässlich nur für Schreiber, die `System.err` beim Schreiben nachschlagen
 * und nicht beim Laden einer Klasse, wie der `DefaultErrorHandler` des JDK (#395).
 */
fun <T> capturingStandardError(block: () -> T): Pair<T, String> {
    val original = System.err
    val captured = ByteArrayOutputStream()
    System.setErr(PrintStream(captured, true, Charsets.UTF_8))
    val result =
        try {
            block()
        } finally {
            System.setErr(original)
        }
    return result to captured.toString(Charsets.UTF_8)
}
