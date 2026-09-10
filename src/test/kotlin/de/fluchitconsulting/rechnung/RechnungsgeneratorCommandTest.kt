package de.fluchitconsulting.rechnung

import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.parse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Das Elternkommando tut selbst nichts, außer an seine Unterkommandos zu
 * verteilen. Zu prüfen ist deshalb nur, wie es sich verhält, wenn es nichts zu
 * verteilen gibt.
 */
class RechnungsgeneratorCommandTest : FunSpec({

    test("ohne Unterkommando zeigt es die Hilfe und endet nicht mit Exitcode 0") {
        // Ein Skript, das den Aufruf vergisst, soll das am Exitcode merken –
        // nicht erst daran, dass kein PDF entstanden ist.
        val failure = shouldThrow<PrintHelpMessage> { RechnungsgeneratorCommand().parse(emptyArray()) }

        failure.statusCode shouldBe 1
    }

    test("--help bleibt ein Erfolg") {
        // Das Gegenstück: Wer die Hilfe verlangt, hat keinen Fehler gemacht.
        val help = shouldThrow<PrintHelpMessage> { RechnungsgeneratorCommand().parse(arrayOf("--help")) }

        help.statusCode shouldBe 0
    }
})
