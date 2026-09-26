package de.fluchitconsulting.rechnungsgenerator.toolchain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Die Nahtstelle in [ToolResult.output]: Wo stdout aufhört und stderr anfängt,
 * darf keine gemeinsame Zeile entstehen (#246).
 *
 * Eigene Datei und kein Zweig in [ExternalToolTest]: Dort stehen die Wege, die
 * sich nur an einem echten Prozess zeigen. Hier läuft keiner – geprüft wird ein
 * Wert, den auch der [FakeProcessRunner] so zusammensetzt.
 */
class ToolResultTest : FunSpec({

    fun output(standardOutput: String, errorOutput: String) = ToolResult(0, standardOutput, errorOutput).output

    // Der Fall aus #246: Endet stdout nicht auf einem Umbruch, stünde sonst in
    // der Fehlermeldung "letzte Zeileerste Zeile" – eine Zeile aus zwei Quellen.
    test("ohne Umbruch am Ende von stdout kommt einer dazu") {
        output("letzte Zeile", "erste Zeile") shouldBe "letzte Zeile\nerste Zeile"
    }

    test("mit Umbruch am Ende von stdout kommt keiner dazu") {
        output("letzte Zeile\n", "erste Zeile") shouldBe "letzte Zeile\nerste Zeile"
    }

    // Leere Kanäle gehen unverändert durch: Ohne zwei Quellen gibt es keine
    // Nahtstelle, und ein Umbruch wäre dann eine Zeile, die niemand geschrieben
    // hat – in einer Meldung sieht das nach abgeschnittener Ausgabe aus.
    test("leeres stderr lässt stdout, wie es ist") {
        output("nur stdout", "") shouldBe "nur stdout"
    }

    test("leeres stdout lässt stderr, wie es ist") {
        output("", "nur stderr") shouldBe "nur stderr"
    }

    test("zwei leere Kanäle ergeben nichts") {
        output("", "") shouldBe ""
    }
})
