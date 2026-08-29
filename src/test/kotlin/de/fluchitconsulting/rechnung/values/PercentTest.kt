package de.fluchitconsulting.rechnung.values

import com.charleskorn.kaml.Yaml
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class PercentTest : FunSpec({

    context("Konstruktion als Multiplikator (Prozent / 100)") {
        test("valueAsMultiplier") {
            assertSoftly {
                Percent("19").valueAsMultiplier shouldBeEqualComparingTo BigDecimal("0.19")
                Percent("100").valueAsMultiplier shouldBeEqualComparingTo BigDecimal.ONE
                Percent("0").valueAsMultiplier shouldBeEqualComparingTo BigDecimal.ZERO
            }
        }
        test("Interop mit Money.times: 19 % von 100 = 19") {
            Money("100") * Percent("19") shouldBe Money("19.00")
            Money("200") * Percent("50") shouldBe Money("100.00")
        }
    }

    context("Percent × Percent (Anteil vom Anteil)") {
        test("50 % von 19 % sind 9,5 %") {
            Percent("50") * Percent("19") shouldBe Percent("9.5")
        }
        test("100 % ist neutral") {
            Percent("100") * Percent("7") shouldBe Percent("7")
        }
    }

    context("Wertgleichheit") {
        test("scale-unabhängig mit gleichem hashCode") {
            Percent("10") shouldBe Percent("10.00")
            Percent("10").hashCode() shouldBe Percent("10.00").hashCode()
        }
        test("ungleich bei anderem Wert / null / Fremdtyp") {
            (Percent("10") == Percent("5")) shouldBe false
            Percent("10").equals(null) shouldBe false
            Percent("10").equals("10") shouldBe false
        }
    }

    context("Serialisierung (YAML)") {
        test("liest den YAML-Skalar 19") {
            Yaml.default.decodeFromString(PercentSerializer, "19") shouldBe Percent("19")
        }
        test("schreibt ohne überflüssige Nullen") {
            Yaml.default.encodeToString(PercentSerializer, Percent("19")) shouldBe "\"19\""
        }
    }

    test("valueAsPercentage und valueAsMultiplier sind zwei Lesarten desselben Werts") {
        Percent("19").valueAsPercentage shouldBe BigDecimal("19.0000000")
        Percent("19").valueAsMultiplier shouldBe BigDecimal("0.190000000")

        // Scale 7 in Prozent entspricht Scale 9 im Multiplikator – das Verschieben des
        // Kommas ist verlustfrei, die fachlich geforderte Genauigkeit bleibt erhalten.
        Percent("19").valueAsPercentage.scale() shouldBe 7
        Percent("19").valueAsMultiplier.scale() shouldBe 9
    }

    context("toString") {
        // Debug-Form, nicht Report-Form: Punkt statt Komma, Prozentzeichen direkt am Wert.
        test("toString: locale-unabhängige Debug-Form") {
            assertSoftly {
                Percent("19").toString() shouldBe "19.0000000%"
                Percent("2.5").toString() shouldBe "2.5000000%"
            }
        }
    }
})
