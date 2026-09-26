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
 * `rechnungsgenerator xml`: prüft, ob ein PDF eine gültige E-Rechnung nach
 * ZUGFeRD ist, und gibt dann ihr XML auf stdout aus (#380).
 *
 * Benannt nach dem, was auf stdout steht, wie `abschlaege`. Ein Schalter an
 * `erzeuge` wäre ein Aufruf, der mal ein PDF ausgibt und mal etwas anderes
 * (ADR-0003).
 *
 * Wie beim Erzeugen hat jeder Prüfer eine Aufgabe (ADR-0023): veraPDF urteilt
 * über das PDF/A, und zwar gegen das Profil, als das es sich ausgibt, Mustang
 * über das XML. Angenommen wird jede E-Rechnung nach ZUGFeRD oder Factur-X,
 * nicht nur die eigene: Das Profil des XML erkennt Mustang am XML selbst.
 *
 * Das XML geht als Bytes auf [standardOutput] und nicht über `echo`: Das
 * schreibt Text, und ein XML in einer anderen Kodierung als UTF-8 käme
 * verändert heraus. Gereicht wird der Strom wie der Typesetter an `erzeuge`,
 * sonst ließe sich nicht prüfen, welche Bytes dort ankommen.
 *
 * Keine Stammdaten und kein `-s`: Gelesen wird keine Rechnungsdatei, also wird
 * auch keine Wurzel gesucht (ADR-0015). LuaLaTeX verlangt es nicht, und
 * `--externe-pdfa-pruefung` gibt es hier nicht, solange niemand danach fragt.
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

        // Alle Gründe beider Prüfer auf einmal, nicht nur der erste (ADR-0004):
        // Wer eine empfangene Rechnung zurückweist, soll dem Absender alles
        // nennen können.
        val reasons =
            listOfNotNull(
                pdfAReason(identification),
                attachmentReason(attachments),
                checked?.let { (attachment, validation) -> xmlReason(attachment, validation) },
            )

        // Erst hier und nur ohne Grund etwas auf stdout: Sonst stünde bei
        // einem Abbruch ein halbes Ergebnis in der Datei, in die umgeleitet
        // wird. Ohne genau einen Anhang steht immer schon ein Grund da; die
        // erste Bedingung sagt das dem Compiler.
        if (checked == null || reasons.isNotEmpty()) {
            throw CliktError("$pdf ist keine gültige E-Rechnung nach ZUGFeRD.\n" + reasons.joinToString("\n"))
        }
        val (attachment, validation) = checked
        // Eine Warnung macht das XML nicht ungültig; bei den eigenen
        // Rechnungen steht etwa PEPPOL-EN16931-R008 zum leeren
        // ApplicableHeaderTradeDelivery (ADR-0023).
        validation.warnings.forEach { echo("Hinweis: $it", err = true) }
        standardOutput.write(attachment.xml)
        standardOutput.flush()
    }

    /**
     * Das Profil kommt aus dem XMP und nicht aus einer Vorgabe: Ein PDF/A-3a
     * gegen 3u geprüft fiele durch, obwohl es gültig ist, und eines, das 3a
     * behauptet, soll auch 3a genügen.
     */
    private fun pdfAReason(identification: PdfAIdentification?): String? {
        val profile = identification?.profile?.takeIf { it.part == 3 }
        return when {
            identification == null -> "Es gibt sich in seinem XMP nicht als PDF/A aus. $PDFA_3"
            profile == null -> "Es gibt sich als ${identification.label} aus. $PDFA_3"
            else -> BuiltInPdfAValidator.validate(pdf, profile).takeUnless { it.valid }?.describe()
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

    private fun xmlReason(
        attachment: EInvoiceAttachment,
        validation: EInvoiceValidationResult,
    ): String? =
        validation
            .takeUnless { it.valid }
            ?.let { "Das XML in ${attachment.name} ist ungültig:\n" + it.errors.joinToString("\n") { error -> "  $error" } }

    private companion object {
        private const val PDFA_3 = "Eine E-Rechnung nach ZUGFeRD ist ein PDF/A-3."
    }
}
