package de.fluchitconsulting.rechnungsgenerator.toolchain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Der Vergleich der beiden Prüfer am echten PDF bemerkte einen Unterschied nur bei 1b und 3u. */
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
