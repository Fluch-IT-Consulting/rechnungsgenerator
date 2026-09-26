package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import de.fluchitconsulting.rechnungsgenerator.toolchain.BuiltInPdfAValidator
import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

/**
 * Was der Adapter des Betriebs selbst entscheidet, ohne dass ein Werkzeug
 * läuft. Deshalb ohne den Tag `Toolchain`: Das XML prüft Mustang in derselben
 * JVM, und zwar vor LuaLaTeX.
 */
class LuaLatexTypesetterTest : FunSpec({

    test("ein ungültiges XML bricht vor dem Satz ab, als Fehler des Generators und nicht der Rechnung") {
        val xml =
            Path
                .of("src/test/resources/rechnung-erwartet.xml")
                .readText()
                .replaceFirst("<ram:CountryID>DE</ram:CountryID>", "")
        val workDir = createTempDirectory("rechnungsgenerator-test")
        val sources = PdfSources(tex = "", xmpMetadata = "", creationDate = LocalDate.of(2026, 8, 14), einvoice = xml.toByteArray())

        val error =
            shouldThrow<ToolchainException> {
                LuaLatexTypesetter(BuiltInPdfAValidator).typeset(workDir, "rechnung", sources)
            }

        error.message!! shouldContain "Fehler des Generators, nicht der Rechnung"
        error.message!! shouldContain "[BR-09]"
        // LatexCompiler schriebe als Erstes die .tex hierher.
        workDir.listDirectoryEntries().shouldBeEmpty()
    }
})
