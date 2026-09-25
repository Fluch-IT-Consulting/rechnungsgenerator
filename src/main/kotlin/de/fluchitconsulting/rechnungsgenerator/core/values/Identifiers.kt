package de.fluchitconsulting.rechnungsgenerator.core.values

import de.fluchitconsulting.rechnungsgenerator.core.serialization.stringSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.math.BigInteger

// Kennungen aus den Stammdaten, die beim Einlesen geprüft werden.
//
// Warum überhaupt prüfen: Diese vier Werte tippt man einmal und schaut sie nie
// wieder an. Eine falsche IBAN heißt, dass Geld nicht ankommt; eine falsche
// USt-IdNr. macht die Rechnung angreifbar. Beides fällt ohne Prüfung erst
// Wochen später auf – und im ZUGFeRD-XML werden diese Felder ohnehin maschinell
// gelesen, also müssen sie dann syntaktisch stimmen.
//
// Die Werte werden normalisiert abgelegt (ohne Leerzeichen, Großbuchstaben) und
// über formatted() wieder lesbar ausgegeben. So steht auf der Rechnung die
// gewohnte Gruppierung, während im XML später die kanonische Form landet.

/** Wird geworfen, wenn eine Kennung syntaktisch nicht stimmt. */
class InvalidIdentifierException(message: String) : IllegalArgumentException(message)

private fun normalize(raw: String): String = raw.replace(Regex("[\\s.]"), "").uppercase()

/**
 * IBAN nach ISO 13616, geprüft über die Prüfsumme mod 97 – das ist keine
 * Formalie: Die Prüfziffer erkennt Zahlendreher, also genau den Fehler, den man
 * beim Abtippen einer Kontonummer macht.
 */
@Serializable(with = IbanSerializer::class)
class Iban(raw: String) {
    val value: String = normalize(raw)

    init {
        if (!Regex("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$").matches(value)) {
            throw InvalidIdentifierException("Keine gültige IBAN: '$raw'")
        }
        if (checksum(value) != BigInteger.ONE) {
            throw InvalidIdentifierException("IBAN mit falscher Prüfsumme: '$raw' – bitte Ziffern prüfen.")
        }
    }

    /** Vierergruppen, wie man sie auf Rechnungen liest: `DE89 3704 0044 …` */
    fun formatted(): String = value.chunked(4).joinToString(" ")

    override fun equals(other: Any?): Boolean = other is Iban && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = formatted()

    private companion object {
        /**
         * Die ersten vier Zeichen ans Ende, Buchstaben zu Zahlen (A=10 … Z=35),
         * das Ganze als eine große Zahl modulo 97. Ergibt 1, wenn die IBAN stimmt.
         * BigInteger, weil die Zahl bis zu 38 Stellen lang wird.
         */
        fun checksum(iban: String): BigInteger {
            val rearranged = iban.substring(4) + iban.substring(0, 4)
            val digits = rearranged.map { if (it.isDigit()) it.toString() else (it - 'A' + 10).toString() }
            return BigInteger(digits.joinToString("")).mod(BigInteger.valueOf(97))
        }
    }
}

/** BIC nach ISO 9362: 4 Zeichen Bank, 2 Land, 2 Ort, optional 3 für die Filiale. */
@Serializable(with = BicSerializer::class)
class Bic(raw: String) {
    val value: String = normalize(raw)

    init {
        if (!Regex("^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?$").matches(value)) {
            throw InvalidIdentifierException("Kein gültiger BIC: '$raw' (erwartet 8 oder 11 Zeichen nach ISO 9362)")
        }
    }

    override fun equals(other: Any?): Boolean = other is Bic && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}

/**
 * Umsatzsteuer-Identifikationsnummer. Der Länderpräfix ist Pflicht – EN 16931
 * fordert ihn in Regel BR-CO-09.
 *
 * Für deutsche Nummern wird zusätzlich die Prüfziffer gerechnet. Für andere
 * Länder bleibt es beim Formatabgleich: Jedes Land hat ein eigenes Verfahren,
 * und eine halbe Prüfung wäre schlechter als eine ehrlich fehlende.
 */
