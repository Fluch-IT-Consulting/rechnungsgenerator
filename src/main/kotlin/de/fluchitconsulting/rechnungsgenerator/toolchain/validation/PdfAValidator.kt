package de.fluchitconsulting.rechnungsgenerator.toolchain.validation

import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import de.fluchitconsulting.rechnungsgenerator.toolchain.XmlParser
import de.fluchitconsulting.rechnungsgenerator.toolchain.process.VeraPdf
import org.verapdf.core.VeraPDFException
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider
import org.verapdf.pdfa.Foundries
import org.verapdf.pdfa.flavours.PDFAFlavour
import org.verapdf.pdfa.results.TestAssertion
import org.verapdf.pdfa.validation.profiles.Profiles
import org.verapdf.pdfa.validation.profiles.ValidationProfile
import org.w3c.dom.Element
import java.io.IOException
import java.nio.file.Path

/**
 * `erzeuge` setzt 1b oder 3u, `xml` prüft gegen das Profil, als das sich die Datei im XMP ausgibt (#380).
 * [flavour] nennt es der Bibliothek, [cliFlag] dem Programm.
 */
enum class PdfAProfile(
    val part: Int,
    val conformance: String,
    val flavour: PDFAFlavour,
) {
    PDFA_1B(1, "B", PDFAFlavour.PDFA_1_B),
    PDFA_3A(3, "A", PDFAFlavour.PDFA_3_A),
    PDFA_3B(3, "B", PDFAFlavour.PDFA_3_B),
    PDFA_3U(3, "U", PDFAFlavour.PDFA_3_U),
    ;

    val label: String get() = "PDF/A-$part${conformance.lowercase()}"

    val cliFlag: String get() = "$part${conformance.lowercase()}"
}

data class PdfAViolation(
    val specification: String,
    val clause: String,
    val testNumber: String,
    val description: String,
    val errorMessage: String,
) {
    override fun toString(): String = "$specification $clause Test $testNumber: $description\n    $errorMessage"
}

data class ValidationResult(
    val profile: PdfAProfile,
    val valid: Boolean,
    val violations: List<PdfAViolation>,
) {
    fun describe(): String =
        if (valid) {
            "${profile.label}: gültig"
        } else {
            "${profile.label} verletzt:\n" + violations.joinToString("\n") { "  $it" }
        }
}

/** Die PDF/A-Prüfung, nicht die Prüfung der Rechnung (ADR-0004). Eingebaut ist die Vorgabe, extern das unabhängige Urteil (ADR-0014). */
interface PdfAValidator {
    fun checkAvailable()

    /**
     * Ein nicht konformes PDF ist ein Ergebnis und keine Ausnahme. Kann der Prüfer nicht urteilen, wirft er
     * [ToolchainException]; jede andere Ausnahme verließe `main` als Stacktrace (#294).
     */
    fun validate(
        pdf: Path,
        profile: PdfAProfile,
    ): ValidationResult
}

/**
 * In derselben JVM wegen der Startzeit: Das Programm braucht 6,2 s für den Start, die Prüfung 50 ms (#226). Ein
 * Profil zu laden kostet 0,7 s, deshalb bleibt es geladen.
 */
object BuiltInPdfAValidator : PdfAValidator {
    // SYNCHRONIZED, die Vorgabe von lazy, soll bleiben: `initialise()` setzt globalen Zustand in veraPDF.
    private val initialised by lazy { VeraGreenfieldFoundryProvider.initialise() }

    private val validationProfiles: Map<PdfAProfile, Lazy<ValidationProfile>> =
        PdfAProfile.entries.associateWith { profile ->
            lazy {
                initialised
                Profiles.getVeraProfileDirectory().getValidationProfileByFlavour(profile.flavour)
            }
        }

    override fun checkAvailable() = Unit

    override fun validate(
        pdf: Path,
        profile: PdfAProfile,
    ): ValidationResult {
        // Erzwingt das Laden, bevor die Foundry gefragt wird.
        val validationProfile = validationProfiles.getValue(profile).value
        val validator = Foundries.defaultInstance().createValidator(profile.flavour, false)
        // VeraPDFException heißt „konnte nicht urteilen“, dazu die IOException aus `use` (#300). Eine
        // RuntimeException ist ein Programmfehler und soll als Stacktrace erscheinen.
        val result =
            try {
                Foundries.defaultInstance().createParser(pdf.toFile(), profile.flavour).use { parser ->
                    validator.validate(parser)
                }
            } catch (e: VeraPDFException) {
                throw cannotJudge(pdf, e)
            } catch (e: IOException) {
                throw cannotJudge(pdf, e)
            }
        return ValidationResult(
            profile = profile,
            valid = result.isCompliant,
            violations =
                result.testAssertions
                    .filter { it.status == TestAssertion.Status.FAILED }
                    .map { assertion ->
                        // getErrorMessage() der Aussage ist null, den Wortlaut trägt die Regel im Profil.
                        val rule = validationProfile.getRuleByRuleId(assertion.ruleId)
                        PdfAViolation(
                            specification = assertion.ruleId.specification.id,
                            clause = assertion.ruleId.clause,
                            testNumber = assertion.ruleId.testNumber.toString(),
                            description = rule?.description.orEmpty(),
                            errorMessage = rule?.error?.message.orEmpty(),
                        )
                    },
        )
    }

    /** veraPDF legt das Warum in die Ursache, und `execute()` gibt die nicht aus. */
    private fun cannotJudge(
        pdf: Path,
        e: Exception,
    ) = ToolchainException("veraPDF konnte $pdf nicht prüfen: ${reasons(e)}", e)

    private fun reasons(e: Throwable): String =
        generateSequence(e) { it.cause }
            .mapNotNull { it.message }
            .distinct()
            .joinToString(": ")
}

/** Liest die Verstöße aus dem Bericht von [VeraPdf]; Aufruf und Exitcodes kennt der Adapter (ADR-0029). */
class ExternalPdfAValidator(private val veraPdf: VeraPdf) : PdfAValidator {
    override fun checkAvailable() = veraPdf.checkAvailable()

    override fun validate(
        pdf: Path,
        profile: PdfAProfile,
    ): ValidationResult {
        val report = veraPdf.validate(pdf, profile.cliFlag)
        return ValidationResult(
            profile = profile,
            valid = report.compliant,
            violations = parseViolations(report.xml),
        )
    }

    /** Der Bericht nennt auch die bestandenen Regeln. */
    private fun parseViolations(xml: String): List<PdfAViolation> {
        val document = XmlParser.parse(xml.toByteArray())
        val rules = document.getElementsByTagName("rule")
        return (0 until rules.length)
            .map { rules.item(it) as Element }
            .filter { it.getAttribute("status") == "failed" }
            .map { rule ->
                PdfAViolation(
                    specification = rule.getAttribute("specification"),
                    clause = rule.getAttribute("clause"),
                    testNumber = rule.getAttribute("testNumber"),
                    description = rule.childText("description"),
                    errorMessage = rule.childText("errorMessage"),
                )
            }
    }

    private fun Element.childText(tag: String): String =
        getElementsByTagName(tag)
            .item(0)
            ?.textContent
            ?.trim()
            .orEmpty()
}
