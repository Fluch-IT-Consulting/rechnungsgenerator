package de.fluchitconsulting.rechnung.values

import com.charleskorn.kaml.Yaml
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun decode(text: String): InvoiceType = Yaml.default.decodeFromString(InvoiceType.serializer(), text)

class InvoiceTypeTest : FunSpec({

    test("jede Art trägt ihren Code aus UNTDID 1001") {
        assertSoftly {
            InvoiceType.INVOICE.code shouldBe "380"
            InvoiceType.PREPAYMENT.code shouldBe "386"
            InvoiceType.FINAL.code shouldBe "380"
            InvoiceType.PARTIAL.code shouldBe "326"
        }
    }

    test("die Schlussrechnung teilt den Code der gewöhnlichen Rechnung") {
        // Kein eigener Code in der Liste: Die Schlussrechnung ist die Rechnung
        // über die Gesamtleistung, kenntlich an der Absetzung, nicht am Typ.
        InvoiceType.FINAL.code shouldBe InvoiceType.INVOICE.code
    }

    test("die YAML-Schreibweisen werden gelesen") {
        assertSoftly {
            decode("rechnung") shouldBe InvoiceType.INVOICE
            decode("abschlagsrechnung") shouldBe InvoiceType.PREPAYMENT
            decode("schlussrechnung") shouldBe InvoiceType.FINAL
            decode("teilrechnung") shouldBe InvoiceType.PARTIAL
        }
    }

    test("Groß- und Kleinschreibung sowie Leerzeichen stören nicht") {
        decode("\" Abschlagsrechnung \"") shouldBe InvoiceType.PREPAYMENT
    }

    test("eine unbekannte Art nennt die zulässigen") {
        val fehler = shouldThrow<Exception> { decode("anzahlung") }

        // kaml verpackt die SerializationException; die Meldung muss durchkommen.
        val meldung = generateSequence<Throwable>(fehler) { it.cause }.joinToString(" ") { it.message.orEmpty() }
        assertSoftly {
            meldung shouldContain "anzahlung"
            meldung shouldContain "abschlagsrechnung"
            meldung shouldContain "schlussrechnung"
        }
    }
})
