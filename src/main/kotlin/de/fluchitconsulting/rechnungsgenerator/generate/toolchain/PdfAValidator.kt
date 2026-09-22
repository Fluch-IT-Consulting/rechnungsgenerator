package de.fluchitconsulting.rechnungsgenerator.generate.toolchain

import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider
import org.verapdf.pdfa.Foundries
import org.verapdf.pdfa.flavours.PDFAFlavour
import org.verapdf.pdfa.results.TestAssertion
import org.verapdf.pdfa.validation.profiles.Profiles
import org.w3c.dom.Element
import java.nio.file.Path
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.absolutePathString

/** Ein von veraPDF gemeldeter Regelverstoß. */
data class PdfAViolation(
    val clause: String,
    val testNumber: String,
    val description: String,
    val errorMessage: String,
) {
    override fun toString(): String = "ISO 19005-1 $clause Test $testNumber: $description\n    $errorMessage"
}

data class ValidationResult(val valid: Boolean, val violations: List<PdfAViolation>) {
    fun describe(): String =
        if (valid) "PDF/A-1b: gültig" else "PDF/A-1b verletzt:\n" + violations.joinToString("\n") { "  $it" }
}

/**
 * Die PDF/A-Prüfung: Ist das gesetzte Dokument ein gültiges PDF/A-1b?
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
     * Programm. Deshalb verlangt der Generator veraPDF nicht mehr beim Start.
     */
    fun checkAvailable()

    fun validate(pdf: Path): ValidationResult
}

/**
 * Prüft in derselben JVM, über `org.verapdf:validation-model`.
 *
 * Der Grund ist die Startzeit: Das Programm `verapdf` braucht für ein einzelnes
 * Dokument rund 6,4 s, davon 6,2 s für den Start seines CLI-Wrappers. Die
 * Prüfung selbst dauert rund 50 ms, und genau die bleibt hier übrig (#226).
 *
 * Das Validierungsprofil wird einmal geladen und dann behalten: Es zu lesen
 * kostet 0,7 s, jede Prüfung danach Bruchteile davon.
 */
object BuiltInPdfAValidator : PdfAValidator {
    private val flavour = PDFAFlavour.PDFA_1_B

    // by lazy statt im Initialisierer: Das Profil wird erst gelesen, wenn
    // wirklich geprüft wird – ein Lauf, der vorher abbricht, zahlt es nicht.
    //
    // Ohne Argument ist das `LazyThreadSafetyMode.SYNCHRONIZED`, und das soll es
    // bleiben: `initialise()` setzt globalen statischen Zustand in veraPDF, und
    // das Profil ist danach ein geteilter Wert. Hier läuft heute nur ein Thread,
    // die Zusage kostet aber nichts – mit `NONE` wäre sie still weg. Benutzt wird
    // das Profil danach nur lesend (`getRuleByRuleId`), und das bewacht veraPDF
    // selbst: Seine eigene faule Befüllung steht samt Prüfung in einem
    // `synchronized`-Block.
    private val profile by lazy {
        VeraGreenfieldFoundryProvider.initialise()
        Profiles.getVeraProfileDirectory().getValidationProfileByFlavour(flavour)
    }

    /** Nichts zu prüfen: Der Prüfer liegt im Klassenpfad. */
    override fun checkAvailable() = Unit

    override fun validate(pdf: Path): ValidationResult {
        profile // erzwingt das Laden, bevor die Foundry gefragt wird
        val validator = Foundries.defaultInstance().createValidator(flavour, false)
        val result =
            Foundries.defaultInstance().createParser(pdf.toFile(), flavour).use { parser ->
                validator.validate(parser)
            }
        return ValidationResult(
            valid = result.isCompliant,
            violations =
                result.testAssertions
                    .filter { it.status == TestAssertion.Status.FAILED }
                    .map { assertion ->
                        // Die Meldung steht im Profil, nicht an der Aussage:
                        // getErrorMessage() der Aussage ist null, den Wortlaut
                        // trägt die Regel.
                        val rule = profile.getRuleByRuleId(assertion.ruleId)
                        PdfAViolation(
                            clause = assertion.ruleId.clause,
                            testNumber = assertion.ruleId.testNumber.toString(),
                            description = rule?.description.orEmpty(),
                            errorMessage = rule?.error?.message.orEmpty(),
                        )
                    },
        )
    }
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

    override fun validate(pdf: Path): ValidationResult {
        val result =
            runner.run(
                listOf(PROGRAM, "--format", "xml", "-f", "1b", pdf.absolutePathString()),
                pdf.parent,
            )
        if (result.exitCode !in listOf(EXIT_COMPLIANT, EXIT_NOT_COMPLIANT)) {
            throw ToolchainException("$PROGRAM brach mit Exitcode ${result.exitCode} ab:\n${result.output.take(800)}")
        }
        return ValidationResult(
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
