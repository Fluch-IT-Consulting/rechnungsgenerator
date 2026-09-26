package de.fluchitconsulting.rechnungsgenerator.toolchain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Ein Profil geht auf zwei Wegen an veraPDF: als [PdfAProfile.flavour] an die
 * Bibliothek und als [PdfAProfile.cliFlag] an das Programm. Nennten die beiden
 * Verschiedenes, urteilten die Prüfer über verschiedene Normen. Der Vergleich
 * der beiden Prüfer am echten PDF bemerkte das nur bei 1b und 3u.
 */
class PdfAProfileTest : FunSpec({

    test("Bibliothek und Programm bekommen je Profil dasselbe") {
        PdfAProfile.entries.forEach { profile ->
            profile.flavour.id shouldBe profile.cliFlag
        }
    }

    test("jedes Profil nennt sich, wie die Norm es schreibt") {
        PdfAProfile.entries.map { it.label } shouldBe listOf("PDF/A-1b", "PDF/A-3a", "PDF/A-3b", "PDF/A-3u")
    }
})
