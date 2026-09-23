package de.fluchitconsulting.rechnungsgenerator.core.values

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Das Muster aus den Stammdaten (#54, ADR-0005). Geprüft wird hier nur, was
 * dieser Wert für sich weiß: ob der Ausdruck einer ist und wie er trifft.
 * Ob eine bestimmte Rechnung dazu passt, entscheidet `InvoiceNumberRules` –
 * das hängt an zwei Dateien und steht in `InvoiceNumberRulesTest`.
 */
class InvoiceNumberPatternTest : FunSpec({

    test("das Muster trifft die ganze Nummer, nicht bloß eine Stelle darin") {
        val muster = InvoiceNumberPattern("R\\d{2}-\\d{3}")

        muster.matches("R26-001") shouldBe true
        muster.matches("XR26-001") shouldBe false
        muster.matches("R26-001-storno") shouldBe false
    }

    test("Anker sind erlaubt und ändern nichts") {
        InvoiceNumberPattern("^R\\d{2}-\\d{3}$").matches("R26-001") shouldBe true
    }

    test("mehrere Nummernkreise stehen als Alternative im Ausdruck") {
        // UStAE 14.5 Abs. 11 erlaubt sie; eine eigene Mechanik dafür braucht es
        // nicht, ein regulärer Ausdruck trägt seine Alternativen selbst.
        val muster = InvoiceNumberPattern("(R|G)\\d{2}-\\d{3}")

        muster.matches("R26-001") shouldBe true
        muster.matches("G26-001") shouldBe true
        muster.matches("X26-001") shouldBe false
    }

    test("ein Ausdruck, der keiner ist, wird beim Bauen abgelehnt") {
        // Der Tippfehler steckt in der Wiederholung: {2-3} statt {2,3}.
        val fehler = shouldThrow<InvalidIdentifierException> { InvoiceNumberPattern("R\\d{2-3}") }

        fehler.message!! shouldContain "R\\d{2-3}"
        // Die Meldung der PatternSyntaxException wird übernommen, nicht ersetzt:
        // Sie zeigt mit einem ^ auf die schadhafte Stelle im Ausdruck.
        fehler.message!! shouldContain "^"
    }

    test("zwei gleiche Ausdrücke sind derselbe Wert") {
        // Regex selbst hat kein equals – ohne eigenes wären zwei Stammdaten mit
        // demselben Muster verschieden, und data class copy() löge.
        InvoiceNumberPattern("R\\d{2}") shouldBe InvoiceNumberPattern("R\\d{2}")
        InvoiceNumberPattern("R\\d{2}").hashCode() shouldBe InvoiceNumberPattern("R\\d{2}").hashCode()
    }
})
