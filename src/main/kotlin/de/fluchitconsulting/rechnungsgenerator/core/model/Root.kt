package de.fluchitconsulting.rechnungsgenerator.core.model

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/** Das Verzeichnis mit dem Ordner `.rechnungsgenerator/` (ADR-0015). Privater Konstruktor: Eine Wurzel stammt aus der Suche. */
@JvmInline
value class Root private constructor(val directory: Path) {
    val sellerConfig: Path get() = directory.resolve(FOLDER).resolve(SELLER_CONFIG)

    override fun toString(): String = directory.toString()

    companion object {
        /** Allein der Ordner macht ein Verzeichnis zur Wurzel, eine lose Datei nicht. */
        const val FOLDER = ".rechnungsgenerator"

        const val SELLER_CONFIG = "stammdaten.yaml"

        /**
         * Ab dem Verzeichnis der Rechnungsdatei, nicht dem Arbeitsverzeichnis. `normalize()` und nicht `toRealPath()`:
         * Wer eine Rechnung in seine Ablage verlinkt, sucht von dort aus, wo der Link liegt.
         */
        fun of(invoiceFile: Path): Root {
            val start = invoiceFile.toAbsolutePath().normalize().parent
            for (directory in generateSequence(start) { it.parent }) {
                val folder = directory.resolve(FOLDER)
                if (!folder.exists()) continue
                // Halb eingerichtet bricht ab, statt weiter oben zu suchen: Sonst stünde der falsche Aussteller da.
                if (!folder.isDirectory()) {
                    throw InputFileException(
                        "$folder ist eine Datei und kein Ordner. Eine Wurzel gibt sich mit dem Ordner $FOLDER " +
                            "zu erkennen, und darin gehört die Datei $SELLER_CONFIG. $NOT_ABOVE",
                    )
                }
                if (!folder.resolve(SELLER_CONFIG).isRegularFile()) {
                    throw InputFileException("Der Ordner $folder enthält keine Datei $SELLER_CONFIG. $NOT_ABOVE")
                }
                return Root(directory)
            }
            throw InputFileException(
                "Zu $invoiceFile gibt es keine Stammdaten: Weder in $start noch in einem Verzeichnis darüber " +
                    "liegt der Ordner $FOLDER mit der Datei $SELLER_CONFIG. " +
                    "Mit rechnungsgenerator einrichten entsteht beides, oder gib die Stammdaten mit -s an.",
            )
        }

        /**
         * Anders als [of] wirft sie nicht und übergeht einen halb eingerichteten Ordner: Sie liefert `einrichten`
         * nur einen Hinweis, keine Stammdaten fürs Blatt.
         */
        fun above(directory: Path): Root? {
            val start = directory.toAbsolutePath().normalize().parent ?: return null
            return generateSequence(start) { it.parent }
                .firstOrNull { it.resolve(FOLDER).resolve(SELLER_CONFIG).isRegularFile() }
                ?.let { Root(it) }
        }

        /** Verschiedene Wurzeln brechen ab: Ein Abschlagsblock gehört zu einem Rechnungssteller. */
        fun of(invoiceFiles: List<Path>): Root {
            require(invoiceFiles.isNotEmpty()) { "Ohne Rechnungsdatei gibt es keine Wurzel zu suchen." }
            val roots = invoiceFiles.associateWith { of(it) }
            val distinct = roots.values.distinct()
            if (distinct.size > 1) {
                val named =
                    distinct.joinToString("; ") { root ->
                        "$root (${roots.filterValues { it == root }.keys.joinToString(", ") { it.name }})"
                    }
                throw InputFileException(
                    "Die Rechnungen liegen in verschiedenen Wurzeln: $named. " +
                        "Abgesetzt werden nur Abschläge desselben Rechnungsstellers; " +
                        "mit -s lassen sich für alle dieselben Stammdaten angeben.",
                )
            }
            return distinct.first()
        }

        private const val NOT_ABOVE =
            "Weiter oben wird nicht gesucht: Sonst stünde am Ende der falsche Aussteller auf der Rechnung."
    }
}
