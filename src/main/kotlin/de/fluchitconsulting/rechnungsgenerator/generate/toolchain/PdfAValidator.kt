package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

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
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.absolutePathString

/**
 * Wogegen geprüft wird: PDF/A-1b ohne E-Rechnung, PDF/A-3u mit ihr (ADR-0023).
 *
 * [flavour] und [cliFlag] nennen dasselbe Profil für die beiden Wege zu
 * veraPDF, die Bibliothek und das Programm.
 */
enum class PdfAProfile(
    val label: String,
    val flavour: PDFAFlavour,
    val cliFlag: String,
) {
    PDFA_1B("PDF/A-1b", PDFAFlavour.PDFA_1_B, "1b"),
    PDFA_3U("PDF/A-3u", PDFAFlavour.PDFA_3_U, "3u"),
}

/**
 * Ein von veraPDF gemeldeter Regelverstoß. Welcher Teil der Norm, sagt
 * [specification] so, wie veraPDF es meldet, etwa "ISO 19005-1:2005".
 */
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

/**
 * Die PDF/A-Prüfung: Ist das gesetzte Dokument ein gültiges PDF/A im
 * verlangten [PdfAProfile]?
 *
 * Nicht zu verwechseln mit der *Prüfung* der Rechnung, die Befunde liefert und
 * über Annahme oder Ablehnung entscheidet (ADR-0004). Anderer Gegenstand,
 * anderes Ergebnis, anderer Zeitpunkt – siehe das Glossar in `CONTEXT.md`.
 *
 * Zwei Adapter, wie beim [Typesetter] eine echte Naht und keine gedachte
 * (ADR-0014): [BuiltInPdfAValidator] prüft in derselben JVM, [ExternalPdfAValidator]
 * ruft das Programm `verapdf` auf. Eingebaut ist die Vorgabe; der externe
 * Prüfer urteilt dafür unabhängig – getrennt installiert, getrennt versioniert.
 */
interface PdfAValidator {
    /**
     * Prüft, ob der Prüfer einsatzbereit ist. Gehört zum Adapter und nicht
     * daneben: Der eingebaute braucht nichts, der externe ein installiertes
     * Programm. Deshalb verlangt der Generator veraPDF beim Start nur, wenn
     * der externe Prüfer gewählt ist.
     */
    fun checkAvailable()

    /**
     * Urteilt über [pdf]: gültig oder nicht, und wenn nicht, welche Regeln es
     * verletzt. Ein nicht konformes PDF ist ein Ergebnis und keine Ausnahme.
     *
     * Kann der Prüfer gar nicht urteilen – die Datei ist kein lesbares PDF,
     * das Werkzeug bricht mitten in der Prüfung ab –, wirft er
     * [ToolchainException]. Die Zusage steht hier und nicht in einem Adapter,
     * weil `execute()` genau diese Ausnahme als Meldung ausgibt; jede andere
     * verließe `main` als Stacktrace (#294).
     */
    fun validate(
        pdf: Path,
        profile: PdfAProfile,
    ): ValidationResult
}

/**
 * Prüft in derselben JVM, über `org.verapdf:validation-model-jakarta`.
 *
 * Der Grund ist die Startzeit: Das Programm `verapdf` braucht für ein einzelnes
 * Dokument rund 6,4 s, davon 6,2 s für den Start seines CLI-Wrappers. Die
 * Prüfung selbst dauert rund 50 ms, und genau die bleibt hier übrig (#226).
 *
 * Jedes Validierungsprofil wird einmal geladen und dann behalten: Es zu lesen
 * kostet 0,7 s, jede Prüfung danach Bruchteile davon.
 */
object BuiltInPdfAValidator : PdfAValidator {
    // by lazy statt im Initialisierer: Ein Profil wird erst gelesen, wenn
    // wirklich danach geprüft wird – ein Lauf, der vorher abbricht, zahlt es
    // nicht, und ein Lauf ohne E-Rechnung liest das für PDF/A-3u nie.
    //
    // Ohne Argument ist das `LazyThreadSafetyMode.SYNCHRONIZED`, und das soll es
    // bleiben: `initialise()` setzt globalen statischen Zustand in veraPDF, und
    // ein Profil ist danach ein geteilter Wert. Hier läuft heute nur ein Thread,
    // die Zusage kostet aber nichts – mit `NONE` wäre sie still weg. Benutzt wird
    // ein Profil danach nur lesend (`getRuleByRuleId`), und das bewacht veraPDF
    // selbst: Seine eigene faule Befüllung steht samt Prüfung in einem
    // `synchronized`-Block.
    private val initialised by lazy { VeraGreenfieldFoundryProvider.initialise() }

    private val validationProfiles: Map<PdfAProfile, Lazy<ValidationProfile>> =
        PdfAProfile.entries.associateWith { profile ->
            lazy {
                initialised
                Profiles.getVeraProfileDirectory().getValidationProfileByFlavour(profile.flavour)
            }
        }