@Serializable(with = VatIdSerializer::class)
class VatId(raw: String) {
    val value: String = normalize(raw)

    init {
        if (!Regex("^[A-Z]{2}[A-Z0-9]{2,12}$").matches(value)) {
            throw InvalidIdentifierException("Keine gültige USt-IdNr.: '$raw' (erwartet Länderpräfix und Kennung)")
        }
        if (value.startsWith("DE")) {
            val digits = value.drop(2)
            if (!Regex("^[0-9]{9}$").matches(digits)) {
                throw InvalidIdentifierException("Deutsche USt-IdNr. braucht neun Ziffern: '$raw'")
            }
            if (germanCheckDigit(digits.take(8)) != digits.last().digitToInt()) {
                throw InvalidIdentifierException("USt-IdNr. mit falscher Prüfziffer: '$raw'")
            }
        }
    }

    /** Deutsche Schreibweise mit Leerzeichen zur besseren Lesbarkeit: `DE 325 503 529`. */
    fun formatted(): String =
        if (value.startsWith("DE") && value.length == 11) {
            "DE " + value.drop(2).chunked(3).joinToString(" ")
        } else {
            value
        }

    override fun equals(other: Any?): Boolean = other is VatId && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = formatted()

    private companion object {
        /** Verfahren des Bundeszentralamts für Steuern: verschachteltes mod 10 / mod 11. */
        fun germanCheckDigit(first8: String): Int {
            var p = 10
            for (c in first8) {
                val m = ((c.digitToInt() + p) % 10).let { if (it == 0) 10 else it }
                p = (2 * m) % 11
            }
            return (11 - p).let { if (it == 10) 0 else it }
        }
    }
}

/**
 * Steuernummer des Finanzamts.
 *
 * Bewusst nur eine grobe Strukturprüfung: Die Formate unterscheiden sich je
 * Bundesland (10 bis 11 Ziffern, verschieden gruppiert), und eine Prüfziffer
 * gibt es im geschriebenen Format nicht. Eine strengere Regel würde gültige
 * Nummern ablehnen – schlimmer als eine fehlende Prüfung.
 */
@Serializable(with = TaxNumberSerializer::class)
class TaxNumber(raw: String) {
    val value: String = raw.trim()

    init {
        val digits = value.filter { it.isDigit() }
        if (!Regex("^[0-9/ ]+$").matches(value) || digits.length !in 10..13) {
            throw InvalidIdentifierException(
                "Keine plausible Steuernummer: '$raw' (erwartet 10 bis 13 Ziffern, ggf. mit Schrägstrichen)",
            )
        }
    }

    override fun equals(other: Any?): Boolean = other is TaxNumber && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}

// --- Serializer -----------------------------------------------------------
// Alle vier nach demselben Muster: als String lesen, beim Bauen prüfen, eine
// fehlerhafte Kennung als SerializationException melden. Dadurch nennt die
// CLI den Fehler wie einen YAML-Fehler und nicht wie einen Programmabsturz.

private inline fun <T> parseOrFail(raw: String, build: (String) -> T): T =
    try {
        build(raw)
    } catch (e: InvalidIdentifierException) {
        throw SerializationException(e.message, e)
    }

object IbanSerializer :
    KSerializer<Iban> by stringSerializer("Iban", { it.formatted() }, { parseOrFail(it, ::Iban) })

object BicSerializer :
    KSerializer<Bic> by stringSerializer("Bic", { it.value }, { parseOrFail(it, ::Bic) })

object VatIdSerializer :
    KSerializer<VatId> by stringSerializer("VatId", { it.value }, { parseOrFail(it, ::VatId) })

object TaxNumberSerializer :
    KSerializer<TaxNumber> by stringSerializer("TaxNumber", { it.value }, { parseOrFail(it, ::TaxNumber) })
