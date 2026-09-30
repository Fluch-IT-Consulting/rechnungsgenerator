package de.fluchitconsulting.rechnungsgenerator.generate.typesetting

import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import de.fluchitconsulting.rechnungsgenerator.toolchain.validation.BuiltInPdfAValidator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldNotBeInstanceOf
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

/** Ohne Tag `Toolchain`: Mustang prüft das XML in derselben JVM, vor LuaLaTeX. */
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

        // Kein Satzfehler, sonst nennte erzeuge das Logo (#491).
        error.shouldNotBeInstanceOf<TypesettingException>()
        error.message!! shouldContain "Fehler des Generators, nicht der Rechnung"
        error.message!! shouldContain "[BR-09]"
        // LatexCompiler schriebe als Erstes die .tex hierher.
        workDir.listDirectoryEntries().shouldBeEmpty()
    }
})
