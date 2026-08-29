package de.fluchitconsulting.rechnung.values

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
        test("negate liefert das inverse Element") { checkAll(money) { a -> a + a.negate() shouldBe Money.ZERO } }
        test("a - b == a + (-b)") { checkAll(money, money) { a, b -> a - b shouldBe a + b.negate() } }
        test("negate ist involutiv") { checkAll(money) { a -> a.negate().negate() shouldBe a } }
        test("a - a == ZERO") { checkAll(money) { a -> a - a shouldBe Money.ZERO } }
    }

    context("Vorzeichen & Prädikate") {
        test("isZero genau dann, wenn == ZERO") { checkAll(money) { a -> a.isZero() shouldBe (a == Money.ZERO) } }
        test("isPositive und isNegative schließen sich aus") {
            checkAll(money) { a -> (a.isPositive() && a.isNegative()) shouldBe false }
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

    context("Multiplikation mit Percent") {
        test("Prozentanteil eines Betrags") { Money("100") * Percent("10") shouldBe Money("10.00") }
        test("100 % ist neutral") { checkAll(money) { a -> a * Percent("100") shouldBe a } }
        test("Percent × Money ist kommutativ zu Money × Percent") {
            checkAll(money) { a -> Percent("19") * a shouldBe a * Percent("19") }
        }
    }

    // Money × Menge gibt es bewusst nicht – das ist Sache von Price, siehe PriceTest.
    // Ein Test dafür kann hier nicht stehen: Er würde schlicht nicht kompilieren.

    context("Summen-Extensions") {
        test("sumOf über eine Liste") {
            listOf("1.10", "2.20", "3.30").map(::Money).sumOf { it } shouldBe Money("6.60")
        }
        test("sum einer leeren Liste ist ZERO") {
            emptyList<Money>().sum() shouldBe Money.ZERO
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
            val fehler =
                shouldThrow<SerializationException> {
                    Yaml.default.decodeFromString(MoneySerializer, "neunzig")
                }
            fehler.message shouldContain "neunzig"
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
