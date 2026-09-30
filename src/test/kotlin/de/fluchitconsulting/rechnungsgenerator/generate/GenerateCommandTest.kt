package de.fluchitconsulting.rechnungsgenerator.generate

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import de.fluchitconsulting.rechnungsgenerator.RechnungsgeneratorCommand
import de.fluchitconsulting.rechnungsgenerator.core.model.Root
import de.fluchitconsulting.rechnungsgenerator.generate.render.Foundation
import de.fluchitconsulting.rechnungsgenerator.generate.render.Template
import de.fluchitconsulting.rechnungsgenerator.generate.typesetting.FakeTypesetter
import de.fluchitconsulting.rechnungsgenerator.generate.typesetting.LuaLatexTypesetter
import de.fluchitconsulting.rechnungsgenerator.generate.typesetting.Typesetter
import de.fluchitconsulting.rechnungsgenerator.runCli
import de.fluchitconsulting.rechnungsgenerator.setup.SetupCommand
import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import de.fluchitconsulting.rechnungsgenerator.toolchain.process.FakeProcessRunner
import de.fluchitconsulting.rechnungsgenerator.toolchain.validation.BuiltInPdfAValidator
import de.fluchitconsulting.rechnungsgenerator.toolchain.validation.ExternalPdfAValidator
import de.fluchitconsulting.rechnungsgenerator.toolchain.validation.PdfAValidator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.IOException
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Über das Elternkommando aufgerufen; `echoMessage` erbt das Unterkommando von ihm. */
class GenerateCommandTest : FunSpec({

    val inputDir = createTempDirectory("kommando-test")
    val invoice = inputDir.resolve("rechnung.yaml").also { it.writeText(resource("rechnung.yaml")) }
    val sellerConfig = inputDir.resolve("stammdaten.yaml").also { it.writeText(resource("stammdaten.yaml")) }

    // Der Viewer ist immer ein Fake: `--oeffnen` soll im Build nie ein Fenster aufmachen.
    fun generate(
        typesetter: Typesetter,
        vararg args: String,
        viewer: PdfViewer = FakePdfViewer(),
        output: MutableList<String> = mutableListOf(),
        sellerConfigFile: Path = sellerConfig,
        invoiceFile: Path = invoice,
    ): List<String> {
        val command = RechnungsgeneratorCommand({ typesetter }, viewer)
        command.context { echoMessage = { _, message, _, _ -> output += message.toString() } }
        command.parse(arrayOf("erzeuge", invoiceFile.toString(), "-s", sellerConfigFile.toString()) + args)
        return output
    }

    // #226
    context("die Wahl des PDF/A-Prüfers") {
        fun chosen(vararg args: String): PdfAValidator {
            lateinit var seen: PdfAValidator
            val command =
                RechnungsgeneratorCommand({ validator ->
                    seen = validator
                    FakeTypesetter()
                })
            command.context { echoMessage = { _, _, _, _ -> } }
            command.parse(
                arrayOf(
                    "erzeuge",
                    invoice.toString(),
                    "-s",
                    sellerConfig.toString(),
                    "-o",
                    inputDir.resolve("pruefer-${args.size}.pdf").toString(),
                    "--ueberschreiben",
                ) + args,
            )
            return seen
        }

        test("ohne Schalter prüft die eingebaute Bibliothek") {
            chosen() shouldBe BuiltInPdfAValidator
        }

        test("mit --externe-pdfa-pruefung prüft das Programm verapdf") {
            chosen("--externe-pdfa-pruefung").shouldBeInstanceOf<ExternalPdfAValidator>()
        }
    }

    // ADR-0023
    context("--zugferd") {
        test("die Quellen tragen das XML der Rechnung, und das Urteil nennt PDF/A-3u") {
            val typesetter = FakeTypesetter()
            val target = inputDir.resolve("zugferd.pdf")

            val output = generate(typesetter, "-o", target.toString(), "--zugferd")

            String(checkNotNull(typesetter.sources?.einvoice)) shouldBe resource("rechnung-erwartet.xml")
            output shouldContainInOrder listOf(target.toString(), "PDF/A-3u: gültig")
        }

        test("ohne den Schalter tragen die Quellen kein XML") {
            val typesetter = FakeTypesetter()

            generate(typesetter, "-o", inputDir.resolve("ohne-zugferd.pdf").toString())

            checkNotNull(typesetter.sources).einvoice shouldBe null
        }

        test("ein ungültiges PDF/A-3u heißt auch so") {
            val error =
                shouldThrow<ToolchainException> {
                    generate(FakeTypesetter(valid = false), "-o", inputDir.resolve("zugferd-ungueltig.pdf").toString(), "--zugferd")
                }

            error.message!! shouldContain "kein gültiges PDF/A-3u"
        }

        test("ein ungültiges PDF/A-3u nennt zum Ansehen die geprüfte Datei und nicht nur das Verzeichnis") {
            val fake = FakeTypesetter(valid = false)

            val error =
                shouldThrow<ToolchainException> {
                    generate(
                        fake,
                        "-o",
                        inputDir.resolve("zugferd-ansehen.pdf").toString(),
                        "--zugferd",
                        "--arbeitsdateien-behalten",
                    )
                }

            error.message!! shouldContain "Das PDF liegt zum Ansehen unter ${fake.workingDir!!.resolve("${fake.jobName}-zugferd.pdf")}."
        }

        test("auch die Schlussrechnung geht als E-Rechnung, samt ihrer Abschläge") {
            val finalInvoice = inputDir.resolve("schlussrechnung.yaml").also { it.writeText(resource("schlussrechnung.yaml")) }
            val typesetter = FakeTypesetter()

            generate(typesetter, "-o", inputDir.resolve("schluss-zugferd.pdf").toString(), "--zugferd", invoiceFile = finalInvoice)

            String(checkNotNull(typesetter.sources?.einvoice)) shouldBe resource("schlussrechnung-erwartet.xml")
        }
    }

    test("das PDF landet am gewünschten Ort, und das Ergebnis steht in der Ausgabe") {
        val target = inputDir.resolve("ergebnis.pdf")

        val output = generate(FakeTypesetter(), "-o", target.toString())

        target.exists() shouldBe true
        target.readText() shouldContain "%PDF"
        output shouldContainInOrder listOf(target.toString(), "PDF/A-1b: gültig")
    }

    test("das Arbeitsverzeichnis wird aufgeräumt") {
        val fake = FakeTypesetter()

        generate(fake, "-o", inputDir.resolve("weg.pdf").toString())

        fake.workingDir!!.exists() shouldBe false
    }

    test("mit --arbeitsdateien-behalten bleibt es stehen und wird genannt") {
        val fake = FakeTypesetter()

        val output = generate(fake, "-o", inputDir.resolve("bleibt.pdf").toString(), "--arbeitsdateien-behalten")

        fake.workingDir!!.exists() shouldBe true
        output.last() shouldContain "Arbeitsdateien:"
    }

    test("ein nicht beschreibbares Ziel nennt die Zieldatei, und nur einmal") {
        // NoSuchFileException trägt in message nur den Pfad.
        val target = inputDir.resolve("gibt-es-nicht").resolve("ergebnis.pdf")

        val error = shouldThrow<CliktError> { generate(FakeTypesetter(), "-o", target.toString()) }

        error.message shouldBe "Die Datei $target konnte nicht geschrieben werden."
    }

    // Hält die Abschrift in einrichten gleich (#327). Ein Ort unter einer Datei
    // braucht keine Dateirechte, die ein Lauf als root überginge.
    test("ein nicht beschreibbares Ziel meldet sich wie bei einrichten") {
        val blocked = inputDir.resolve("blockiert").also { it.writeText("") }
        val generateTarget = blocked.resolve("ergebnis.pdf")
        val setupTarget = blocked.resolve(Root.FOLDER).resolve(Root.SELLER_CONFIG)

        val generateError = shouldThrow<CliktError> { generate(FakeTypesetter(), "-o", generateTarget.toString()) }
        val setupError = shouldThrow<CliktError> { SetupCommand(blocked).parse(emptyArray()) }

        val message = generateError.message!!.replace(generateTarget.toString(), "<ziel>")
        message shouldStartWith "Die Datei <ziel> konnte nicht geschrieben werden: "
        setupError.message!!.replace(setupTarget.toString(), "<ziel>") shouldBe message
    }

    // #300
    test("eine IOException aus dem Satz wird nicht zum Schreibfehler") {
        val error =
            shouldThrow<IOException> {
                generate(FakeTypesetter(ioFailure = "kein Platz im Temp-Verzeichnis"), "-o", inputDir.resolve("nie.pdf").toString())
            }

        error.message shouldBe "kein Platz im Temp-Verzeichnis"
    }

    test("ein ungültiges PDF/A ist ein Fehler der Werkzeuge") {
        // Exitcode und Kanal prüft MainTest (#119).
        val error =
            shouldThrow<ToolchainException> {
                generate(FakeTypesetter(valid = false), "-o", inputDir.resolve("ungueltig.pdf").toString())
            }

        error.message!! shouldContain "kein gültiges PDF/A-1b"
    }

    test("auch ein ungültiges PDF/A lässt kein Arbeitsverzeichnis zurück") {
        val fake = FakeTypesetter(valid = false)

        shouldThrow<ToolchainException> { generate(fake, "-o", inputDir.resolve("ungueltig2.pdf").toString()) }

        fake.workingDir!!.exists() shouldBe false
    }

    test("ein ungültiges PDF/A legt keine Datei an und sagt, wie man es ansieht") {
        val target = inputDir.resolve("nie-da.pdf")

        val error = shouldThrow<ToolchainException> { generate(FakeTypesetter(valid = false), "-o", target.toString()) }

        target.exists() shouldBe false
        error.message!! shouldContain "--arbeitsdateien-behalten"
    }

    test("ein ungültiges PDF/A ersetzt keine vorhandene Datei, auch nicht mit --ueberschreiben") {
        val target = inputDir.resolve("verschickt.pdf").also { it.writeText("das verschickte Original") }

        val error =
            shouldThrow<ToolchainException> {
                generate(FakeTypesetter(valid = false), "-o", target.toString(), "--ueberschreiben")
            }

        target.readText() shouldBe "das verschickte Original"
        error.message!! shouldContain "6.1.2"
    }

    test("eine vorhandene Zieldatei bleibt ohne --ueberschreiben unangetastet") {
        val target = inputDir.resolve("vorhanden.pdf").also { it.writeText("das verschickte Original") }
        val fake = FakeTypesetter()

        val error = shouldThrow<CliktError> { generate(fake, "-o", target.toString()) }

        error.statusCode shouldBe 1
        error.message!! shouldContain "--ueberschreiben"
        target.readText() shouldBe "das verschickte Original"
        // Geprüft wird vor dem Satz.
        fake.workingDir shouldBe null
    }

    test("mit --ueberschreiben wird eine vorhandene Zieldatei ersetzt") {
        val target = inputDir.resolve("entwurf.pdf").also { it.writeText("der erste Entwurf") }

        generate(FakeTypesetter(), "-o", target.toString(), "--ueberschreiben")

        target.readText() shouldContain "%PDF"
    }

    test("der Jobname kommt aus Rechnungsart und -nummer") {
        val fake = FakeTypesetter()

        generate(fake, "-o", inputDir.resolve("jobname.pdf").toString())

        fake.jobName shouldBe "Rechnung-R26-042"
    }

    // Je Test ein eigenes Verzeichnis, denn ohne -o entscheidet die Rechnungsdatei, wo das PDF entsteht.
    context("ohne -o entsteht das PDF neben der Rechnungsdatei (ADR-0018)") {
        fun invoiceFile(name: String) =
            createTempDirectory("ohne-ausgabe").resolve(name).also { it.writeText(resource("rechnung.yaml")) }

        test("im Verzeichnis der Rechnungsdatei und mit ihrem Namen, nicht mit dem aus der Nummer") {
            val file = invoiceFile("R.yaml")
            val fake = FakeTypesetter()

            val output = generate(fake, invoiceFile = file)

            val pdf = file.resolveSibling("R.pdf")
            pdf.readText() shouldContain "%PDF"
            output.first() shouldBe pdf.toString()
            // Der Name des PDF hängt nicht am Jobnamen (ADR-0005).
            fake.jobName shouldBe "Rechnung-R26-042"
        }

        test("aus .yml wird .pdf") {
            val file = invoiceFile("R.yml")

            generate(FakeTypesetter(), invoiceFile = file)

            file.resolveSibling("R.pdf").exists() shouldBe true
        }

        test("eine Datei ohne Endung bekommt .pdf angehängt") {
            val file = invoiceFile("R")

            generate(FakeTypesetter(), invoiceFile = file)

            file.resolveSibling("R.pdf").exists() shouldBe true
        }

        test("die Ausgabe nennt das Ziel so, wie die Rechnungsdatei angegeben wurde") {
            // Mit ../ bis ins Temp-Verzeichnis, aber relativ.
            val file = invoiceFile("R.yaml")
            val relative = Path.of("").toAbsolutePath().relativize(file)

            val output = generate(FakeTypesetter(), invoiceFile = relative)

            output.first() shouldBe relative.resolveSibling("R.pdf").toString()
        }

        test("der Überschreibschutz greift auch hier") {
            val file = invoiceFile("R.yaml")
            val pdf = file.resolveSibling("R.pdf").also { it.writeText("das verschickte Original") }

            val error = shouldThrow<CliktError> { generate(FakeTypesetter(), invoiceFile = file) }

            error.message!! shouldContain "--ueberschreiben"
            pdf.readText() shouldBe "das verschickte Original"
        }
    }

    // Auch nicht mit `--ueberschreiben`: Die Eingabe zu zerstören ist kein Nachbessern.
    context("das Ziel ist nie die Rechnungsdatei") {
        fun invoiceFile(name: String) =
            createTempDirectory("ziel-eingabe").resolve(name).also { it.writeText(resource("rechnung.yaml")) }

        listOf(emptyList(), listOf("--ueberschreiben")).forEach { flags ->
            test("-o auf die Rechnungsdatei bricht ab ${if (flags.isEmpty()) "ohne" else "mit"} --ueberschreiben") {
                val file = invoiceFile("R.yaml")
                val fake = FakeTypesetter()

                val error =
                    shouldThrow<CliktError> {
                        generate(fake, "-o", file.toString(), *flags.toTypedArray(), invoiceFile = file)
                    }

                error.statusCode shouldBe 1
                error.message!! shouldContain "Rechnungsdatei selbst"
                file.readText() shouldBe resource("rechnung.yaml")
                fake.workingDir shouldBe null
            }
        }

        test("auch unter anderer Schreibweise derselben Datei") {
            val file = invoiceFile("R.yaml")

            shouldThrow<CliktError> {
                generate(FakeTypesetter(), "-o", file.resolveSibling("./R.yaml").toString(), "--ueberschreiben", invoiceFile = file)
            }

            file.readText() shouldBe resource("rechnung.yaml")
        }

        test("heißt die Rechnungsdatei selbst .pdf, bricht auch der Lauf ohne -o ab") {
            val file = invoiceFile("R.pdf")

            val error =
                shouldThrow<CliktError> { generate(FakeTypesetter(), "--ueberschreiben", invoiceFile = file) }

            error.message!! shouldContain "Rechnungsdatei selbst"
            file.readText() shouldBe resource("rechnung.yaml")
        }
    }

    test("das PDF trägt das Rechnungsdatum als Erstellungsdatum") {
        val fake = FakeTypesetter()

        generate(fake, "-o", inputDir.resolve("datum.pdf").toString())

        fake.sources!!.creationDate shouldBe LocalDate.of(2026, 8, 14)
    }

    // #202
    test("die Metadaten kommen beim Typesetter an") {
        val fake = FakeTypesetter()

        generate(fake, "-o", inputDir.resolve("metadaten.pdf").toString())

        fake.sources!!.xmpMetadata shouldContain "\\Title{Rechnung R26-042}"
    }

    context("--oeffnen") {
        test("öffnet genau die Zieldatei, und zwar nach der Ausgabe") {
            val target = inputDir.resolve("oeffnen.pdf")
            val output = mutableListOf<String>()
            var outputAtOpen: List<String>? = null
            val viewer = FakePdfViewer(onOpen = { outputAtOpen = output.toList() })

            generate(FakeTypesetter(), "-o", target.toString(), "--oeffnen", viewer = viewer, output = output)

            viewer.opened shouldBe listOf(target)
            output shouldContainInOrder listOf(target.toString(), "PDF/A-1b: gültig")
            outputAtOpen shouldBe output
        }

        test("ohne den Schalter öffnet sich nichts") {
            val viewer = FakePdfViewer()

            generate(FakeTypesetter(), "-o", inputDir.resolve("nicht-oeffnen.pdf").toString(), viewer = viewer)

            viewer.opened shouldBe emptyList()
        }

        test("ein ungültiges PDF/A öffnet sich nicht, auch nicht mit --arbeitsdateien-behalten") {
            val viewer = FakePdfViewer()

            shouldThrow<ToolchainException> {
                generate(
                    FakeTypesetter(valid = false),
                    "-o",
                    inputDir.resolve("ungueltig-oeffnen.pdf").toString(),
                    "--oeffnen",
                    "--arbeitsdateien-behalten",
                    viewer = viewer,
                )
            }

            viewer.opened shouldBe emptyList()
        }

        test("bricht erzeuge an einer vorhandenen Zieldatei ab, öffnet sich auch die nicht") {
            val target = inputDir.resolve("vorhanden-oeffnen.pdf").also { it.writeText("das verschickte Original") }
            val viewer = FakePdfViewer()

            shouldThrow<CliktError> { generate(FakeTypesetter(), "-o", target.toString(), "--oeffnen", viewer = viewer) }

            viewer.opened shouldBe emptyList()
        }

        test("scheitert das Öffnen, bleibt es ein Erfolg – mit einem Hinweis als letzter Zeile") {
            val target = inputDir.resolve("viewer-fehlt.pdf")
            val viewer = FakePdfViewer(result = ViewerResult.Failed("open endete mit Exitcode 1."))

            // Nur die Reihenfolge; Exitcode und Kanal liest MainTest (#153).
            val output = generate(FakeTypesetter(), "-o", target.toString(), "--oeffnen", viewer = viewer)

            target.exists() shouldBe true
            output.last() shouldBe "Hinweis: Das PDF konnte nicht geöffnet werden: open endete mit Exitcode 1."
        }
    }

    context("logo (#198)") {
        val logoDir = createTempDirectory("kommando-logo")
        val logo = logoDir.resolve("logo.tex").also { it.writeText(resource("logo.tex")) }

        fun sellerConfigWithLogo(design: String) =
            logoDir.resolve("stammdaten-$design.yaml").also {
                it.writeText(resource("stammdaten.yaml") + "design: $design\nlogo: logo.tex\n")
            }

        fun target(name: String) = inputDir.resolve("$name.pdf").toString()

        test("jedes Design bekommt das Logo als weitere Datei mit") {
            listOf("helsinki", "prag").forEach { design ->
                withClue(design) {
                    val fake = FakeTypesetter()

                    generate(fake, "-o", target("logo-$design"), sellerConfigFile = sellerConfigWithLogo(design))

                    fake.sources!!.files.keys shouldBe setOf(Foundation.FILE_NAME, Template.LANGUAGE_FILE_NAME, "logo.tex")
                    fake.sources!!
                        .files
                        .getValue("logo.tex")
                        .decodeToString() shouldBe logo.readText()
                }
            }
        }

        test("scheitert der Satz, nennt die Meldung das Logo mit seinem Pfad") {
            val error =
                shouldThrow<ToolchainException> {
                    generate(
                        FakeTypesetter(typesettingFailure = "lualatex konnte Rechnung-R26-042.tex nicht übersetzen"),
                        "-o",
                        target("logo-kaputt"),
                        sellerConfigFile = sellerConfigWithLogo("prag"),
                    )
                }

            error.message!! shouldContain "nicht übersetzen"
            error.message!! shouldContain "Die Stammdaten nennen ein Logo (${logo.toAbsolutePath()})"
        }

        // Mit dem echten Typesetter: Erst LatexCompiler macht aus der Zeitüberschreitung einen Satzfehler.
        test("hängt der Satz über die Zeitgrenze, nennt die Meldung das Logo mit seinem Pfad (#507)") {
            val hanging = LuaLatexTypesetter(BuiltInPdfAValidator, FakeProcessRunner(FakeProcessRunner.Run(timedOut = true)))

            val error =
                shouldThrow<ToolchainException> {
                    generate(hanging, "-o", target("logo-haengt"), sellerConfigFile = sellerConfigWithLogo("prag"))
                }

            error.message!! shouldContain "lualatex hat nach 2m nicht geantwortet"
            error.message!! shouldContain "Die Stammdaten nennen ein Logo (${logo.toAbsolutePath()})"
        }

        test("auch ein ungültiges PDF/A nennt das Logo, denn es kann daran liegen") {
            val error =
                shouldThrow<ToolchainException> {
                    generate(FakeTypesetter(valid = false), "-o", target("logo-ungueltig"), sellerConfigFile = sellerConfigWithLogo("prag"))
                }

            error.message!! shouldContain "kein gültiges PDF/A-1b"
            error.message!! shouldContain "Die Stammdaten nennen ein Logo"
        }

        // #386, #491
        test("scheitern Prüfer, XML oder Einbetten, nennt die Meldung das Logo nicht, denn daran kann es nicht liegen") {
            listOf(
                "verapdf brach mit Exitcode 7 ab",
                "veraPDF konnte Rechnung-R26-042.pdf nicht prüfen",
                "Das XML der E-Rechnung ist ungültig.",
                "Die E-Rechnung ließ sich nicht in Rechnung-R26-042.pdf einbetten",
            ).forEach { failure ->
                withClue(failure) {
                    val error =
                        shouldThrow<ToolchainException> {
                            generate(
                                FakeTypesetter(failure = failure),
                                "--zugferd",
                                "-o",
                                target("logo-kein-satzfehler"),
                                sellerConfigFile = sellerConfigWithLogo("prag"),
                            )
                        }

                    error.message!! shouldContain failure
                    error.message!! shouldNotContain "Logo"
                }
            }
        }

        test("ohne Logo in den Stammdaten nennt die Meldung keines") {
            val error =
                shouldThrow<ToolchainException> {
                    generate(
                        FakeTypesetter(typesettingFailure = "lualatex konnte Rechnung-R26-042.tex nicht übersetzen"),
                        "-o",
                        target("ohne-logo-kaputt"),
                    )
                }

            error.message!! shouldNotContain "Logo"
        }
    }

    test("die Hinweise für die eigene Akte stehen nach dem Ergebnis, nicht davor") {
        // Die einfache Bestätigungsabfrage ergibt einen Hinweis für die eigene Akte.
        val austrianInvoice =
            inputDir.resolve("oesterreich.yaml").also {
                it.writeText(
                    resource("rechnung.yaml")
                        .replace("ort: Nürnberg", "ort: Wien\n    land: AT")
                        .replace(
                            "  referenz: BST-2026-0815",
                            """
                            |  umsatzsteuer-id: ATU12345678
                            |  ust-id-geprueft:
                            |    am: 2026-08-01
                            |    verfahren: einfach
                            |    nachweis: BZSt-Protokoll 4711
                            """.trimMargin(),
                        ).replace(
                            "    einzelpreis: 95.00",
                            "    einzelpreis: 95.00\n    ust-satz: 0\n    steuerkategorie: AE",
                        ).replace(
                            "    einzelpreis: 120.00",
                            "    einzelpreis: 120.00\n    ust-satz: 0\n    steuerkategorie: AE",
                        ),
                )
            }

        val output = mutableListOf<String>()
        val command = RechnungsgeneratorCommand({ FakeTypesetter() })
        command.context { echoMessage = { _, message, _, _ -> output += message.toString() } }
        command.parse(
            arrayOf(
                "erzeuge",
                austrianInvoice.toString(),
                "-s",
                sellerConfig.toString(),
                "-o",
                inputDir.resolve("at.pdf").toString(),
            ),
        )

        val result = output.indexOfFirst { it.contains("PDF/A-1b") }
        val hint = output.indexOfFirst { it.startsWith("Hinweis:") }
        (hint > result) shouldBe true
        output[hint] shouldContain "nicht qualifiziert"
    }

    // #507: Die Meldung zitiert Zeilen der .tex, und ohne den Schalter ist sie gelöscht.
    test("scheitert der Satz, sagt die Meldung, wo die Arbeitsdateien liegen oder wie sie liegen bleiben") {
        val typesetter = FakeTypesetter(typesettingFailure = "lualatex konnte Rechnung-R26-042.tex nicht übersetzen")

        val withoutFlag =
            shouldThrow<ToolchainException> { generate(typesetter, "-o", inputDir.resolve("satz-ohne.pdf").toString()) }
        val withFlag =
            shouldThrow<ToolchainException> {
                generate(typesetter, "-o", inputDir.resolve("satz-mit.pdf").toString(), "--arbeitsdateien-behalten")
            }

        withoutFlag.message!! shouldContain
            "Mit --arbeitsdateien-behalten bleiben die Arbeitsdateien samt Rechnung-R26-042.tex zum Ansehen liegen."
        withFlag.message!! shouldContain "Die Arbeitsdateien samt Rechnung-R26-042.tex liegen unter ${typesetter.workingDir}."
        typesetter.workingDir!!.exists() shouldBe true
    }

    // #480
    test("was über den Rand steht, ist ein Hinweis nach dem Ergebnis, und das PDF liegt am Ziel") {
        val target = inputDir.resolve("zu-breit.pdf")

        val output = generate(FakeTypesetter(overflowHint = "Etwas steht über den Rand."), "-o", target.toString())

        target.exists() shouldBe true
        output.indexOfFirst { it.contains("PDF/A-1b") } shouldBeLessThan
            output.indexOfFirst { it.startsWith("Hinweis: Etwas steht über den Rand.") }
    }

    // #488: Ohne den Schalter ist die .tex gelöscht, die der Hinweis nennt.
    test("der Hinweis auf den Überstand sagt, wo die .tex liegt oder wie sie liegen bleibt") {
        val typesetter = FakeTypesetter(overflowHint = "In Rechnung-R26-042.tex steht etwas über den Rand.")

        val withoutFlag = generate(typesetter, "-o", inputDir.resolve("ohne.pdf").toString())
        val withFlag = generate(typesetter, "-o", inputDir.resolve("mit.pdf").toString(), "--arbeitsdateien-behalten")

        withoutFlag.single { it.startsWith("Hinweis: In Rechnung-R26-042.tex") } shouldContain
            "Mit --arbeitsdateien-behalten bleibt Rechnung-R26-042.tex zum Ansehen liegen."
        withFlag.single { it.startsWith("Hinweis: In Rechnung-R26-042.tex") } shouldContain
            Regex("""Die Datei liegt unter \S+/Rechnung-R26-042\.tex\.""")
    }

    // #493
    test("verlangte der letzte Lauf noch einen, ist das ein Hinweis nach dem Ergebnis, und das PDF liegt am Ziel") {
        val target = inputDir.resolve("ohne-letzten-lauf.pdf")

        val output = generate(FakeTypesetter(rerunHint = "lualatex verlangte noch einen Lauf."), "-o", target.toString())

        target.exists() shouldBe true
        output.indexOfFirst { it.contains("PDF/A-1b") } shouldBeLessThan
            output.indexOfFirst { it == "Hinweis: lualatex verlangte noch einen Lauf." }
    }

    context("ohne -s kommen die Stammdaten aus der Wurzel (ADR-0015)") {
        // [company] steht später im Satz und zeigt, welche Stammdaten gelesen wurden.
        fun archive(company: String? = null): Path {
            val root = createTempDirectory("wurzel-erzeuge")
            if (company != null) {
                root
                    .resolve(Root.FOLDER)
                    .createDirectories()
                    .resolve(Root.SELLER_CONFIG)
                    .writeText(resource("stammdaten.yaml").replace("Muster IT-Consulting UG (haftungsbeschränkt)", company))
            }
            root
                .resolve("2026")
                .createDirectories()
                .resolve("rechnung.yaml")
                .writeText(resource("rechnung.yaml"))
            return root
        }

        test("gesucht wird von der Rechnungsdatei aus, nicht im Arbeitsverzeichnis") {
            // Im Arbeitsverzeichnis des Testlaufs, dem Projekt, liegt keine Wurzel.
            val root = archive("Wurzelfirma UG")
            val fake = FakeTypesetter()

            val result =
                runCli(
                    "erzeuge",
                    root.resolve("2026/rechnung.yaml").toString(),
                    "-o",
                    root.resolve("ziel.pdf").toString(),
                    typesetter = fake,
                )

            result.exitCode shouldBe 0
            fake.sources!!.tex shouldContain "Wurzelfirma UG"
        }

        test("ohne Wurzel bricht der Lauf ab und nennt den Ausweg") {
            val withoutRoot = archive()

            val result = runCli("erzeuge", withoutRoot.resolve("2026/rechnung.yaml").toString())

            result.exitCode shouldBe 1
            result.out shouldBe ""
            result.err shouldContain Root.FOLDER
            result.err shouldContain "-s"
        }
    }
})

private fun resource(name: String) = Path.of("src/test/resources/$name").readText()
