package de.fluchitconsulting.rechnungsgenerator.core.values

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** #54, ADR-0005 */
class InvoiceNumberPatternTest : FunSpec({

    test("das Muster trifft die ganze Nummer, nicht bloß eine Stelle darin") {
        val pattern = InvoiceNumberPattern("R\\d{2}-\\d{3}")

        pattern.matches("R26-001") shouldBe true
        pattern.matches("XR26-001") shouldBe false
        pattern.matches("R26-001-storno") shouldBe false
    }

    test("Anker sind erlaubt und ändern nichts") {
        InvoiceNumberPattern("^R\\d{2}-\\d{3}$").matches("R26-001") shouldBe true
    }

    test("mehrere Nummernkreise stehen als Alternative im Ausdruck") {
        // UStAE 14.5 Abs. 11
        val pattern = InvoiceNumberPattern("(R|G)\\d{2}-\\d{3}")

        pattern.matches("R26-001") shouldBe true
        pattern.matches("G26-001") shouldBe true
        pattern.matches("X26-001") shouldBe false
    }

    test("ein Ausdruck, der keiner ist, wird beim Bauen abgelehnt") {
        val error = shouldThrow<InvalidIdentifierException> { InvoiceNumberPattern("R\\d{2-3}") }

        error.message!! shouldContain "R\\d{2-3}"
        // Mit dem ^ der PatternSyntaxException, das auf die Stelle zeigt.
        error.message!! shouldContain "^"
    }

    test("zwei gleiche Ausdrücke sind derselbe Wert") {
        // Regex selbst hat kein equals.
        InvoiceNumberPattern("R\\d{2}") shouldBe InvoiceNumberPattern("R\\d{2}")
        InvoiceNumberPattern("R\\d{2}").hashCode() shouldBe InvoiceNumberPattern("R\\d{2}").hashCode()
    }
})
