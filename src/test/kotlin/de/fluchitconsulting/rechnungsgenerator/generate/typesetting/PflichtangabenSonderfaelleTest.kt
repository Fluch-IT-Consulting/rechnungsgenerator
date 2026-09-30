package de.fluchitconsulting.rechnungsgenerator.generate.typesetting

import de.fluchitconsulting.rechnungsgenerator.core.values.Currency
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Was `verify` nicht kennt, steht hier wörtlich und nicht aus dem JDK oder dem
 * Programm gelesen, sonst prüfte das Netz die Umsetzung gegen sich selbst.
 */
class PflichtangabenSonderfaelleTest : FunSpec({
    tags(Toolchain)

    val net = PflichtangabenRun()
    val designs = net.designs

    // #218. Je Satz zwei Positionen, sonst stünde das Entgelt je Satz schon
    // zufällig als Positionsbetrag da.
    for (design in designs) {
        test("gemischte Steuersätze im Design ${design.name}: gültig, mit dem Entgelt je Steuersatz") {
            val (calculated, _) =
                net.typesetAndCheck("gemischte-steuersaetze", design, design.sellerConfig, targetName = "${design.name}-gemischt")

            calculated.vatGroups.size shouldBe 4
        }
    }

    // #342
    for (design in designs) {
        test("Skonto im Design ${design.name}: gültig, mit Satz und Frist jeder Stufe") {
            val (calculated, _) = net.typesetAndCheck("skonto", design, design.sellerConfig, targetName = "${design.name}-skonto")

            calculated.invoice.skonto.size shouldBe 2
        }

        // #406
        test("Skonto auf Englisch im Design ${design.name}: gültig, mit Satz und Frist jeder Stufe") {
            val input = net.workDir.resolve("skonto-englisch.yaml")
            input.writeText("sprache: en-GB\n" + Path.of("src/test/resources/skonto.yaml").readText())

            val (calculated, sheet) =
                net.typesetAndCheck("skonto", design, design.sellerConfig, targetName = "${design.name}-skonto-englisch", input = input)

            calculated.invoice.language shouldBe Language.BRITISH_ENGLISH
            sheet.shows("Skonto", "For payment by 21 August 2026, an early payment discount of 2%")
            if (design.closingLineAtPaymentTerm) sheet.shows("Zahlungsfrist", "Payment is due in full 14 days after the invoice date.")
            sheet.lacks("Deutsch auf dem Blatt", "Skonto", "ohne Abzug")
        }
    }

    // #342
    for (design in designs) {
        test("ausgeglichene Schlussrechnung im Design ${design.name}: gültig, ohne Zahlungsaufforderung") {
            val (calculated, _) =
                net.typesetAndCheck(
                    "schlussrechnung-ausgeglichen",
                    design,
                    design.sellerConfig,
                    targetName = "${design.name}-ausgeglichen",
                )

            calculated.prepayments.size shouldBe 2
            calculated.remainingAmount.isZero() shouldBe true
        }

        // #405
        test("ausgeglichene Schlussrechnung auf Englisch im Design ${design.name}: gültig, mit dem englischen Satz") {
            val input = net.workDir.resolve("schlussrechnung-ausgeglichen-englisch.yaml")
            input.writeText("sprache: en-GB\n" + Path.of("src/test/resources/schlussrechnung-ausgeglichen.yaml").readText())

            val (calculated, _) =
                net.typesetAndCheck(
                    "schlussrechnung-ausgeglichen",
                    design,
                    design.sellerConfig,
                    targetName = "${design.name}-ausgeglichen-englisch",
                    input = input,
                )

            calculated.invoice.language shouldBe Language.BRITISH_ENGLISH
        }
    }

    // #374, das Land nach DIN 5008.
    for (design in designs) {
        test("Auslandsrechnung im Design ${design.name}: gültig, mit dem Land ausgeschrieben unter der Anschrift") {
            val (calculated, sheet) =
                net.typesetAndCheck("auslandsrechnung", design, design.sellerConfig, targetName = "${design.name}-ausland")

            calculated.isDomestic shouldBe false
            sheet.shows("Land Kunde", "1010 Wien ÖSTERREICH")
        }
    }

    // #377
    for (design in designs) {
        test("englische Rechnung im Design ${design.name}: gültig, mit allen Pflichtangaben auf Englisch") {
            val (calculated, sheet) =
                net.typesetAndCheck("englische-rechnung", design, design.sellerConfig, targetName = "${design.name}-englisch")

            calculated.invoice.language shouldBe Language.BRITISH_ENGLISH
            sheet.shows("Land Kunde", "EC1A 1BB London UNITED KINGDOM")
            sheet.shows("Einheit, eine Stunde", "1 hr")
            sheet.shows("Einheit, ein Tag", "1 day")
            sheet.shows("Einheit, zwei Tage", "2 days")
            sheet.shows("Einheit, ein halber Tag", "0.5 days")
            sheet.shows("negativer Betrag", "\u2212£200.00")
            sheet.lacks("Euro auf einer Rechnung in Pfund", "€")
            sheet.lacks("Deutsch auf dem Blatt", "Rechnung", "Leistungszeitraum", "Umsatzsteuer", "Seite")
        }
    }

    // #407
    for (design in designs) {
        test("amerikanische Rechnung im Design ${design.name}: gültig, mit dem Datum der Variante") {
            val (calculated, sheet) =
                net.typesetAndCheck("amerikanische-rechnung", design, design.sellerConfig, targetName = "${design.name}-amerikanisch")

            calculated.invoice.language shouldBe Language.AMERICAN_ENGLISH
            sheet.shows("Land Kunde", "94105 San Francisco, CA UNITED STATES")
            sheet.shows("Rechnungsdatum", "August 14, 2026")
            sheet.shows("Skonto", "For payment by August 21, 2026, an early payment discount of 2% may be deducted")
            sheet.lacks("britisches Datum", "14 August 2026", "21 August 2026")
            sheet.shows("Betrag in US-Dollar", "$3,800.00")
            sheet.lacks("Euro auf einer Rechnung in US-Dollar", "€")
            sheet.lacks("Deutsch auf dem Blatt", "Rechnung", "Leistungszeitraum", "Umsatzsteuer", "Skonto")
        }
    }

    // #409
    for (design in designs) {
        test("Rechnung in US-Dollar auf Deutsch im Design ${design.name}: gültig, mit dem Dollarzeichen hinter der Zahl") {
            val (calculated, sheet) =
                net.typesetAndCheck("dollar-auf-deutsch", design, design.sellerConfig, targetName = "${design.name}-dollar")

            calculated.invoice.language shouldBe Language.GERMAN
            calculated.invoice.currency shouldBe Currency.USD
            sheet.shows("Betrag in US-Dollar", "4.400,00 $")
            sheet.shows("negativer Betrag", "\u2212200,00 $")
            sheet.lacks("Euro auf einer Rechnung in US-Dollar", "€")
        }
    }

    // #418
    val conversionSpellings =
        mapOf(
            Language.GERMAN to listOf("1.463,41 €", "1 € = 1,1685 $", "BMF für Juli 2026"),
            Language.BRITISH_ENGLISH to listOf("€1,463.41", "€1 = $1.1685", "Finance for July 2026"),
            Language.AMERICAN_ENGLISH to listOf("€1,463.41", "€1 = $1.1685", "Finance for July 2026"),
        )
    for ((language, spellings) in conversionSpellings) {
        for (design in designs) {
            test("Umsatzsteuer in US-Dollar auf ${language.tag} im Design ${design.name}: gültig, mit der Steuer in Euro") {
                val input = net.workDir.resolve("umsatzsteuer-in-dollar-${language.tag}.yaml")
                input.writeText(
                    "sprache: ${language.tag}\n" + Path.of("src/test/resources/umsatzsteuer-in-dollar.yaml").readText(),
                )

                val (calculated, sheet) =
                    net.typesetAndCheck(
                        "umsatzsteuer-in-dollar",
                        design,
                        design.sellerConfig,
                        targetName = "${design.name}-umsatzsteuer-in-dollar-${language.tag}",
                        input = input,
                    )

                calculated.invoice.language shouldBe language
                val (euro, rate, month) = spellings
                sheet.shows("Umsatzsteuer in Euro", euro)
                sheet.shows("Umrechnungskurs", rate)
                sheet.shows("Monat des Kurses", month)
            }
        }
    }

    // ADR-0021
    for (design in designs) {
        test("Schreibweise der Zahlen im Design ${design.name}: jeder Einzelpreis mit allen Nachkommastellen") {
            val (calculated, _) =
                net.typesetAndCheck("schreibweise-der-zahlen", design, design.sellerConfig, targetName = "${design.name}-zahlen")

            calculated.invoice.items.maxOf {
                it.unitPrice.value
                    .stripTrailingZeros()
                    .scale()
            } shouldBe 5
        }
    }

    // Der einzige Lauf ohne Geschäftsführung und ohne Logo.
    for (design in designs) {
        test("rechnung im Design ${design.name} für ein Einzelunternehmen ohne Logo: gültig, mit dem Namen des Inhabers") {
            val soleProprietorship = net.sellerConfigWith(design.name, logo = false, withManagingDirector = false)

            val (calculated, _) = net.typesetAndCheck("rechnung", design, soleProprietorship, targetName = "${design.name}-einzel")

            calculated.config.managingDirector shouldBe null
            calculated.config.logo shouldBe null
        }
    }
})
