package de.fluchitconsulting.rechnungsgenerator.core.values

import com.charleskorn.kaml.Yaml
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.serialization.SerializationException
import java.math.BigDecimal

class MoneyTest : FunSpec({

    // Arb<Money>: Beträge -1.000.000,00 .. 1.000.000,00 in Cent-Schritten.
    val money: Arb<Money> = Arb.long(-100_000_000L..100_000_000L).map { Money(BigDecimal.valueOf(it, 2)) }

    context("Konstruktion") {
        test("normalisiert auf 2 Nachkommastellen (HALF_UP)") {
            listOf(
                "1.004" to "1.00",
                "1.005" to "1.01",
                "2.675" to "2.68",
                "-1.005" to "-1.01",
                "5" to "5.00",
            ).forEach { (input, expected) -> Money(input) shouldBe Money(expected) }
        }

        test("String- und BigDecimal-Konstruktor sind äquivalent") {
            checkAll(money) { m -> Money(m.value.toString()) shouldBe m }
        }
    }

    context("Arithmetik bildet eine kommutative Gruppe unter +") {
        test("Kommutativität") { checkAll(money, money) { a, b -> a + b shouldBe b + a } }
        test("Assoziativität") { checkAll(money, money, money) { a, b, c -> (a + b) + c shouldBe a + (b + c) } }
        test("ZERO ist neutrales Element") { checkAll(money) { a -> a + Money.ZERO shouldBe a } }
        // negate() ist mit #32 entfallen – ungerufen. Das inverse Element prüft
        // jetzt a - a, ohne dafür eine Operation vorzuhalten.
        test("a - a == ZERO") { checkAll(money) { a -> a - a shouldBe Money.ZERO } }
    }

    context("Vorzeichen & Prädikate") {
        test("isNegative genau dann, wenn kleiner als ZERO") {
            checkAll(money) { a -> a.isNegative() shouldBe (a < Money.ZERO) }
        }
        test("isZero genau dann, wenn == ZERO") { checkAll(money) { a -> a.isZero() shouldBe (a == Money.ZERO) } }
        test("isPositive genau dann, wenn größer als ZERO") {
            checkAll(money) { a -> a.isPositive() shouldBe (a > Money.ZERO) }
        }
        test("compareTo==0 ist deckungsgleich mit equals") {
            checkAll(money, money) { a, b -> (a.compareTo(b) == 0) shouldBe (a == b) }
        }
    }

    context("Wertgleichheit") {
        test("scale-unabhängig: 2 == 2,00 mit gleichem hashCode") {
            Money("2") shouldBe Money("2.00")
            Money("2").hashCode() shouldBe Money("2.00").hashCode()
        }
        test("nicht gleich zu null oder Fremdtyp") {
            Money("1").equals(null) shouldBe false
            Money("1").equals("1") shouldBe false
        }
    }

    context("Vorzeichenwechsel") {
        test("kehrt das Vorzeichen um") { -Money("19.99") shouldBe Money("-19.99") }
        test("zweimal ist neutral") { checkAll(money) { a -> -(-a) shouldBe a } }
        test("die Null bleibt die Null") { -Money.ZERO shouldBe Money.ZERO }
    }

    context("Betrag ohne Vorzeichen") {
        test("streicht das Minus, lässt das Plus") {
            Money("-19.99").abs() shouldBe Money("19.99")
            Money("19.99").abs() shouldBe Money("19.99")
            Money.ZERO.abs() shouldBe Money.ZERO
        }
        test("ist nie negativ") { checkAll(money) { a -> a.abs().isNegative() shouldBe false } }
        // Die Form, die vor #247 in ItemRules von Hand dastand.
        test("ist der Wert selbst oder sein Gegenteil") {
            checkAll(money) { a -> a.abs() shouldBe if (a.isNegative()) -a else a }
        }
    }

    context("Multiplikation mit Percent") {
        test("Prozentanteil eines Betrags") { Money("100") * Percent("10") shouldBe Money("10.00") }
        test("100 % ist neutral") { checkAll(money) { a -> a * Percent("100") shouldBe a } }
    }

    // Money × Menge gibt es bewusst nicht – das ist Sache von Price, siehe PriceTest.
    // Ein Test dafür kann hier nicht stehen: Er würde schlicht nicht kompilieren.

    context("Summen-Extensions") {
        test("sumOf über eine Liste") {
            listOf("1.10", "2.20", "3.30").map(::Money).sumOf { it } shouldBe Money("6.60")
        }
        test("sumOf über eine leere Liste ist ZERO") {
            emptyList<Money>().sumOf { it } shouldBe Money.ZERO
        }
    }

    context("Serialisierung (YAML)") {
        test("liest den YAML-Skalar 95.00") {
            Yaml.default.decodeFromString(MoneySerializer, "95.00") shouldBe Money("95.00")
        }
        test("Roundtrip erhält den Wert") {
            checkAll(money) { a ->
                Yaml.default.decodeFromString(MoneySerializer, Yaml.default.encodeToString(MoneySerializer, a)) shouldBe a
            }
        }
        test("unlesbarer Betrag nennt den Wert") {
            val error =
                shouldThrow<SerializationException> {
                    Yaml.default.decodeFromString(MoneySerializer, "neunzig")
                }
            error.message shouldContain "neunzig"
        }

        // #232: Der Konstruktor rundet weiter (siehe unten), die Eingabe nicht.
        test("eine dritte Nachkommastelle bricht ab, statt still zu runden") {
            val error =
                shouldThrow<SerializationException> {
                    Yaml.default.decodeFromString(MoneySerializer, "1000.005")
                }
            assertSoftly {
                error.message shouldContain "1000.005"
                error.message shouldContain "zwei Nachkommastellen"
            }
        }
        test("eine nachgestellte Null verliert nichts und wird gelesen") {
            Yaml.default.decodeFromString(MoneySerializer, "200.500") shouldBe Money("200.50")
        }
        test("der Konstruktor rundet unverändert weiter") {
            Money("1000.005") shouldBe Money("1000.01")
        }
    }

    context("Formatierung") {
        // toString ist Debug-Darstellung, nicht Report-Darstellung: locale-unabhängig,
        // ohne Währung. Das deutsche Format ist Sache der Ausgabeschicht.
        test("toString: locale-unabhängige Debug-Form") {
            assertSoftly {
                Money("1234.56").toString() shouldBe "1234.56"
                Money("-5").toString() shouldBe "-5.00"
                Money.ZERO.toString() shouldBe "0.00"
            }
        }
    }
})
