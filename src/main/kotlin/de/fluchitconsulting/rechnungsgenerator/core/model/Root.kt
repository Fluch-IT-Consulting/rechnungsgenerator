package de.fluchitconsulting.rechnungsgenerator.core.model

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * Die Wurzel einer Ablage: das Verzeichnis, das den Ordner
 * `.rechnungsgenerator/` mit den Stammdaten enthält. Alles darunter wird mit
 * diesen Stammdaten gesetzt (ADR-0015).
 *
 * `value class` wie `Latex` im Renderer: Zur Laufzeit bleibt es der Pfad, es
 * entsteht kein Objekt. Der Konstruktor ist
 * privat, damit eine Wurzel nur aus der Suche stammt – derselbe Zug wie bei
 * `AcceptedInvoice` und `Template`: Was ungeprüft ist, lässt sich nicht bauen.
 *
 * Die Suche liegt hier neben dem [YamlLoader], weil der Dateiname dorthin
 * gehört, wo gelesen wird, und weil beide Kommandos sie brauchen. Eine eigene
 * Naht bekommt sie nicht: Sie rechnet Pfade und fragt `exists`, und das lässt
 * sich mit temporären Verzeichnissen prüfen.
 */
@JvmInline
value class Root private constructor(val directory: Path) {
    /** Die Stammdaten dieser Wurzel. Ein Logo daneben gilt ab ihrem Ordner (ADR-0012). */
    val sellerConfig: Path get() = directory.resolve(FOLDER).resolve(SELLER_CONFIG)

    override fun toString(): String = directory.toString()

    companion object {
        /** Die Markierung. Allein sie macht ein Verzeichnis zur Wurzel – eine lose Datei nicht. */
        const val FOLDER = ".rechnungsgenerator"

        const val SELLER_CONFIG = "stammdaten.yaml"

        /**
         * Die Wurzel über [invoiceFile]: den angegebenen Pfad hinauf, absolut
         * und normalisiert, bis zur Wurzel des Dateisystems.
         *
         * Gesucht wird ab dem Verzeichnis der **Rechnungsdatei** und nicht ab
         * dem Arbeitsverzeichnis: Derselbe Aufruf auf dieselbe Datei soll
         * dieselben Stammdaten finden, egal wo man steht.
         *
         * Symbolische Links bleiben stehen – `normalize()` rechnet nur am
         * Pfad, `toRealPath()` löste sie auf. Wer eine Rechnung in seine Ablage
         * verlinkt, sucht von dort aus, wo der Link liegt.
         */
        fun of(invoiceFile: Path): Root {
            val start = invoiceFile.toAbsolutePath().normalize().parent
            // generateSequence: das Verzeichnis und jedes darüber, bis `parent`
            // null liefert – dann ist die Wurzel des Dateisystems erreicht.
            for (directory in generateSequence(start) { it.parent }) {
                val folder = directory.resolve(FOLDER)
                if (!folder.exists()) continue
                // Halb eingerichtet ist keine Wurzel, und weiter oben wird
                // trotzdem nicht gesucht: Eine innere Wurzel ohne Stammdaten
                // nähme sonst still die der äußeren, und auf der Rechnung
                // stünde der falsche Aussteller.
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
         * Die nächste vollständige Wurzel **oberhalb** von [directory], oder
         * `null`, wenn es keine gibt.
         *
         * Eine Frage und kein Urteil: `einrichten` stellt sie, um auf eine
         * Wurzel darüber hinzuweisen, bevor es daneben eine zweite anlegt.
         * Deshalb wirft sie nicht, und deshalb übergeht sie – anders als
         * [of] – einen halb eingerichteten Ordner und sucht weiter. [of] darf
         * das nicht: Dort geht es um die Stammdaten, die wirklich aufs Blatt
         * kommen, und ein stiller Griff zur äußeren Wurzel setzte den falschen
         * Aussteller. Hier geht es nur um einen Satz auf stderr.
         */
        fun above(directory: Path): Root? {
            val start = directory.toAbsolutePath().normalize().parent ?: return null
            return generateSequence(start) { it.parent }
                .firstOrNull { it.resolve(FOLDER).resolve(SELLER_CONFIG).isRegularFile() }
                ?.let { Root(it) }
        }

        /**
         * Die eine Wurzel über allen [invoiceFiles].
         *
         * Gesucht wird je Datei, denn jede kann woanders liegen. Verschiedene
         * Wurzeln brechen ab: Ein Abschlagsblock setzt Abschläge **eines**
         * Rechnungsstellers ab, und „die erste gewinnt" verschleierte das nur.
         */
        fun of(invoiceFiles: List<Path>): Root {
            require(invoiceFiles.isNotEmpty()) { "Ohne Rechnungsdatei gibt es keine Wurzel zu suchen." }
            // associateWith: zu jeder Datei ihre Wurzel, in der übergebenen Reihenfolge.
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