    /** Nichts zu prüfen: Der Prüfer liegt im Klassenpfad. */
    override fun checkAvailable() = Unit

    override fun validate(
        pdf: Path,
        profile: PdfAProfile,
    ): ValidationResult {
        // Erzwingt das Laden, bevor die Foundry gefragt wird.
        val validationProfile = validationProfiles.getValue(profile).value
        val validator = Foundries.defaultInstance().createValidator(profile.flavour, false)
        // Gefangen wird VeraPDFException und nicht mehr: Sie ist die gemeinsame
        // Oberklasse dessen, was beide Aufrufe deklarieren, also genau "konnte
        // nicht urteilen". Dazu die IOException, die `use` beim Schließen des
        // Parsers werfen kann (#300). Eine RuntimeException aus der Bibliothek
        // ist ein Programmfehler, und den soll ein Stacktrace zeigen, keine
        // Meldung.
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
                        // Die Meldung steht im Profil, nicht an der Aussage:
                        // getErrorMessage() der Aussage ist null, den Wortlaut
                        // trägt die Regel.
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

    /**
     * Alle Meldungen der Ursachenkette, von außen nach innen. veraPDF sagt
     * außen nur, *dass* es scheiterte ("Couldn't parse stream"), und legt das
     * *Warum* in die Ursache ("End of file is reached"). `execute()` gibt die
     * Ursache nicht aus, also muss sie in die Meldung.
     */
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

/**
 * Prüft mit dem Programm `verapdf` als eigenem Prozess.
 *
 * veraPDF unterscheidet über den Exitcode: 0 = konform, 1 = nicht konform,
 * alles andere = das Werkzeug selbst kam nicht klar (etwa Datei nicht lesbar).
 * Nur der dritte Fall ist ein Fehler unsererseits; ein nicht konformes PDF ist
 * ein gültiges Prüfergebnis und wird als solches zurückgegeben.
 *
 * Langsamer als der eingebaute Prüfer und trotzdem behalten: Er ist getrennt
 * installiert und getrennt versioniert und urteilt damit unabhängig von dem
 * Stück Software, das der Generator selbst mitbringt (ADR-0014).
 */
class ExternalPdfAValidator(private val runner: ProcessRunner) : PdfAValidator {
    private companion object {
        private const val PROGRAM = "verapdf"
        private const val EXIT_COMPLIANT = 0
        private const val EXIT_NOT_COMPLIANT = 1
    }

    override fun checkAvailable() = runner.requireAll(PROGRAM to "brew install verapdf")

    override fun validate(
        pdf: Path,
        profile: PdfAProfile,
    ): ValidationResult {
        val result =
            runner.run(
                PROGRAM,
                listOf("--format", "xml", "-f", profile.cliFlag, pdf.absolutePathString()),
                pdf.parent,
            )
        if (result.exitCode !in listOf(EXIT_COMPLIANT, EXIT_NOT_COMPLIANT)) {
            throw ToolchainException("$PROGRAM brach mit Exitcode ${result.exitCode} ab:\n${result.output.take(800)}")
        }
        return ValidationResult(
            profile = profile,
            valid = result.exitCode == EXIT_COMPLIANT,
            violations = parseViolations(extractXml(result.standardOutput)),
        )
    }

    /**
     * Schneidet weg, was vor dem XML steht.
     *
     * Gelesen wird allein stdout, und dort steht der Bericht für sich: Sein
     * Protokoll schreibt veraPDF auf stderr, die JVM ihre Warnungen ebenfalls
     * (#227). Nötig ist das Abschneiden damit nicht – es bleibt als
     * Sicherung, weil ein fremdes Programm jederzeit etwas auf stdout legen
     * kann und der Parser sonst mit "Content is not allowed in prolog"
     * abbräche. Drei Zeilen gegen eine Ausnahme, die der Benutzerin als
     * Absturz erschiene.
     *
     * Was *mitten* im Bericht steht, fängt es nicht – genau daran ist #227
     * gescheitert. Dagegen hilft nur, die Kanäle getrennt zu lassen.
     */
    private fun extractXml(output: String): String {
        val start = output.indexOf("<?xml")
        if (start < 0) throw ToolchainException("verapdf lieferte keinen XML-Bericht:\n${output.take(800)}")
        return output.substring(start)
    }

    /** Die verletzten Regeln aus dem Bericht; bestandene Regeln stehen dort auch drin. */
    private fun parseViolations(xml: String): List<PdfAViolation> {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                // Externe Entitäten abschalten: Standard-Hygiene beim XML-Parsen,
                // auch wenn die Eingabe hier aus dem eigenen Prozess stammt.
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }
        val document = factory.newDocumentBuilder().parse(xml.byteInputStream())
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

    // Kleine private Extension: holt den Text des ersten Kindelements mit diesem
    // Namen. Erspart an vier Stellen dieselbe Null- und Längenprüfung.
    private fun Element.childText(tag: String): String =
        getElementsByTagName(tag)
            .item(0)
            ?.textContent
            ?.trim()
            .orEmpty()
}
