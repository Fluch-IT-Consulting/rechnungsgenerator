package de.fluchitconsulting.rechnungsgenerator

import com.lemonappdev.konsist.api.Konsist
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/**
 * Hält die Paketstruktur aus CLAUDE.md (#107): zwei vertikale Pakete, eines je
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

    // Wer darf woher importieren – die Zeichnung aus #107, Zeile für Zeile.
    val allowedImports =
        mapOf(
            root to setOf("$root.generate", "$root.generate.toolchain", "$root.prepayments"),
            "$root.generate" to
                setOf("$root.generate.render", "$root.generate.toolchain", "$root.rules", "$root.calculation", "$root.model"),
            "$root.generate.render" to setOf("$root.rules", "$root.calculation", "$root.model", "$root.values"),
            "$root.generate.toolchain" to emptySet(),
            "$root.prepayments" to setOf("$root.rules", "$root.calculation", "$root.model", "$root.values"),
            "$root.rules" to setOf("$root.calculation", "$root.model", "$root.values"),
            "$root.calculation" to setOf("$root.model", "$root.values"),
            "$root.model" to setOf("$root.values"),
            "$root.values" to emptySet(),
        )

    // Nur die Quellen unter src/main: Ein Test darf jedes Paket sehen.
    val sourceFiles =
        Konsist.scopeFromProduction().files.map { file ->
            SourceFile(
                name = file.name,
                packageName = checkNotNull(file.packagee?.name) { "${file.name} hat keine package-Zeile" },
                imports = file.imports.map { it.name }.filter { it.startsWith("$root.") },
            )
        }

    /**
     * Das Paket, aus dem ein Import stammt: der längste Eintrag der Tabelle,
     * der ihm vorangeht. `substringBeforeLast('.')` reichte nicht, weil
     * `rules.InvoiceKindRules.Result` einen verschachtelten Typ nennt und dann
     * die Klasse herauskäme, nicht das Paket.
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
