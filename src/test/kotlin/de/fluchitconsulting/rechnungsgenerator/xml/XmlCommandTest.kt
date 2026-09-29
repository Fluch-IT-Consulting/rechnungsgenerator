package de.fluchitconsulting.rechnungsgenerator.xml

import de.fluchitconsulting.rechnungsgenerator.core.accepted.accepted
import de.fluchitconsulting.rechnungsgenerator.core.calculation.CalculatedInvoice
import de.fluchitconsulting.rechnungsgenerator.core.model.YamlLoader
import de.fluchitconsulting.rechnungsgenerator.execute
import de.fluchitconsulting.rechnungsgenerator.generate.einvoice.CiiWriter
import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.EInvoiceEmbedder
import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.Toolchain
import de.fluchitconsulting.rechnungsgenerator.toolchain.capturingStandardError
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.mustangproject.BankDetails
import org.mustangproject.Invoice
import org.mustangproject.Item
import org.mustangproject.Product
import org.mustangproject.TradeParty
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA1
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.math.BigDecimal
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readBytes
import kotlin.text.Charsets.UTF_8

/**
 * `xml` von außen: Exitcode, stdout als Bytes, stderr als Text.
 *
 * Die E-Rechnungen entstehen ohne LuaLaTeX über den Embedder aus
 * `rechnung-a1b.pdf`, also genau so wie hinter `erzeuge --zugferd`, nur ohne
 * den Satz davor. Allein die Rundreise durch beide Kommandos setzt wirklich
 * und trägt deshalb den Tag [Toolchain].
 */
