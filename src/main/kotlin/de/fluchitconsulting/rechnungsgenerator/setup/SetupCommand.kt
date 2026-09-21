package de.fluchitconsulting.rechnungsgenerator.setup

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import de.fluchitconsulting.rechnungsgenerator.model.Root
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.writeText

/**
 * `rechnungsgenerator einrichten`: legt `.rechnungsgenerator/stammdaten.yaml`
 * im Arbeitsverzeichnis an und macht es damit zur Wurzel (ADR-0015).
 *
 * Ein Gerüst und kein `git init`: Es schreibt eine Datei, die danach ganz der
 * Benutzerin gehört. Das Programm merkt sich nichts und schreibt nie wieder in
 * den Ordner.
 *
 * Ohne Argument, im Arbeitsverzeichnis – ein `cd` davor kostet nichts, und ein
 * Argument lässt sich später ergänzen, aber nicht zurücknehmen. [workingDir]
 * ist trotzdem gereicht: Sonst ließe sich das Kommando nur prüfen, indem der
 * Testlauf in sein eigenes Projektverzeichnis schreibt.
 *
 * LuaLaTeX verlangt es nicht; `checkAvailable()` bleibt bei `erzeuge`.
 */
class SetupCommand(
    private val workingDir: Path = Path.of(""),
) : CoreCliktCommand(name = "einrichten") {
    override fun help(context: Context) =
        "Legt im Arbeitsverzeichnis ${Root.FOLDER}/${Root.SELLER_CONFIG} als Vorlage an."

    override fun run() {
        val folder = workingDir.resolve(Root.FOLDER)
        val target = folder.resolve(Root.SELLER_CONFIG)

        // Vorhandenes wird nie überschrieben, und es gibt kein --force: In
        // dieser Datei steht, was jemand von Hand eingetragen hat.
        if (target.exists()) {
            throw CliktError("Die Datei $target gibt es schon; eingerichtet ist hier also bereits.")
        }
        // Den Ordner gibt es womöglich schon, ohne die Datei – das ist der
        // Ausweg aus dem Abbruch "Ordner ohne Stammdaten". Was sonst darin
        // liegt, bleibt unberührt.
        if (folder.exists() && !folder.isDirectory()) {
            throw CliktError("$folder ist eine Datei und kein Ordner; die Wurzel lässt sich hier nicht einrichten.")
        }

        try {
            folder.createDirectories()
            target.writeText(SellerConfigTemplate.text)
        } catch (e: IOException) {
            throw CliktError("Die Datei $target konnte nicht geschrieben werden: ${e.message}")
        }

        // Auf stdout allein der Pfad, damit $(rechnungsgenerator einrichten)
        // ihn liefert. Kein Satz "als Nächstes …": Das sagen die Kommentare
        // der Vorlage, und dort liest man es in dem Moment, in dem man es
        // braucht.
        echo("$target")

        // Verschachteln ist der vorgesehene Weg für einen zweiten
        // Rechnungssteller, aus Versehen aber eine Falle. Deshalb ein Hinweis
        // und kein Abbruch – dieselbe Unterscheidung wie bei den Befunden.
        Root.above(workingDir)?.let {
            echo("Hinweis: Oberhalb liegt schon eine Wurzel ($it). Für Rechnungen ab hier gilt von nun an die neue.", err = true)
        }
    }
}
