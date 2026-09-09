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

class PriceTest : FunSpec({

    // Arb<Price>: Werte mit vier Nachkommastellen – bewusst feiner als Cent,
    // denn genau darin unterscheidet sich Price von Money.
    val price: Arb<Price> = Arb.long(-10_000_000L..10_000_000L).map { Price(BigDecimal.valueOf(it, 4)) }

    context("Konstruktion") {
        test("rundet nicht – die Nachkommastellen bleiben erhalten") {
            assertSoftly {
                Price("0.335").value shouldBe BigDecimal("0.335")
                Price("0.0425").value shouldBe BigDecimal("0.0425")
                Price("95").value shouldBe BigDecimal("95")
            }
        }
        test("String- und BigDecimal-Konstruktor sind äquivalent") {
            checkAll(price) { p -> Price(p.value.toString()) shouldBe p }
        }
    }

    context("Arithmetik bleibt exakt") {
        // Price trägt seit #32 nur noch, was die Domäne braucht: Menge mal
        // Einzelpreis und den Übergang zu Money. Addition und Vorzeichen sind
        // entfallen – zwei Einzelpreise zu addieren ergibt keinen Einzelpreis.
        test("Menge 1 ist neutral") { checkAll(price) { a -> a * BigDecimal.ONE shouldBe a } }
        test("Menge × Einzelpreis, ohne jede Rundung") {
            assertSoftly {
                // Der Kernfall der Rechnung: Stunden × Stundensatz.
                Price("95.00") * BigDecimal("86.5") shouldBe Price("8217.50")
                // Sub-Cent-Preis: die dritte Stelle überlebt die Multiplikation.
                Price("0.335") * BigDecimal("1000") shouldBe Price("335.000")
            }
        }
    }

    context("Übergang zu Money") {
        test("roundToAmount rundet kaufmännisch auf Cent") {
            assertSoftly {
                Price("123.7375").roundToAmount() shouldBe Money("123.74")
                Price("1.005").roundToAmount() shouldBe Money("1.01")
                Price("-1.005").roundToAmount() shouldBe Money("-1.01")
            }
        }

        // Der eigentliche Punkt der beiden Typen: erst rechnen, dann runden.
        // Würde der Einzelpreis vorher auf Cent normalisiert (0,335 -> 0,34),
        // kämen hier 340,00 € heraus – 5 € zu viel.
        test("erst multiplizieren, dann runden – nicht umgekehrt") {
            val net = (Price("0.335") * BigDecimal("1000")).roundToAmount()

            net shouldBe Money("335.00")
        }
    }

    context("Wertgleichheit") {
        test("scale-unabhängig mit gleichem hashCode") {
            Price("0.10") shouldBe Price("0.100")
            Price("0.10").hashCode() shouldBe Price("0.100").hashCode()
        }
        test("nicht gleich zu null, Fremdtyp oder Money") {
            Price("1").equals(null) shouldBe false
            Price("1").equals("1") shouldBe false
            // Price und Money sind gleichrangige Typen ohne Verwandtschaft –
            // deshalb sind sie auch bei gleichem Zahlenwert nicht gleich.
            Price("1").equals(Money("1")) shouldBe false
        }
    }

    context("Serialisierung (YAML)") {
        test("liest einen Preis mit mehr als zwei Nachkommastellen") {
            Yaml.default.decodeFromString(PriceSerializer, "0.0425") shouldBe Price("0.0425")
        }
        test("Roundtrip erhält den Wert") {
            checkAll(price) { a ->
                Yaml.default.decodeFromString(PriceSerializer, Yaml.default.encodeToString(PriceSerializer, a)) shouldBe a
            }
        }
        test("unlesbarer Preis nennt den Wert") {
            val fehler =
                shouldThrow<SerializationException> {
                    Yaml.default.decodeFromString(PriceSerializer, "neunzig")
                }
            fehler.message shouldContain "neunzig"
        }
    }
})
