package de.fluchitconsulting.rechnungsgenerator.skill

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isSymbolicLink

/** Der Skill im Paket und die Stelle, an der Claude Code ihn sucht (#550). */
class SkillPaths(
    val source: Path,
    val link: Path,
) {
    /** Unterschieden wird zwischen Link und allem anderen, nie über das Ziel hinweg: Ein Link ins Leere bleibt ein Link. */
    fun state(): LinkState =
        when {
            link.isSymbolicLink() -> LinkState.Linked(Files.readSymbolicLink(link))
            link.exists(LinkOption.NOFOLLOW_LINKS) -> LinkState.Occupied
            else -> LinkState.Absent
        }

    companion object {
        const val SOURCE_VARIABLE = "RECHNUNGSGENERATOR_SKILL"
        const val CONFIG_DIR_VARIABLE = "CLAUDE_CONFIG_DIR"

        fun of(
            env: (String) -> String? = System::getenv,
            home: Path = Path.of(System.getProperty("user.home")),
            bundled: () -> Path = ::skillNextToLib,
        ): SkillPaths {
            // Nicht toRealPath(): Die Formel reicht einen Pfad über opt/ herein, der den Wechsel der Fassung
            // überlebt. Aufgelöst zeigte der Link in Cellar/<fassung> und risse beim nächsten Upgrade ab.
            val source = env(SOURCE_VARIABLE)?.takeUnless { it.isBlank() }?.let { Path.of(it) } ?: bundled()
            val configDir = env(CONFIG_DIR_VARIABLE)?.takeUnless { it.isBlank() }?.let { Path.of(it) } ?: home.resolve(".claude")
            return SkillPaths(
                source = source.toAbsolutePath().normalize(),
                link =
                    configDir
                        .toAbsolutePath()
                        .normalize()
                        .resolve("skills")
                        .resolve("rechnung"),
            )
        }

        /** `skill/rechnung` neben `lib/`, wie im Archiv und nach `installDist`. */
        private fun skillNextToLib(): Path {
            val jar =
                Path.of(
                    SkillPaths::class.java.protectionDomain.codeSource.location
                        .toURI(),
                )
            return jar.parent.resolveSibling("skill").resolve("rechnung")
        }
    }
}

sealed interface LinkState {
    data object Absent : LinkState

    data class Linked(val target: Path) : LinkState

    /** Ein Ordner oder eine Datei: jemandes eigener Skill oder eine angepasste Kopie. */
    data object Occupied : LinkState
}
