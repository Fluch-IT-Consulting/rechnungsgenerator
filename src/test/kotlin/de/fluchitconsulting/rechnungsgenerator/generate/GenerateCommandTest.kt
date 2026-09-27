package de.fluchitconsulting.rechnungsgenerator.generate

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import de.fluchitconsulting.rechnungsgenerator.RechnungsgeneratorCommand
import de.fluchitconsulting.rechnungsgenerator.core.model.Root
import de.fluchitconsulting.rechnungsgenerator.generate.render.Foundation
import de.fluchitconsulting.rechnungsgenerator.generate.render.Template
import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.FakeTypesetter
import de.fluchitconsulting.rechnungsgenerator.generate.toolchain.Typesetter
import de.fluchitconsulting.rechnungsgenerator.runCli
import de.fluchitconsulting.rechnungsgenerator.setup.SetupCommand
import de.fluchitconsulting.rechnungsgenerator.toolchain.BuiltInPdfAValidator
import de.fluchitconsulting.rechnungsgenerator.toolchain.ExternalPdfAValidator
import de.fluchitconsulting.rechnungsgenerator.toolchain.PdfAValidator
import de.fluchitconsulting.rechnungsgenerator.toolchain.ToolchainException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainInOrder
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

/**
 * Prüft, was zwischen Einlesen und Ergebnis passiert: kopieren, ausgeben,
 * aufräumen, Exitcode setzen.
 *
 * Ohne die Naht [Typesetter] setzte jeder Lauf LuaLaTeX und veraPDF voraus.
 * Hinter ihr steht hier ein Adapter, der ein vorbereitetes PDF und ein
 * vorbereitetes Prüfergebnis liefert – der Weg davor bleibt echt, nur die
 * beiden fremden Programme sind ersetzt.
 *
 * Aufgerufen wird über das Elternkommando, so wie ein Mensch es tut:
 * `rechnungsgenerator erzeuge …`. Die Ausgabe lässt sich trotzdem dort
 * abgreifen, denn ein Unterkommando erbt `echoMessage` von seinem Elternteil.
 */
