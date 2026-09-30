package de.fluchitconsulting.rechnungsgenerator

import com.lemonappdev.konsist.api.Konsist
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/**
 * #107. Nicht mit Konsists `assertArchitecture`: Dort enthielte `generate..` auch
 * `generate.render`, und ein Import zwischen beiden fiele nicht auf
 * (nachgemessen mit Konsist 0.17.3). Die Tabelle vergleicht exakte Paketnamen.
 */
class PackageStructureTest : FunSpec({

    val root = "de.fluchitconsulting.rechnungsgenerator"

    // `core` selbst enthält keine Datei (#310, #312, #432).
    val core = "$root.core"

    val allowedImports =
        mapOf(
            // Für die Fachfehler, die execute() übersetzt (#119).
            root to
                setOf(
                    "$root.generate",
                    "$root.generate.typesetting",
                    "$root.prepayments",
                    "$root.setup",
                    "$root.xml",
                    "$root.toolchain",
                    "$core.rules",
                    "$core.model",
                ),
            "$root.generate" to
                setOf(
                    "$root.generate.render",
                    "$root.generate.einvoice",
                    "$root.generate.typesetting",
                    "$root.toolchain",
                    "$root.toolchain.process",
                    "$root.toolchain.validation",
                    "$core.accepted",
                    "$core.rules",
                    "$core.calculation",
                    "$core.model",
                ),
            // generate.typesetting für PdfSources (#202).
            "$root.generate.render" to
                setOf(
                    "$root.generate.typesetting",
                    "$core.accepted",
                    "$core.rules",
                    "$core.calculation",
                    "$core.model",
                    "$core.values",
                    "$core.text",
                ),
            // ADR-0023; core.text für den Pflichtsatz (ADR-0019).
            "$root.generate.einvoice" to
                setOf(
                    "$core.accepted",
                    "$core.rules",
                    "$core.calculation",
                    "$core.model",
                    "$core.values",
                    "$core.text",
                ),
            // ADR-0029
            "$root.generate.typesetting" to setOf("$root.toolchain", "$root.toolchain.process", "$root.toolchain.validation"),
            "$root.toolchain" to emptySet(),
            "$root.toolchain.process" to setOf("$root.toolchain"),
            "$root.toolchain.validation" to setOf("$root.toolchain", "$root.toolchain.process"),
            "$root.prepayments" to setOf("$core.accepted", "$core.rules", "$core.calculation", "$core.model", "$core.values"),
            // #275
            "$root.setup" to setOf("$core.model"),
            // #380
            "$root.xml" to setOf("$root.toolchain", "$root.toolchain.validation"),
            // Ein Typ wohnt, wo er gebaut wird, sonst zeigte rules nach oben (#304).
            "$core.accepted" to setOf("$core.rules", "$core.calculation", "$core.model", "$core.values", "$core.text"),
            "$core.rules" to setOf("$core.calculation", "$core.model", "$core.values"),
            "$core.calculation" to setOf("$core.model", "$core.values"),
            "$core.model" to setOf("$core.localization", "$core.values", "$core.text", "$core.serialization"),
            // #432
            "$core.localization" to setOf("$core.values"),
            "$core.values" to setOf("$core.serialization"),
            // #312
            "$core.text" to emptySet(),
            "$core.serialization" to emptySet(),
        )

    // Nur src/main dieses Checkouts: scopeFromProduction() läse auch die
    // Worktrees unter .claude/worktrees.
    val sourceFiles =
        Konsist.scopeFromDirectory("src/main/kotlin").files.map { file ->
            SourceFile(
                name = file.name,
                packageName = checkNotNull(file.packagee?.name) { "${file.name} hat keine package-Zeile" },
                imports = file.imports.map { it.name }.filter { it.startsWith("$root.") },
            )
        }

    // Nicht `substringBeforeLast('.')`: Bei einem verschachtelten Typ käme die Klasse heraus.
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

    // ktlint lässt diese Importe stehen, und der Hook feuert bei `git mv` nicht (#111).
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

private data class SourceFile(val name: String, val packageName: String, val imports: List<String>)
