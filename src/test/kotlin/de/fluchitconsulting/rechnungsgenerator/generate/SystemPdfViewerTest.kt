package de.fluchitconsulting.rechnungsgenerator.generate

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.nio.file.Path

/**
 * Nur die Wahl des Befehls. Den Prozess selbst startet kein Test – sonst ginge
 * bei jedem Build ein Fenster auf.
 */
class SystemPdfViewerTest : FunSpec({

    val pdf = Path.of("/tmp/Rechnung-R26-042.pdf")

    test("auf macOS öffnet open") {
        // So meldet sich macOS in os.name, unabhängig von der Version.
        SystemPdfViewer.commandFor("Mac OS X", pdf) shouldBe listOf("open", "/tmp/Rechnung-R26-042.pdf")
    }

    test("auf Linux öffnet xdg-open") {
        SystemPdfViewer.commandFor("Linux", pdf) shouldBe listOf("xdg-open", "/tmp/Rechnung-R26-042.pdf")
    }

    test("auf anderen Systemen gibt es keinen Befehl") {
        SystemPdfViewer.commandFor("Windows 11", pdf).shouldBeNull()
    }
})
