package de.fluchitconsulting.rechnungsgenerator

import com.lemonappdev.konsist.api.Konsist
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/**
 * Hält die Paketstruktur aus CLAUDE.md (#107): ein vertikales Paket je
 * Kommando, darunter der gemeinsame Kern, und jede Kante zeigt nach unten.
 *
 * Kotlin kennt keine Paketgrenze – `internal` gilt pro Modul –, deshalb steht
 * die Zeichnung hier als Tabelle: für jedes Paket die Pakete, aus denen es
 * importieren darf. Ein neues Paket muss eingetragen werden, sonst wird der
 * Test rot. Das ist gewollt: Wo es in der Zeichnung steht, ist eine
 * Entscheidung, keine Nebenwirkung.
 *
 * Konsist liest dafür die Quellen; geprüft wird aber mit Kotest, nicht mit
 * Konsists `assertArchitecture`. Das verlangt an jeder Schicht die zwei Punkte
 * für "samt Unterpaketen", und dann enthält `generate..` auch
 * `generate.render`: Ein Import von `generate.render` nach `generate` gilt als
 * schichtintern und fällt nicht auf (nachgemessen mit Konsist 0.17.3). Die
 * Tabelle vergleicht dagegen exakte Paketnamen. Und `shouldBeEmpty` nennt
 * jeden verbotenen Import samt Datei, Konsists `assertFalse` nur die Datei.
 */
class PackageStructureTest : FunSpec({

    val root = "de.fluchitconsulting.rechnungsgenerator"

    // Der Kern selbst hat keine Zeile: `core` enthält keine Datei, nur seine
    // fünf Schichten (#310) und die zwei Blätter text und serialization (#312).
    val core = "$root.core"

    // Wer darf woher importieren – die Zeichnung aus #107, Zeile für Zeile.
    val allowedImports =
        mapOf(
            // model und rules für die Fachfehler, die execute() übersetzt (#119).
            root to
                setOf(
                    "$root.generate",
                    "$root.generate.toolchain",
                    "$root.prepayments",
                    "$root.setup",
                    "$core.rules",
                    "$core.model",
                ),
            "$root.generate" to
                setOf(
                    "$root.generate.render",
                    "$root.generate.toolchain",
                    "$core.accepted",
                    "$core.rules",
                    "$core.calculation",
                    "$core.model",
                ),
            // generate.toolchain für PdfSources: Der Renderer liefert die
            // Quellen des PDF im Eingabetyp der Werkzeuge (#202). Die Kante
            // zeigt nach unten auf ein Blatt – toolchain selbst importiert
            // nichts aus dem Projekt.
            "$root.generate.render" to
                setOf(
                    "$root.generate.toolchain",
                    "$core.accepted",
                    "$core.rules",
                    "$core.calculation",
                    "$core.model",
                    "$core.values",
                    "$core.text",
                ),
            // Das XML der E-Rechnung liest dieselbe angenommene Rechnung wie
            // der LaTeX-Renderer, kennt aber weder ihn noch die Werkzeuge
            // (ADR-0023).
            "$root.generate.einvoice" to
                setOf(
                    "$core.accepted",
                    "$core.calculation",
                    "$core.model",
                    "$core.values",
                ),
            "$root.generate.toolchain" to emptySet(),
            "$root.prepayments" to setOf("$core.accepted", "$core.rules", "$core.calculation", "$core.model", "$core.values"),
            // setup kennt aus dem Kern nur die Wurzel: den Namen ihres
            // Ordners und die Frage, ob oberhalb schon eine liegt (#275).
            "$root.setup" to setOf("$core.model"),
            // Über rules, weil AcceptedInvoice.check die Regeln aufruft. Was
            // die Regeln bauen – InvoiceKind, Deduction –, bleibt dort: Ein
            // Typ wohnt, wo er gebaut wird, sonst zeigte rules nach oben (#304).
            "$core.accepted" to setOf("$core.rules", "$core.calculation", "$core.model", "$core.values", "$core.text"),
            "$core.rules" to setOf("$core.calculation", "$core.model", "$core.values"),
            "$core.calculation" to setOf("$core.model", "$core.values"),
            "$core.model" to setOf("$core.values", "$core.text", "$core.serialization"),
            "$core.values" to setOf("$core.serialization"),
            // Die Platzhalter-Texte liegen neben values und nicht darunter:
            // Sie kennen weder Werte noch Vokabular, und gebraucht werden sie
            // erst in model, bei den Fachkatalogen (#312).
            "$core.text" to emptySet(),
            // Das Gerüst, über das jeder Werttyp seinen Serializer baut.
            "$core.serialization" to emptySet(),
        )

    // Nur die Quellen unter src/main: Ein Test darf jedes Paket sehen. Und nur
    // die dieses Checkouts – scopeFromProduction() liest alles unterhalb der
    // Projektwurzel, auch die Worktrees in .claude/worktrees mit ihrem
    // eigenen, womöglich alten Stand.
    val sourceFiles =
        Konsist.scopeFromDirectory("src/main/kotlin").files.map { file ->
            SourceFile(
                name = file.name,
                packageName = checkNotNull(file.packagee?.name) { "${file.name} hat keine package-Zeile" },
                imports = file.imports.map { it.name }.filter { it.startsWith("$root.") },
            )
        }

    /**
     * Das Paket, aus dem ein Import stammt: der längste Eintrag der Tabelle,
     * der ihm vorangeht. `substringBeforeLast('.')` reichte nicht, weil
     * `core.rules.InvoiceKindRules.Result` einen verschachtelten Typ nennt und
     * dann die Klasse herauskäme, nicht das Paket.
     */
    fun packageOf(importName: String): String? = allowedImports.keys.filter { importName.startsWith("$it.") }.maxByOrNull { it.length }

    test("die Tabelle nennt genau die Pakete, die es gibt") {
        sourceFiles.map { it.packageName }.toSet() shouldBe allowedImports.keys
    }

    test("jede Kante zwischen den Paketen zeigt nach unten") {
        val violations =
            sourceFiles.flatMap { file ->
                val allowed = requireNotNull(allowedImports[file.packageName]) { "Paket ${file.packageName} fehlt in der Tabelle" }
                file.imports
                    .filter { packageOf(it) != file.packageName && packageOf(it) !in allowed }
                    .map { "${file.name} in ${file.packageName} importiert $it" }
            }
        violations.shouldBeEmpty()
    }

    // ktlint lässt diese Importe absichtlich stehen (seit 0.50.0), und der
    // Hook feuert bei `git mv` plus `sed` gar nicht – 99 Stück blieben beim
    // Umzug in #111 liegen.
    test("keine Datei importiert aus ihrem eigenen Paket") {
        val violations =
            sourceFiles.flatMap { file ->
                file.imports
                    .filter { it.substringBeforeLast('.') == file.packageName }
                    .map { "${file.name} importiert ${it.substringAfterLast('.')} aus dem eigenen Paket" }
            }
        violations.shouldBeEmpty()
    }
})

/** Was der Test von einer Quelldatei wissen muss: Name, Paket, projektinterne Importe. */
private data class SourceFile(val name: String, val packageName: String, val imports: List<String>)
