package de.fluchitconsulting.rechnungsgenerator.toolchain.process

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** #246 */
class ToolResultTest : FunSpec({

    fun output(standardOutput: String, errorOutput: String) = ToolResult(0, standardOutput, errorOutput).output

    test("ohne Umbruch am Ende von stdout kommt einer dazu") {
        output("letzte Zeile", "erste Zeile") shouldBe "letzte Zeile\nerste Zeile"
    }

    test("mit Umbruch am Ende von stdout kommt keiner dazu") {
        output("letzte Zeile\n", "erste Zeile") shouldBe "letzte Zeile\nerste Zeile"
    }

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