class GenerateCommandTest : FunSpec({

    val inputDir = createTempDirectory("kommando-test")
    val invoice = inputDir.resolve("rechnung.yaml").also { it.writeText(resource("rechnung.yaml")) }
    val sellerConfig = inputDir.resolve("stammdaten.yaml").also { it.writeText(resource("stammdaten.yaml")) }

    /**
     * Führt das Kommando aus und sammelt, was es ausgegeben hätte.
     *
     * Der Viewer ist immer ein Fake, auch wo ein Test ihn nicht nennt: Ein
     * `--oeffnen` soll im Build nie ein Fenster aufmachen. [output] lässt sich
     * hineinreichen, damit ein Test sie schon während des Laufs sieht.
     */
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

    /**
     * Welcher Prüfer prüft, entscheidet der Schalter – und das lässt sich
     * sehen, ohne dass ein PDF entsteht: Das Kommando reicht den Prüfer an
     * die Fabrik weiter, und die gehört dem Test (#226).
     */
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

        // Nicht bloß "ein anderer": Dieser Prüfer ruft das installierte
        // Programm auf, und genau das ist der Sinn des Schalters.
        test("mit --externe-pdfa-pruefung prüft das Programm verapdf") {
            chosen("--externe-pdfa-pruefung").shouldBeInstanceOf<ExternalPdfAValidator>()
        }
    }

    /**
     * Der Schalter ändert die Quellen und mit ihnen das Profil, sonst nichts
     * (ADR-0023): dasselbe Ziel, derselbe Überschreibschutz, dieselben
     * Ausgaben.
     */
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

        // Die Schlussrechnung stand als letzte Art aus; seitdem kennt der
        // Writer keinen Abbruch mehr.
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

        // Der Adapter hat sich gemerkt, wo gearbeitet wurde – sonst wüsste der
        // Test es nicht, denn das Kommando nennt das Verzeichnis nur mit
        // --arbeitsdateien-behalten.
        fake.workingDir!!.exists() shouldBe false
    }

    test("mit --arbeitsdateien-behalten bleibt es stehen und wird genannt") {
        val fake = FakeTypesetter()

        val output = generate(fake, "-o", inputDir.resolve("bleibt.pdf").toString(), "--arbeitsdateien-behalten")

        fake.workingDir!!.exists() shouldBe true
        output.last() shouldContain "Arbeitsdateien:"
    }

    test("ein nicht beschreibbares Ziel nennt die Zieldatei, und nur einmal") {
        // NoSuchFileException trägt in message nur den Pfad, und der stünde
        // sonst ein zweites Mal hinter dem Doppelpunkt.
        val target = inputDir.resolve("gibt-es-nicht").resolve("ergebnis.pdf")

        val error = shouldThrow<CliktError> { generate(FakeTypesetter(), "-o", target.toString()) }

        error.message shouldBe "Die Datei $target konnte nicht geschrieben werden."
    }

    /**
     * erzeuge und einrichten schreiben den Satz ab, statt ihn zu teilen – die
     * Begründung steht in GenerateCommand. Dieser Test hält die Abschriften
     * gleich: Beide scheitern an derselben Ursache, und bis auf den Pfad muss
     * dieselbe Meldung herauskommen (#327).
     *
     * Die Ursache ist ein Ort unter einer Datei statt unter einem Ordner. Das
     * braucht keine Dateirechte, die ein Testlauf als root überginge, und es
     * liefert einen Grund, also auch den Teil hinter dem Doppelpunkt.
     */
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

    /**
     * "Nicht geschrieben" meint die Zieldatei und nichts sonst (#300). Was
     * im Satz scheitert, kommt über die Naht als ToolchainException; eine
     * IOException von dort bricht deren Vertrag und geht unverändert durch,
     * statt als Schreibfehler auf die falsche Stelle zu zeigen.
     */
    test("eine IOException aus dem Satz wird nicht zum Schreibfehler") {
        val error =
            shouldThrow<IOException> {
                generate(FakeTypesetter(ioFailure = "kein Platz im Temp-Verzeichnis"), "-o", inputDir.resolve("nie.pdf").toString())
            }

        error.message shouldBe "kein Platz im Temp-Verzeichnis"
    }

    test("ein ungültiges PDF/A ist ein Fehler der Werkzeuge") {
        // Den Exitcode und den Kanal prüft MainTest: Übersetzt wird die
        // Ausnahme in execute(), einmal für alle Kommandos (#119).
        val error =
            shouldThrow<ToolchainException> {
                generate(FakeTypesetter(valid = false), "-o", inputDir.resolve("ungueltig.pdf").toString())
            }

        error.message!! shouldContain "kein gültiges PDF/A-1b"
    }

    test("auch ein ungültiges PDF/A lässt kein Arbeitsverzeichnis zurück") {
        val fake = FakeTypesetter(valid = false)

        shouldThrow<ToolchainException> { generate(fake, "-o", inputDir.resolve("ungueltig2.pdf").toString()) }

        // Das Aufräumen steht in einem finally – hier wird es geprüft.
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
        // Die Verstöße gehören zum Fehlschlag und stehen deshalb in der
        // Meldung, also auf stderr – der Kanal folgt dem Exitcode.
        error.message!! shouldContain "6.1.2"
    }

    test("eine vorhandene Zieldatei bleibt ohne --ueberschreiben unangetastet") {
        val target = inputDir.resolve("vorhanden.pdf").also { it.writeText("das verschickte Original") }
        val fake = FakeTypesetter()

        val error = shouldThrow<CliktError> { generate(fake, "-o", target.toString()) }

        error.statusCode shouldBe 1
        error.message!! shouldContain "--ueberschreiben"
        target.readText() shouldBe "das verschickte Original"
        // Geprüft wird vor dem Satz: Ein LuaLaTeX-Lauf, dessen Ergebnis
        // nirgends hin darf, wäre vergeudet.
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

    /**
     * Die Vorgabe hängt nicht am Standort des Aufrufs – deshalb wechselt
     * hier kein Test das Arbeitsverzeichnis. Jeder bekommt ein eigenes
     * Verzeichnis, denn ohne -o entscheidet der Name der Rechnungsdatei, wo
     * das PDF entsteht.
     */
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
            // Der Jobname bleibt, was er war; der Name des PDF hängt nicht an ihm.
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
            // Relativ zum Arbeitsverzeichnis des Testlaufs, also mit ../ bis ins
            // Temp-Verzeichnis – aber relativ, und so bleibt es.
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

    /**
     * `--ueberschreiben` ist fürs Nachbessern eines Entwurfs da. Die Eingabe
     * zu zerstören ist kein Nachbessern – deshalb gilt der Schalter hier nicht.
     */
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

    /**
     * Die Metadaten gehen denselben Weg wie der Quelltext (#202). Merkte der
     * Fake sie sich nicht, belegte allein der Lauf mit den echten Werkzeugen,
     * dass das Kommando sie überhaupt weiterreicht – ohne sie gibt es kein
     * gültiges PDF/A, aber das sagt erst veraPDF.
     */
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
            // Die vorhandene Datei kann die verschickte Rechnung sein – nicht
            // die, die gerade aus der Rechnungsdatei entstehen sollte.
            val target = inputDir.resolve("vorhanden-oeffnen.pdf").also { it.writeText("das verschickte Original") }
            val viewer = FakePdfViewer()

            shouldThrow<CliktError> { generate(FakeTypesetter(), "-o", target.toString(), "--oeffnen", viewer = viewer) }

            viewer.opened shouldBe emptyList()
        }

        test("scheitert das Öffnen, bleibt es ein Erfolg – mit einem Hinweis als letzter Zeile") {
            val target = inputDir.resolve("viewer-fehlt.pdf")
            val viewer = FakePdfViewer(result = ViewerResult.Failed("open endete mit Exitcode 1."))

            // Hier zählt die Reihenfolge: Der Hinweis steht als letzte Zeile,
            // also nach dem Ergebnis. Dass der Lauf dabei wirklich mit Exitcode 0
            // endet und der Hinweis auf stdout landet, liest MainTest (#153) –
            // von hier aus wäre es nur aus "keine Ausnahme" geraten.
            val output = generate(FakeTypesetter(), "-o", target.toString(), "--oeffnen", viewer = viewer)

            target.exists() shouldBe true
            output.last() shouldBe "Hinweis: Das PDF konnte nicht geöffnet werden: open endete mit Exitcode 1."
        }
    }

    context("logo (#198)") {
        // Stammdaten mit Logo in einem eigenen Verzeichnis, das Logo daneben.
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
            // Im Protokoll von LuaLaTeX heißt es nur logo.tex; welche Datei
            // das war, weiß allein das Kommando.
            val error =
                shouldThrow<ToolchainException> {
                    generate(
                        FakeTypesetter(failure = "lualatex konnte Rechnung-R26-042.tex nicht übersetzen"),
                        "-o",
                        target("logo-kaputt"),
                        sellerConfigFile = sellerConfigWithLogo("prag"),
                    )
                }

            error.message!! shouldContain "nicht übersetzen"
            error.message!! shouldContain "Die Stammdaten nennen ein Logo (${logo.toAbsolutePath()})"
        }

        test("auch ein ungültiges PDF/A nennt das Logo, denn es kann daran liegen") {
            // Etwa eine Deckkraft im Fragment: Transparenz verbietet PDF/A-1.
            val error =
                shouldThrow<ToolchainException> {
                    generate(FakeTypesetter(valid = false), "-o", target("logo-ungueltig"), sellerConfigFile = sellerConfigWithLogo("prag"))
                }

            error.message!! shouldContain "kein gültiges PDF/A-1b"
            error.message!! shouldContain "Die Stammdaten nennen ein Logo"
        }

        test("ein ungültiges XML der E-Rechnung nennt das Logo nicht, denn daran kann es nicht liegen") {
            val error =
                shouldThrow<ToolchainException> {
                    generate(
                        FakeTypesetter(einvoiceFailure = "Das XML der E-Rechnung ist ungültig."),
                        "--zugferd",
                        "-o",
                        target("logo-xml-ungueltig"),
                        sellerConfigFile = sellerConfigWithLogo("prag"),
                    )
                }

            error.message!! shouldContain "Das XML der E-Rechnung ist ungültig."
            error.message!! shouldNotContain "Logo"
        }

        test("ohne Logo in den Stammdaten nennt die Meldung keines") {
            val error =
                shouldThrow<ToolchainException> {
                    generate(
                        FakeTypesetter(failure = "lualatex konnte Rechnung-R26-042.tex nicht übersetzen"),
                        "-o",
                        target("ohne-logo-kaputt"),
                    )
                }

            error.message!! shouldNotContain "Logo"
        }
    }

    test("die Hinweise für die eigene Akte stehen nach dem Ergebnis, nicht davor") {
        // Eine Rechnung ohne Umsatzsteuer an einen Kunden in Österreich, deren
        // Bestätigungsabfrage nur einfach war: Der Calculator gibt dazu einen
        // Hinweis aus – er betrifft die eigene Ablage, nicht den Kunden, und
        // soll deshalb nicht wie ein Fehler oben stehen.
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

    context("ohne -s kommen die Stammdaten aus der Wurzel (ADR-0015)") {
        /**
         * Eine Ablage mit einer Rechnung im Jahresordner – mit Wurzel, wenn
         * [company] genannt ist. Der Name steht später im Satz und sagt, welche
         * Stammdaten wirklich gelesen wurden.
         */
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
            // Das Arbeitsverzeichnis des Testlaufs ist das Projekt, und dort
            // liegt keine Wurzel: Was hier gefunden wird, kann nur die über
            // der Rechnungsdatei sein.
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
