package de.fluchitconsulting.rechnungsgenerator.skill

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.subcommands
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

/**
 * `rechnungsgenerator skill` (#550): hängt den rechnung-Skill aus dem Paket bei Claude Code ein, als Link und nie
 * als Kopie. Die Pfade sind gereicht statt gelesen, sonst schriebe der Testlauf ins Heimatverzeichnis.
 */
class SkillCommand(
    paths: SkillPaths = SkillPaths.of(),
) : CoreCliktCommand(name = "skill") {
    override val invokeWithoutSubcommand = true

    init {
        subcommands(InstallSkillCommand(paths), RemoveSkillCommand(paths), SkillStatusCommand(paths))
    }

    override fun help(context: Context) = "Hängt den rechnung-Skill bei Claude Code ein, nimmt ihn weg oder zeigt, ob er eingehängt ist."

    // Wie beim Hauptkommando: Clikts eigene Hilfe endete mit 0.
    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            throw PrintHelpMessage(currentContext, statusCode = 1)
        }
    }
}

private class InstallSkillCommand(
    private val paths: SkillPaths,
) : CoreCliktCommand(name = "installieren") {
    override fun help(context: Context) = "Legt den Link auf den Skill an; wiederholbar."

    override fun run() {
        if (!paths.source.resolve("SKILL.md").isRegularFile()) {
            throw CliktError(
                "Unter ${paths.source} liegt kein Skill. Er kommt mit dem Archiv des Programms, neben lib/; " +
                    "ein anderer Ort steht in ${SkillPaths.SOURCE_VARIABLE}.",
            )
        }

        when (val state = paths.state()) {
            LinkState.Absent -> {
                createLink()
            }
            // Kein --force: Was dort liegt, hat jemand anderes angelegt.
            is LinkState.Linked -> {
                if (state.target != paths.source) {
                    throw CliktError(
                        "${paths.link} ist schon ein Link, aber auf ${state.target}. " +
                            "Mit 'rechnungsgenerator skill entfernen' nimmst du ihn weg.",
                    )
                }
            }
            LinkState.Occupied -> {
                throw occupied(paths.link)
            }
        }

        echo(linkedLine(paths.link, paths.source))
    }

    private fun createLink() {
        try {
            paths.link.parent.createDirectories()
            Files.createSymbolicLink(paths.link, paths.source)
        } catch (e: FileAlreadyExistsException) {
            // Zwischen Prüfung und Anlegen entstanden.
            throw CliktError("${paths.link} gibt es inzwischen; angelegt wurde nichts.")
        } catch (e: IOException) {
            throw failed("Der Link ${paths.link} konnte nicht angelegt werden", e)
        }
    }
}

private class RemoveSkillCommand(
    private val paths: SkillPaths,
) : CoreCliktCommand(name = "entfernen") {
    override fun help(context: Context) = "Nimmt den Link wieder weg; einen echten Ordner löscht es nie."

    override fun run() {
        when (val state = paths.state()) {
            LinkState.Absent -> {
                echo(absentLine(paths.link))
            }
            // Jeden Link, gleich wohin: Nach `brew uninstall` zeigt der eigene ins Leere.
            is LinkState.Linked -> {
                try {
                    paths.link.deleteExisting()
                } catch (e: IOException) {
                    throw failed("Der Link ${paths.link} konnte nicht entfernt werden", e)
                }
                echo("Entfernt: ${paths.link} (zeigte auf ${state.target})")
            }
            LinkState.Occupied -> {
                throw occupied(paths.link)
            }
        }
    }
}

private class SkillStatusCommand(
    private val paths: SkillPaths,
) : CoreCliktCommand(name = "status") {
    override fun help(context: Context) = "Zeigt in einer Zeile, ob der Skill eingehängt ist und wohin der Link zeigt."

    // Immer 0: „Nicht eingehängt“ ist ein Ergebnis und kein Fehler.
    override fun run() {
        echo(
            when (val state = paths.state()) {
                LinkState.Absent -> absentLine(paths.link)
                is LinkState.Linked -> linkedLine(paths.link, state.target)
                LinkState.Occupied -> "Kein Link: ${paths.link} ist ein Ordner oder eine Datei"
            },
        )
    }
}

private fun linkedLine(
    link: Path,
    target: Path,
): String {
    // Relativ zum Ordner des Links, wie das Dateisystem ihn liest.
    val missing = if (link.resolveSibling(target).exists()) "" else " (das Ziel gibt es nicht)"
    return "Eingehängt: $link -> $target$missing"
}

private fun absentLine(link: Path) = "Nicht eingehängt: $link gibt es nicht"

private fun occupied(link: Path) =
    CliktError(
        "$link ist ein Ordner oder eine Datei und kein Link. Das Programm rührt es nicht an; " +
            "wer den Skill aus dem Paket will, räumt es selbst weg.",
    )

private fun failed(
    what: String,
    e: IOException,
): CliktError {
    val reason = if (e is FileSystemException) e.reason else e.message
    return CliktError(what + (reason?.let { ": $it" } ?: "."))
}
