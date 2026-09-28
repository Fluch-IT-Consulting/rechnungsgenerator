package de.fluchitconsulting.rechnungsgenerator.core.values

import com.charleskorn.kaml.Yaml
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.SerializationException

class ExchangeRateTest : FunSpec({

    context("toEuro") {
        test("teilt durch den Kurs und rundet kaufmännisch auf Cent") {
            assertSoftly {
                // 1584.13 / 1.1685 = 1355.6953… -> 1355.70
                ExchangeRate("1.1685").toEuro(Money("1584.13")) shouldBe Money("1355.70")
                // 100 / 0.8 geht auf
                ExchangeRate("0.8").toEuro(Money("100.00")) shouldBe Money("125.00")
                // 1.00 / 162.45 = 0.006155… -> 0.01
                ExchangeRate("162.45").toEuro(Money("1.00")) shouldBe Money("0.01")
            }
        }

        test("rundet genau einmal") {
            // 1.00 / 40.0064 = 0.024996… -> 0.02. Über ein Zwischenergebnis
            // mit vier Stellen, 0.0250, würde daraus 0.03.
            ExchangeRate("40.0064").toEuro(Money("1.00")) shouldBe Money("0.02")
        }

        test("rundet einen negativen Betrag spiegelbildlich") {
            ExchangeRate("1.1685").toEuro(Money("-1584.13")) shouldBe Money("-1355.70")
        }
    }

    test("ein Kurs liegt über null") {
        listOf("0", "0.0000", "-1.1685").forEach { raw ->
            withClue(raw) {
                shouldThrow<InvalidExchangeRateException> { ExchangeRate(raw) }.message!! shouldContain "über null"
            }
        }
    }

    test("Wertgleichheit ist unabhängig von der Zahl der Nullen") {
        ExchangeRate("1.1685") shouldBe ExchangeRate("1.16850")
        ExchangeRate("1.1685").hashCode() shouldBe ExchangeRate("1.16850").hashCode()
    }

    context("Serialisierung (YAML)") {
        test("liest den Kurs ohne ihn zu runden") {
            Yaml.default
                .decodeFromString(ExchangeRateSerializer, "1.16853")
                .value
                .toPlainString() shouldBe "1.16853"
        }

        test("ein Kurs von null bricht das Lesen mit einer Meldung ab") {
            shouldThrow<SerializationException> {
                Yaml.default.decodeFromString(ExchangeRateSerializer, "0")
            }.message!! shouldContain "Kein gültiger Kurs: '0'"
        }

        test("keine Zahl bricht das Lesen mit einer Meldung ab") {
            shouldThrow<SerializationException> {
                Yaml.default.decodeFromString(ExchangeRateSerializer, "1,1685")
            }.message!! shouldContain "Kein gültiger Kurs: '1,1685'"
        }
    }
})
