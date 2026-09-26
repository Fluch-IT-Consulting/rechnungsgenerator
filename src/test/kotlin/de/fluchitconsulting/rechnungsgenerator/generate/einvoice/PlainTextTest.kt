package de.fluchitconsulting.rechnungsgenerator.generate.einvoice

import de.fluchitconsulting.rechnungsgenerator.core.text.TextTemplate
import de.fluchitconsulting.rechnungsgenerator.core.text.TextValue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

private enum class Variable { NUMMER, DATUM }

class PlainTextTest : FunSpec({
    val resolver = { variable: Variable ->
        when (variable) {
            Variable.NUMMER -> TextValue.Text("R26-042 & Co.")
            Variable.DATUM -> TextValue.Date(LocalDate.of(2026, 8, 4))
        }
    }

    // Maskiert wird erst im XML-Writer; hier stünde sonst `&amp;amp;`.
    test("Text und Werte stehen unverändert da, das Datum ausgeschrieben wie auf dem Blatt") {
        TextTemplate.parse<Variable>("Rechnung <<NUMMER>> vom <<DATUM>> (100 %).").toPlainText(resolver) shouldBe
            "Rechnung R26-042 & Co. vom 4. August 2026 (100 %)."
    }
})
