package de.fluchitconsulting.rechnungsgenerator.core.values

import com.charleskorn.kaml.Yaml
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class IdentifiersTest : FunSpec({

    context("IBAN") {
        test("nimmt gültige IBANs mit und ohne Leerzeichen an") {
            assertSoftly {
                Iban("DE89 3704 0044 0532 0130 00").value shouldBe "DE89370400440532013000"
                Iban("DE89370400440532013000").value shouldBe "DE89370400440532013000"
                // Kleinschreibung und Punkte kommen beim Kopieren aus PDFs vor.
                Iban("de89.3704.0044.0532.0130.00").value shouldBe "DE89370400440532013000"
            }
        }

        test("gibt die gewohnte Vierergruppierung zurück") {
            Iban("DE89370400440532013000").formatted() shouldBe "DE89 3704 0044 0532 0130 00"
        }

        test("erkennt einen Zahlendreher über die Prüfsumme") {
            // Genau der Fehler, den man beim Abtippen macht: zwei Ziffern vertauscht.
            // Das Muster stimmt weiterhin, nur die Prüfsumme nicht.
            val error = shouldThrow<InvalidIdentifierException> { Iban("DE89 3704 0044 0532 0130 09") }
            error.message!! shouldContain "Prüfsumme"
        }

        test("weist zu kurze oder unsinnige Eingaben ab") {
            assertSoftly {
                shouldThrow<InvalidIdentifierException> { Iban("DE89") }
                shouldThrow<InvalidIdentifierException> { Iban("1234567890123456") }
                shouldThrow<InvalidIdentifierException> { Iban("") }
            }
        }

        test("gleiche IBAN bleibt gleich, egal wie geschrieben") {
            Iban("DE89 3704 0044 0532 0130 00") shouldBe Iban("de89370400440532013000")
        }
    }

    context("BIC") {
        test("akzeptiert die 8- und die 11-stellige Form") {
            assertSoftly {
                Bic("COBADEFF").value shouldBe "COBADEFF"
                Bic("COBADEFFXXX").value shouldBe "COBADEFFXXX"
            }
        }

        test("weist falsche Längen und Zeichen ab") {
            assertSoftly {
                shouldThrow<InvalidIdentifierException> { Bic("COBADEF") } // 7 Zeichen
                shouldThrow<InvalidIdentifierException> { Bic("COBADEFFXX") } // 10 Zeichen
                shouldThrow<InvalidIdentifierException> { Bic("1234DEFF") } // Bankteil muss Buchstaben sein
            }
        }
    }

    context("USt-IdNr.") {
        test("rechnet die Prüfziffer deutscher Nummern nach") {
            // DE123456788 hat eine gültige Prüfziffer, DE123456789 nicht –
            // die beiden unterscheiden sich nur in der letzten Stelle.
            VatId("DE123456788").value shouldBe "DE123456788"
            shouldThrow<InvalidIdentifierException> { VatId("DE123456789") }
                .message!! shouldContain "Prüfziffer"
        }

        test("Leerzeichen in der Schreibweise stören nicht") {
            VatId("DE 123 456 788").value shouldBe "DE123456788"
        }

        test("gibt die deutsche Schreibweise mit Gruppen zurück") {
            VatId("DE123456788").formatted() shouldBe "DE 123 456 788"
        }

        test("verlangt den Länderpräfix – EN 16931 BR-CO-09") {
            shouldThrow<InvalidIdentifierException> { VatId("123456788") }
        }

        test("prüft bei anderen Ländern nur das Format") {
            // Jedes Land hat ein eigenes Prüfverfahren; eine halbe Prüfung wäre
            // schlechter als eine ehrlich fehlende.
            assertSoftly {
                VatId("ATU12345678").value shouldBe "ATU12345678"
                VatId("CHE123456789").value shouldBe "CHE123456789"
            }
        }
    }

    context("Steuernummer") {
        test("nimmt die üblichen Schreibweisen an") {
            assertSoftly {
                TaxNumber("257/123/45678").value shouldBe "257/123/45678"
                TaxNumber("12/345/67890").value shouldBe "12/345/67890"
                TaxNumber("1234567890").value shouldBe "1234567890"
            }
        }

        test("weist offensichtlich Falsches ab") {
            assertSoftly {
                shouldThrow<InvalidIdentifierException> { TaxNumber("123") }
                shouldThrow<InvalidIdentifierException> { TaxNumber("ABC/123/45678") }
            }
        }
    }

    context("Einheit") {
        // Gelesen wird über den Serializer und nicht über eine eigene
        // Nachschlagefunktion: Das ist der Weg, den eine YAML-Datei tatsächlich
        // nimmt, und seit die Deklaration an einer Stelle steht, gibt es
        // daneben keine zweite Tür mehr.
        fun unit(text: String): Unit = Yaml.default.decodeFromString(UnitSerializer, text)

        test("löst die deutschen Schreibweisen auf Rec-20-Codes auf") {
            assertSoftly {
                unit("Std.").code shouldBe "HUR"
                unit("Tage").code shouldBe "DAY"
                unit("pauschal").code shouldBe "LS"
                unit("Stück").code shouldBe "H87"
            }
        }

        test("ist unempfindlich gegen Groß- und Kleinschreibung") {
            unit("STD.") shouldBe Unit.HOUR
        }

        test("eine unbekannte Einheit nennt die zulässigen") {
            val error = shouldThrow<Exception> { unit("Stnd.") }
            assertSoftly {
                error.message!! shouldContain "Stnd."
                error.message!! shouldContain "Std."
            }
        }
    }
})