class XmlCommandTest : FunSpec({

    val dir = createTempDirectory("xml-command-test")
    val a1b = Path.of("src/test/resources/rechnung-a1b.pdf")
    val xml = Path.of("src/test/resources/rechnung-erwartet.xml").readBytes()
    val withoutCountry = xml.decodeToString().replaceFirst("<ram:CountryID>DE</ram:CountryID>", "").toByteArray()

    // Die eigenen Rechnungen tragen keine Warnung (ADR-0023). Ein leerer
    // Lieferblock steht für eine fremde, die eine trägt: PEPPOL-EN16931-R008.
    val withoutDelivery =
        xml
            .decodeToString()
            .replace(
                Regex("(?s)<ram:ApplicableHeaderTradeDelivery>.*?</ram:ApplicableHeaderTradeDelivery>"),
                "<ram:ApplicableHeaderTradeDelivery/>",
            ).toByteArray()

    fun einvoice(
        name: String,
        xml: ByteArray,
    ): Path = dir.resolve(name).also { EInvoiceEmbedder.embed(a1b, xml, it) }

    test("aus einer E-Rechnung des Generators kommt ihr XML Byte für Byte, und stderr bleibt leer") {
        val result = runProgram("xml", einvoice("e-rechnung.pdf", xml).toString())

        withClue(result.err) { result.exitCode shouldBe 0 }
        result.out.contentEquals(xml) shouldBe true
        result.err shouldBe ""
    }

    test("eine Warnung steht als Hinweis auf stderr, und das XML kommt trotzdem") {
        val result = runProgram("xml", einvoice("mit-warnung.pdf", withoutDelivery).toString())

        withClue(result.err) { result.exitCode shouldBe 0 }
        result.out.contentEquals(withoutDelivery) shouldBe true
        result.err shouldContain "Hinweis: [PEPPOL-EN16931-R008]"
    }

    test("das PDF/A-1b aus erzeuge ohne --zugferd nennt beide Gründe, und stdout bleibt leer") {
        val result = runProgram("xml", a1b.toString())

        result.exitCode shouldBe 1
        result.out.size shouldBe 0
        result.err shouldContain "rechnung-a1b.pdf ist keine gültige E-Rechnung nach ZUGFeRD."
        result.err shouldContain "Es gibt sich als PDF/A-1b aus."
        result.err shouldContain "Es trägt kein XML einer E-Rechnung"
    }

    // Der Embedder prüft selbst nichts (ADR-0023), und so entsteht ein
    // gültiges PDF/A-3u um ein ungültiges XML.
    test("ein PDF/A-3u, dessen XML BR-09 verletzt, nennt den Verstoß und nicht das PDF/A") {
        val result = runProgram("xml", einvoice("br-09.pdf", withoutCountry).toString())

        result.exitCode shouldBe 1
        result.out.size shouldBe 0
        result.err shouldContain "Das XML in factur-x.xml ist ungültig:"
        result.err shouldContain "[BR-09]"
        result.err shouldNotContain "verletzt:"
    }

    // Teil 3 stimmt, die Stufe nicht: Die Meldung nennt die Stufe und
    // widerspricht sich nicht mit "gibt sich als PDF/A-3 aus … ist ein
    // PDF/A-3" (#386).
    test("ein PDF/A-3 ohne Konformitätsstufe nennt die fehlende Stufe") {
        val pdf = writePdf(dir.resolve("ohne-stufe.pdf"), mapOf("factur-x.xml" to xml), xmpClaiming(3, null))

        val result = runProgram("xml", pdf.toString())

        result.exitCode shouldBe 1
        result.out.size shouldBe 0
        result.err shouldContain "Es gibt sich als PDF/A-3 aus, nennt aber keine Konformitätsstufe."
        result.err shouldContain "Eine E-Rechnung nach ZUGFeRD ist ein PDF/A-3a, -3b oder -3u."
    }

    test("ein PDF/A-3 mit unbekannter Konformitätsstufe nennt sie") {
        val pdf = writePdf(dir.resolve("unbekannte-stufe.pdf"), mapOf("factur-x.xml" to xml), xmpClaiming(3, "X"))

        val result = runProgram("xml", pdf.toString())

        result.exitCode shouldBe 1
        result.out.size shouldBe 0
        result.err shouldContain "Es gibt sich als PDF/A-3x aus, eine Konformitätsstufe, die PDF/A-3 nicht kennt."
        result.err shouldContain "Eine E-Rechnung nach ZUGFeRD ist ein PDF/A-3a, -3b oder -3u."
    }

    test("ein PDF, das sich als PDF/A-3u ausgibt, es aber nicht ist, fällt beim PDF/A durch") {
        val pdf = writePdf(dir.resolve("kein-pdfa.pdf"), mapOf("factur-x.xml" to xml), xmpClaiming(3, "U"))

        val result = runProgram("xml", pdf.toString())

        result.exitCode shouldBe 1
        result.out.size shouldBe 0
        result.err shouldContain "PDF/A-3u verletzt:"
        result.err shouldNotContain "Das XML in"
    }

    test("die Gründe beider Prüfer stehen auf einmal da") {
        val pdf = writePdf(dir.resolve("beides.pdf"), mapOf("factur-x.xml" to withoutCountry), xmpClaiming(3, "B"))

        val result = runProgram("xml", pdf.toString())

        result.err shouldContain "PDF/A-3b verletzt:"
        result.err shouldContain "[BR-09]"
    }

    test("zwei XML einer E-Rechnung: Welches gilt, sagt das PDF nicht") {
        val pdf = writePdf(dir.resolve("zwei.pdf"), mapOf("factur-x.xml" to xml, "xrechnung.xml" to xml), xmpClaiming(3, "U"))

        val result = runProgram("xml", pdf.toString())

        result.exitCode shouldBe 1
        result.out.size shouldBe 0
        result.err shouldContain "Es trägt mehr als ein XML einer E-Rechnung"
        result.err shouldContain "factur-x.xml"
        result.err shouldContain "xrechnung.xml"
    }

    // EInvoiceAttachment reicht den Eintrag mit leerem XML weiter; dass es
    // abgelehnt wird, sagt erst der Prüfer. Deshalb von außen geprüft.
    test("ein Eintrag ohne eingebettete Datei heißt ungültiges XML, nicht kein Anhang") {
        val pdf =
            writePdf(
                dir.resolve("ohne-datei.pdf"),
                mapOf("factur-x.xml" to xml),
                xmpClaiming(3, "U"),
                embeddedUnder = EmbeddedUnder.NONE,
            )

        val result = runProgram("xml", pdf.toString())

        result.exitCode shouldBe 1
        result.out.size shouldBe 0
        result.err shouldContain
            "Das XML in factur-x.xml ist ungültig:\n  Kein wohlgeformtes XML (Zeile 1, Spalte 1): Vorzeitiges Dateiende."
        result.err shouldNotContain "Es trägt kein XML einer E-Rechnung"
    }

    // Ohne eigenen ErrorHandler schreibt der Parser des JDK [Fatal Error]
    // direkt auf System.err, am Ausgeber von execute() vorbei (#395). Deshalb
    // wird System.err eigens mitgeschnitten.
    test("ein XML, das nicht wohlgeformt ist, nennt Ort und Grund, und auf stderr steht nur diese Meldung") {
        val malformed = xml.decodeToString().replaceFirst("</ram:CountryID>", "</ram:Country>").toByteArray()
        val pdf = einvoice("nicht-wohlgeformt.pdf", malformed)

        val (result, systemErr) = capturingStandardError { runProgram("xml", pdf.toString()) }

        result.exitCode shouldBe 1
        result.out.size shouldBe 0
        result.err shouldBe
            "$pdf ist keine gültige E-Rechnung nach ZUGFeRD.\n" +
            "Das XML in factur-x.xml ist ungültig:\n" +
            "  Kein wohlgeformtes XML (Zeile 93, Spalte 30): " +
            "Elementtyp \"ram:CountryID\" muss mit dem entsprechenden Endtag \"</ram:CountryID>\" beendet werden.\n"
        systemErr shouldBe ""
    }

    // SpecificationIdentifier.of liefert dann null, und specificationReason
    // schweigt. Dass das XML trotzdem nicht durchgeht, sagt die XML-Prüfung
    // (#399), und zwar mit einem Grund statt eines Stacktrace (#426).
    test("ein XML ohne Kennung der Spezifikation lehnt die XML-Prüfung ab") {
        val withoutSpecification =
            xml
                .decodeToString()
                .replace(
                    Regex(
                        "<ram:GuidelineSpecifiedDocumentContextParameter>.*?</ram:GuidelineSpecifiedDocumentContextParameter>",
                        RegexOption.DOT_MATCHES_ALL,
                    ),
                    "",
                ).toByteArray()
        val pdf = einvoice("ohne-kennung.pdf", withoutSpecification)

        val result = runProgram("xml", pdf.toString())

        result.exitCode shouldBe 1
        result.out.size shouldBe 0
        result.err shouldBe
            "$pdf ist keine gültige E-Rechnung nach ZUGFeRD.\n" +
            "Das XML in factur-x.xml ist ungültig:\n" +
            "  Keine Kennung der Spezifikation (BT-24), gegen die sich das XML prüfen ließe.\n"
    }

    /**
     * Eine E-Rechnung, die nicht vom Generator stammt: Mustang schreibt sie
     * aus seinem eigenen Modell im [profile] nach [target] und gibt ihr XML
     * zurück. Der Prüfer erkennt das Profil am XML, und das PDF gibt sich als
     * das PDF/A-3 aus, das Mustang daraus macht.
     */
    fun foreignEInvoice(
        profile: String,
        target: Path,
    ): ByteArray {
        fun date(day: Int) = Date.from(LocalDate.of(2026, 8, day).atStartOfDay(ZoneOffset.UTC).toInstant())
        val invoice =
            Invoice()
                .setNumber("L-2026-0815")
                .setIssueDate(date(14))
                .setDueDate(date(28))
                .setDeliveryDate(date(14))
                .setSender(
                    TradeParty("Lieferant B GmbH", "Musterweg 1", "97070", "Würzburg", "DE")
                        .addVATID("DE123456789")
                        .addBankDetails(BankDetails("DE89370400440532013000", "MUSTDEFFXXX")),
                ).setRecipient(TradeParty("Kunde A GmbH", "Beispielstraße 2", "10115", "Berlin", "DE"))
                .addItem(Item(Product("Beratung", "", "HUR", BigDecimal("19")), BigDecimal("120.00"), BigDecimal("8")))
        val exporter = ZUGFeRDExporterFromA1().setZUGFeRDVersion(2).setProfile(profile).load(a1b.toString())
        exporter.setTransaction(invoice)
        exporter.export(target.toString())
        return exporter.provider.xml
    }

    test("eine fremde E-Rechnung im Profil EN16931 nimmt es an") {
        val target = dir.resolve("en16931.pdf")
        val written = foreignEInvoice("EN16931", target)

        val result = runProgram("xml", target.toString())

        withClue(result.err) { result.exitCode shouldBe 0 }
        result.out.contentEquals(written) shouldBe true
        result.out.decodeToString() shouldContain "urn:cen.eu:en16931:2017"
    }

    // Mustang findet an BASIC-WL nichts auszusetzen: Das Profil erlaubt eine
    // Rechnung ohne Positionen. Eine E-Rechnung ist sie trotzdem nicht (#399).
    test("eine fremde Rechnung im Profil BASIC-WL besteht beide Prüfer und wird trotzdem abgelehnt") {
        val target = dir.resolve("basicwl.pdf")
        foreignEInvoice("BASICWL", target)

        val result = runProgram("xml", target.toString())

        result.exitCode shouldBe 1
        result.out.size shouldBe 0
        result.err shouldContain "basicwl.pdf ist keine gültige E-Rechnung nach ZUGFeRD."
        result.err shouldContain
            "Das XML in factur-x.xml folgt urn:factur-x.eu:1p0:basicwl und nicht der EN 16931 (urn:cen.eu:en16931:2017)"
        result.err shouldNotContain "ist ungültig"
        result.err shouldNotContain "verletzt:"
    }

    test("eine Datei, die kein PDF ist, bricht mit einer Meldung ab") {
        val result = runProgram("xml", "src/test/resources/kein-pdf.pdf")

        result.exitCode shouldBe 1
        result.out.size shouldBe 0
        result.err shouldContain "kein-pdf.pdf ist kein lesbares PDF"
    }

    test("Rundreise: erzeuge --zugferd setzt, xml packt genau die Bytes aus, die CiiWriter schrieb").config(tags = setOf(Toolchain)) {
        val invoiceFile = Path.of("src/test/resources/rechnung.yaml")
        val sellerConfig = Path.of("src/test/resources/stammdaten.yaml")
        val target = dir.resolve("rundreise.pdf")

        val generated = runProgram("erzeuge", "--zugferd", invoiceFile.toString(), "-s", sellerConfig.toString(), "-o", target.toString())
        withClue(generated.err) { generated.exitCode shouldBe 0 }
        val result = runProgram("xml", target.toString())

        val written =
            CiiWriter
                .write(CalculatedInvoice(YamlLoader.loadInvoice(invoiceFile), YamlLoader.loadSellerConfig(sellerConfig)).accepted())
                .toByteArray()
        withClue(result.err) { result.exitCode shouldBe 0 }
        result.out.contentEquals(written) shouldBe true
    }
})

/** Was ein Aufruf hinterlässt, stdout als Bytes: Um genau die geht es. */
private class Run(
    val exitCode: Int,
    val out: ByteArray,
    val err: String,
)

/** Ruft das Programm über [execute] auf, mit den Werkzeugen des Betriebs. */
private fun runProgram(vararg args: String): Run {
    val out = ByteArrayOutputStream()
    val err = ByteArrayOutputStream()
    val exitCode = execute(arrayOf(*args), PrintStream(out, true, UTF_8), PrintStream(err, true, UTF_8))
    return Run(exitCode, out.toByteArray(), err.toString(UTF_8))
}
