package de.fluchitconsulting.rechnung

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import de.fluchitconsulting.rechnung.toolchain.ToolchainException
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name
import kotlin.system.exitProcess

// Einstiegspunkt. Eine Top-Level-Funktion `main` genügt in Kotlin – keine Klasse
// nötig. Die JVM sieht sie als statische Methode der generierten Klasse "MainKt"
// (daher der Eintrag mainClass in build.gradle.kts).
//
// Bewusst parse() statt des bequemeren main() von Clikt: Dessen main() fängt die
// Fehler zwar ab und gibt sie aus, beendet den Prozess aber mit Code 0.
// Nachgemessen: Ein Tippfehler in der YAML meldete "Exitcode: 0", ein Skript
// ringsherum hätte den Fehlschlag also nicht bemerkt. Hier bestimmt der
// statusCode des CliktError den Exitcode – 0 nur bei --help und Erfolg.
fun main(args: Array<String>) {
    val command = RechnungCommand()
    try {
        command.parse(args)
    } catch (e: CliktError) {
        command.echoFormattedHelp(e)
        exitProcess(e.statusCode)
    }
}

class RechnungCommand : CoreCliktCommand(name = "rechnung") {
    override fun help(context: Context) =
        "Erzeugt aus einer YAML-Rechnung ein geprüftes PDF/A-1b."

    private val invoiceFile by argument("rechnung.yaml", help = "Die Rechnungsdatei").path(mustExist = true, canBeDir = false)

    private val configFile by option("-s", "--stammdaten", help = "Stammdaten des Rechnungsstellers")
        .path(mustExist = true, canBeDir = false)
        .default(Path.of("stammdaten.yaml"))

    private val outputFile by option("-o", "--ausgabe", help = "Zieldatei; Vorgabe ist der Name aus der Rechnungsnummer")
        .path(canBeDir = false)

    private val keepWorkFiles by option("--arbeitsdateien-behalten", help = "Das .tex und die LaTeX-Hilfsdateien nicht löschen")
        .flag()

    override fun run() {
        // CliktError statt einer durchgereichten Exception: Clikt gibt die
        // Meldung aus und beendet mit Exitcode 1, ohne Stacktrace. Ein
        // Tippfehler in der YAML soll nicht wie ein Programmabsturz aussehen.
        try {
            erzeuge()
        } catch (e: ToolchainException) {
            throw CliktError(e.message)
        } catch (e: InvoiceValidationException) {
            throw CliktError(e.message)
        } catch (e: SerializationException) {
            throw CliktError("Die Eingabedateien konnten nicht gelesen werden:\n${e.message}")
        } catch (e: IOException) {
            // Etwa ein Zielpfad, der nicht beschreibbar ist. Ohne diesen Zweig
            // beendet sich das Programm mit einem Stacktrace, was wie ein
            // Programmfehler aussieht statt wie ein falsches Argument.
            throw CliktError("Die Datei konnte nicht geschrieben werden: ${e.message}")
        }
    }

    private fun erzeuge() {
        // Erst die Werkzeuge prüfen: Ein Abbruch mitten im Satz wäre schwerer zu deuten.
        InvoiceGenerator.checkTools()

        val config = YamlLoader.loadSellerConfig(configFile)
        val invoice = YamlLoader.loadInvoice(invoiceFile)

        val workDir = createTempDirectory("rechnungsgenerator")
        try {
            val result = InvoiceGenerator.generate(invoice, config, workDir)
            val target = outputFile ?: Path.of(result.pdf.name)
            result.pdf.copyTo(target, overwrite = true)

            echo("$target")
            echo(result.validation.describe())
            // Hinweise nach dem Ergebnis, damit sie nicht wie ein Fehler wirken –
            // die Rechnung ist ja entstanden. Sie betreffen die eigene Ablage.
            result.hints.forEach { echo("Hinweis: $it") }
            if (keepWorkFiles) echo("Arbeitsdateien: $workDir")
            // Ein ungültiges PDF/A ist kein Erfolg: Exitcode 1, damit ein Skript
            // ringsherum es merkt.
            if (!result.validation.valid) throw ToolchainException("Das erzeugte PDF ist kein gültiges PDF/A-1b.")
        } finally {
            if (!keepWorkFiles) workDir.toFile().deleteRecursively()
        }
    }
}
