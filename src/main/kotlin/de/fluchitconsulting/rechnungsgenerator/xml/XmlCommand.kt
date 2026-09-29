package de.fluchitconsulting.rechnungsgenerator.xml

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.path
import de.fluchitconsulting.rechnungsgenerator.toolchain.BuiltInPdfAValidator
import de.fluchitconsulting.rechnungsgenerator.toolchain.EInvoiceValidationResult
import de.fluchitconsulting.rechnungsgenerator.toolchain.EInvoiceValidator
import org.apache.pdfbox.Loader
import java.io.IOException
import java.io.OutputStream

/**
 * `rechnungsgenerator xml`: prüft eine E-Rechnung und gibt ihr XML auf stdout aus (#380, ADR-0023). Das XML geht
 * als Bytes auf [standardOutput] und nicht über `echo`, das Text schreibt und eine andere Kodierung veränderte.
 */
class XmlCommand(
    private val standardOutput: OutputStream = System.out,
) : CoreCliktCommand(name = "xml") {
    override fun help(context: Context) = "Prüft, ob ein PDF eine gültige E-Rechnung nach ZUGFeRD ist, und gibt ihr XML aus."

    private val pdf by argument("rechnung.pdf", help = "Die E-Rechnung").path(mustExist = true, canBeDir = false)

    override fun run() {
        val (identification, attachments) =
            try {
                Loader.loadPDF(pdf.toFile()).use { PdfAIdentification.of(it) to EInvoiceAttachment.allIn(it) }
            } catch (e: IOException) {
                throw CliktError("$pdf ist kein lesbares PDF: ${e.message}")
            }

        val checked = attachments.singleOrNull()?.let { it to EInvoiceValidator.validate(it.xml) }
        val identificationReason = identificationReason(identification)
        val pdfAViolations =
            identification
                ?.profile
                ?.takeIf { identificationReason == null }
                ?.let { BuiltInPdfAValidator.validate(pdf, it) }
                ?.takeUnless { it.valid }
        val otherReasons =
            listOfNotNull(
                identificationReason,
                attachmentReason(attachments),
                checked?.let { (attachment, _) -> specificationReason(attachment) },
                checked?.let { (attachment, validation) -> xmlReason(attachment, validation) },
            )
        val pdfAIsWarning = pdfAViolations != null && checked != null && pdfAErrorsAreWarnings(checked.first, otherReasons)

        // Alle Gründe beider Prüfer auf einmal: Wer eine Rechnung zurückweist, soll dem Absender alles nennen können.
        val reasons = listOfNotNull(pdfAViolations?.takeUnless { pdfAIsWarning }?.describe()) + otherReasons

        // Erst hier etwas auf stdout, sonst stünde bei einem Abbruch ein halbes Ergebnis in der Zieldatei.
        if (checked == null || reasons.isNotEmpty()) {
            throw CliktError("$pdf ist keine gültige E-Rechnung nach ZUGFeRD.\n" + reasons.joinToString("\n"))
        }
        val (attachment, validation) = checked
        if (pdfAIsWarning) {
            echo("Hinweis: $PDFA_WITHIN_GERMANY", err = true)
            echo("Hinweis: ${pdfAViolations.describe()}", err = true)
        }
        // Eine Warnung macht nicht ungültig; eine fremde Rechnung kann eine tragen, etwa PEPPOL-EN16931-R008.
        validation.warnings.forEach { echo("Hinweis: $it", err = true) }
        standardOutput.write(attachment.xml)
        standardOutput.flush()
    }

    /**
     * BR-FX-DE-03: Innerhalb Deutschlands ist das XML die Rechnung. Eng ausgelegt: nur bei einem PDF/A-3 mit bekannter
     * Stufe und ohne jeden anderen Grund (ADR-0023).
     */
    private fun pdfAErrorsAreWarnings(
        attachment: EInvoiceAttachment,
        otherReasons: List<String>,
    ): Boolean = otherReasons.isEmpty() && PartyCountries.of(attachment.xml).withinGermany

    /** Das Profil aus dem XMP und nicht aus einer Vorgabe: Ein gültiges PDF/A-3a fiele gegen 3u durch. */
    private fun identificationReason(identification: PdfAIdentification?): String? {
        val profile = identification?.profile?.takeIf { it.part == 3 }
        return when {
            identification == null -> {
                "Es gibt sich in seinem XMP nicht als PDF/A aus. $PDFA_3"
            }
            // Bei Teil 3 liegt es an der Stufe; „eine E-Rechnung ist ein PDF/A-3“ widerspräche hier der Angabe (#386).
            profile == null && identification.part == "3" -> {
                val claimed =
                    identification.conformance
                        ?.let { "Es gibt sich als ${identification.label} aus, eine Konformitätsstufe, die PDF/A-3 nicht kennt." }
                        ?: "Es gibt sich als PDF/A-3 aus, nennt aber keine Konformitätsstufe."
                "$claimed $PDFA_3_LEVELS"
            }
            profile == null -> {
                "Es gibt sich als ${identification.label} aus. $PDFA_3"
            }
            else -> {
                null
            }
        }
    }

    private fun attachmentReason(attachments: List<EInvoiceAttachment>): String? =
        when (attachments.size) {
            0 -> "Es trägt kein XML einer E-Rechnung, gesucht unter ${EInvoiceAttachment.NAMES.joinToString(", ")}."
            1 -> null
            else -> "Es trägt mehr als ein XML einer E-Rechnung (${attachments.joinToString(
                ", ",
            ) { it.name }}) und sagt nicht, welches gilt."
        }

    private fun specificationReason(attachment: EInvoiceAttachment): String? =
        SpecificationIdentifier
            .of(attachment.xml)
            ?.takeUnless { it.isEn16931 }
            ?.let {
                "Das XML in ${attachment.name} folgt ${it.value} und nicht der EN 16931 (${SpecificationIdentifier.EN_16931}); " +
                    "eine E-Rechnung nach §14 UStG ist es damit nicht."
            }

    private fun xmlReason(
        attachment: EInvoiceAttachment,
        validation: EInvoiceValidationResult,
    ): String? =
        validation
            .takeUnless { it.valid }
            ?.let { "Das XML in ${attachment.name} ist ungültig:\n" + it.errors.joinToString("\n") { error -> "  $error" } }

    private companion object {
        private const val PDFA_3 = "Eine E-Rechnung nach ZUGFeRD ist ein PDF/A-3."

        // Die drei Stufen, die ISO 19005-3 kennt.
        private const val PDFA_3_LEVELS = "Eine E-Rechnung nach ZUGFeRD ist ein PDF/A-3a, -3b oder -3u."

        private const val PDFA_WITHIN_GERMANY =
            "Das PDF/A ist nicht gültig. Bei einer Rechnung innerhalb Deutschlands gilt das XML (BR-FX-DE-03); " +
                "der Empfänger kann eine berichtigte Fassung verlangen."
    }
}
