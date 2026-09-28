package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import de.fluchitconsulting.rechnungsgenerator.core.values.Currency
import de.fluchitconsulting.rechnungsgenerator.core.values.Language
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Das Pflichtangaben-Netz ([PflichtangabenRun]) an den Fällen, die kein
 * Beispiel einer Rechnungsart zeigt: je eine eigene Datei für Steuersätze,
 * Skonto, Absetzung, Ausland, Sprache, Währung und Zahlen, dazu Stammdaten
 * ohne Geschäftsführung und Logo.
 */
class PflichtangabenSonderfaelleTest : FunSpec({
    tags(Toolchain)

    val net = PflichtangabenRun()
    val designs = net.designs

    // Mehrere Steuersätze (#218): §14 Abs. 4 Nr. 7 verlangt das Entgelt je
    // Satz. In den Beispielen je Art hat jeder Satz genau eine Position, und
    // deren Betrag stünde zufällig als Positionsbetrag da – hier je Satz zwei,
    // damit das Entgelt nur über den Summenblock auf das Blatt kommen kann.
    // Dazu eine befreite Position (Hinweis statt Satz und Steuerbetrag) und
    // eine mit Nullsatz (Satz und Steuerbetrag, keine Befreiung).
    for (design in designs) {
        test("gemischte Steuersätze im Design ${design.name}: gültig, mit dem Entgelt je Steuersatz") {
            val (calculated, _) =
                net.typesetAndCheck("gemischte-steuersaetze", design, design.sellerConfig, targetName = "${design.name}-gemischt")

            // 19 %, 7 %, steuerbefreit, Nullsatz – die vier Fälle nebeneinander.
            calculated.vatGroups.size shouldBe 4
        }
    }

    // Skonto (#342): §14 Abs. 4 Satz 1 Nr. 7 verlangt die im Voraus
    // vereinbarte Minderung. Keine Rechnung einer Art trägt eines, deshalb
    // eine eigene Datei, mit zwei Stufen.
    for (design in designs) {
        test("Skonto im Design ${design.name}: gültig, mit Satz und Frist jeder Stufe") {
            val (calculated, _) = net.typesetAndCheck("skonto", design, design.sellerConfig, targetName = "${design.name}-skonto")

            calculated.invoice.skonto.size shouldBe 2
        }

        // Dieselbe Datei auf Englisch (#406). Satz und Frist jeder Stufe prüft
        // verify mit den Schreibweisen der Sprache; der Anfang des Satzes und
        // "in full" stehen hier wörtlich, weil verify keine Sätze kennt.
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

    // Eine Schlussrechnung, deren Abschläge den Gesamtbetrag genau decken
    // (#342): Die Schlussrechnung der Arten hat einen Restbetrag, und ohne
    // diese Datei entstünden der Satz statt der Null und der Wegfall von
    // Zahlungsziel und Bankverbindung in keinem PDF.
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

        // Dieselbe Datei auf Englisch (#405): Die Beispiele je Art laufen im
        // PflichtangabenEnglischTest auch, aber keines ist ausgeglichen.
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

    // Eine Rechnung ins Ausland (#374): Unter dem Ort steht das Land,
    // ausgeschrieben und in Versalien nach DIN 5008, nicht der Code aus der
    // Datei. Der Name steht hier wörtlich und kommt nicht aus dem JDK – sonst
    // prüfte das Netz die Umsetzung gegen sich selbst.
    for (design in designs) {
        test("Auslandsrechnung im Design ${design.name}: gültig, mit dem Land ausgeschrieben unter der Anschrift") {
            val (calculated, sheet) =
                net.typesetAndCheck("auslandsrechnung", design, design.sellerConfig, targetName = "${design.name}-ausland")

            calculated.isDomestic shouldBe false
            sheet.shows("Land Kunde", "1010 Wien ÖSTERREICH")
        }
    }

    // Eine englische Rechnung (#377) an einen Unternehmer im Vereinigten
    // Königreich, Kategorie O: der häufigste Fall. verify prüft sie mit den
    // Wörtern des Designs und den Schreibweisen der Sprache; das Land und die
    // Einheiten stehen hier wörtlich, aus demselben Grund wie ÖSTERREICH oben.
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

    // Eine Rechnung in amerikanischem Englisch (#407) an einen Unternehmer
    // in den USA. Das lange Datum prüft verify mit den Schreibweisen der
    // Sprache; hier steht es wörtlich, samt dem Komma nach dem Jahr im Satz
    // zum Skonto, und das Land aus demselben Grund wie ÖSTERREICH oben.
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

    // Eine Rechnung in US-Dollar auf Deutsch (#409): Die Sprache bestimmt
    // die Schreibweise, die Währung das Zeichen. Die Beträge stehen hier
    // wörtlich, aus demselben Grund wie ÖSTERREICH oben – das Zeichen und
    // seine Stellung prüft verify nicht, weil ein Design es in den
    // Spaltenkopf stellen darf.
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

    // Die Schreibweise der Zahlen (ADR-0021): Die Beispiele je Art tragen
    // Einzelpreise mit zwei Nachkommastellen und Mengen unter 1.000. Wie ein
    // Design längere Zahlen gruppiert, zeigte sich an keinem davon.
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

    // Die Stammdaten oben führen eine Geschäftsführung, und jedes Design läuft
    // dort mit Logo. Zwei Zweige der Vorlagen erreichten so nie ein echtes PDF:
    // der Name des Inhabers, der nur ohne Geschäftsführung dasteht, und das
    // Blatt ohne Logo, der Fall ohne Eintrag. Beide hängen an den Stammdaten und
    // nicht an der Rechnungsart, deshalb genügt die gewöhnliche Rechnung.
    for (design in designs) {
        test("rechnung im Design ${design.name} für ein Einzelunternehmen ohne Logo: gültig, mit dem Namen des Inhabers") {
            val soleProprietorship = net.sellerConfigWith(design.name, logo = false, withManagingDirector = false)

            val (calculated, _) = net.typesetAndCheck("rechnung", design, soleProprietorship, targetName = "${design.name}-einzel")

            calculated.config.managingDirector shouldBe null
            calculated.config.logo shouldBe null
        }
    }
})
