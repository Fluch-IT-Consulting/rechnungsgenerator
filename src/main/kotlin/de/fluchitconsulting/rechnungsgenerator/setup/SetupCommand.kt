package de.fluchitconsulting.rechnungsgenerator.setup

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import de.fluchitconsulting.rechnungsgenerator.core.model.Root
import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.writeText

/**
 * `rechnungsgenerator einrichten` (ADR-0015): ein Gerüst, danach schreibt das Programm nie wieder in den Ordner.
 * Ohne Argument, denn ein Argument lässt sich ergänzen, aber nicht zurücknehmen.
 */
class SetupCommand(
    private val workingDir: Path = Path.of(""),
) : CoreCliktCommand(name = "einrichten") {
    override fun help(context: Context) =
        "Legt im Arbeitsverzeichnis ${Root.FOLDER}/${Root.SELLER_CONFIG} als Vorlage an."

    override fun run() {
        val folder = workingDir.resolve(Root.FOLDER)
        val target = folder.resolve(Root.SELLER_CONFIG)

        // Nie überschrieben, kein --force: In der Datei steht, was jemand von Hand eingetragen hat.
        if (target.exists()) {
            throw CliktError("Die Datei $target gibt es schon; eingerichtet ist hier also bereits.")
        }
        // Den Ordner gibt es womöglich schon, ohne die Datei: der Ausweg aus „Ordner ohne Stammdaten“.
        if (folder.exists() && !folder.isDirectory()) {
            throw CliktError("$folder ist eine Datei und kein Ordner; die Wurzel lässt sich hier nicht einrichten.")
        }

        // Derselbe Wortlaut wie bei erzeuge, gehalten von einem Test (#327).
        try {
            folder.createDirectories()
            target.writeText(SellerConfigTemplate.text)
        } catch (e: IOException) {
            val reason = if (e is FileSystemException) e.reason else e.message
            throw CliktError("Die Datei $target konnte nicht geschrieben werden" + (reason?.let { ": $it" } ?: "."))
        }

        // Auf stdout allein der Pfad, damit $(rechnungsgenerator einrichten) ihn liefert.
        echo("$target")

        // Verschachteln ist der vorgesehene Weg für einen zweiten Rechnungssteller, deshalb ein Hinweis und kein Abbruch.
        Root.above(workingDir)?.let {
            echo("Hinweis: Oberhalb liegt schon eine Wurzel ($it). Für Rechnungen ab hier gilt von nun an die neue.", err = true)
        }
    }
}
