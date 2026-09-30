// Build-Skript in Kotlin-DSL: deklariert Plugins, Abhängigkeiten und Konfiguration.

plugins {
    alias(libs.plugins.kotlin.jvm) // Kotlin/JVM-Compiler
    alias(libs.plugins.kotlin.serialization) // Compiler-Plugin: erzeugt Serializer für @Serializable-Klassen
    alias(libs.plugins.ktlint) // Linting: ./gradlew ktlintCheck bzw. ktlintFormat
    application // macht das Projekt ausführbar (./gradlew run) und installierbar
}

// Die Nummer, unter der eine Fassung weitergegeben wird: Sie benennt das
// Release-Archiv (rechnungsgenerator-0.1.0.zip) und damit die URL, auf die eine
// Homebrew-Formel zeigt. Ohne sie lässt Gradle den Teil weg, und jedes Archiv
// hieße gleich.
//
// 0.x nach SemVer, obwohl das Programm längst echte Rechnungen schreibt: Seine
// öffentliche Schnittstelle sind die YAML-Schlüssel der Eingabedateien, und die
// sind nicht fertig – "Stand" im README nennt, was noch offen ist, und einmal ist
// ein Schlüssel schon umbenannt worden (bezahlt-am zu vereinnahmt-am). 1.0.0
// sagte zu, dass ein Schlüssel erst in 2.0.0 wieder verschwindet.
//
// Hier und nicht aus dem Git-Tag abgeleitet: Das machte den Build von der
// Repo-Historie abhängig und schlüge in einem Quelltext-Archiv fehl, gegen die
// Reproduzierbarkeit, auf die dieses Projekt sonst achtet. Dass Tag und Zeile
// zusammenpassen, hält seit #269 der Release-Workflow fest – und zwar am
// Namen des gebauten Archivs, denn darauf zeigt eine Homebrew-Formel. Bis
// dahin war es eine Gewohnheit, wie die Bedingungen im Versionskatalog (#253).
version = "0.7.0"

kotlin {
    // Ziel-JDK; Foojay (siehe settings.gradle.kts) lädt es bei Bedarf herunter.
    // 21 ist das aktuelle "sichere" LTS-Ziel – unabhängig vom lokal installierten Java.
    jvmToolchain(21)
}

// veraPDF schreibt sein Validierungsprofil per JAXB in ein final-Feld
// (ValidationProfileImpl.flavour). Ab JDK 24 meldet die JVM das in drei Zeilen
// auf stderr – dieselben drei, die heute schon der verapdf-Unterprozess
// schreibt und die PdfAValidator nur wegschneidet, weil sie vor dem
// XML-Prolog stehen. Die -jakarta-Artefakte (ADR-0023) ändern daran nichts:
// dort wechselt nur der Verursacher.
//
// Was hier unterdrückt wird, ist eine *Ankündigung* und kein Symptom: Wird die
// Änderung in einer künftigen JDK-Fassung scharf geschaltet, läuft veraPDF gar
// nicht mehr – und diese Option hat uns die Vorwarnung genommen. Der Preis
// dafür ist, dass nicht bei jedem Rechnungslauf drei Zeilen Lärm auf stderr
// stehen; dieselbe Abwägung wie bei Mordant (#226).
//
// IgnoreUnrecognizedVMOptions muss davor stehen und deckt hier auch die
// --enable-Option ab: JDK 21 kennt sie nicht und startet sonst nicht.
private val jaxbFinalFields =
    listOf("-XX:+IgnoreUnrecognizedVMOptions", "--enable-final-field-mutation=ALL-UNNAMED")

application {
    mainClass = "de.fluchitconsulting.rechnungsgenerator.MainKt"
    applicationDefaultJvmArgs = jaxbFinalFields
}

// Die Fassung als Ressource neben den Klassen, damit `rechnungsgenerator
// --version` sie zur Laufzeit nennen kann (#264).
//
// Eine Ressource und nicht das Manifest-Attribut Implementation-Version: Das
// liefert null, sobald nicht aus dem JAR heraus gestartet wird – also in jedem
// Test. Geprüft wäre dann nur der Zweig, der im Betrieb nie läuft. Eine
// Ressource liest das Programm überall gleich, und zwar so, wie Template.of
// schon die Vorlagen der Designs liest.
val generateVersionResource =
    tasks.register("generateVersionResource") {
        val target = layout.buildDirectory.dir("generated/version")
        val current = version.toString()
        inputs.property("version", current)
        outputs.dir(target)
        doLast {
            val file = target.get().asFile.resolve("rechnungsgenerator.version")
            file.writeText(current + "\n")
        }
    }

sourceSets {
    main {
        // Die Task selbst als Quelle: Gradle liest daraus das Verzeichnis und
        // die Abhängigkeit, ein eigenes dependsOn braucht es dafür nicht.
        resources.srcDir(generateVersionResource)
    }
}

// Die Namen der ausgelieferten JARs als Test-Ressource, damit ein Test
// THIRD-PARTY.md dagegen halten kann (#268). Dieselbe Bauart wie die
// Versions-Ressource darüber: Was nur Gradle weiß, reicht Gradle als Datei
// weiter, statt dass der Test es nachbaut.
val generateShippedJarList =
    tasks.register("generateShippedJarList") {
        val target = layout.buildDirectory.dir("generated/shipped")
        val jars = configurations.named("runtimeClasspath").map { conf -> conf.files.map { it.name }.sorted() }
        inputs.property("jars", jars)
        outputs.dir(target)
        doLast {
            val file = target.get().asFile.resolve("shipped-jars.txt")
            file.writeText(jars.get().joinToString("\n") + "\n")
        }
    }

sourceSets {
    test {
        resources.srcDir(generateShippedJarList)
    }
}

// Lizenz und Vermerke gehören in das ausgelieferte Archiv und nicht nur ins
// Repository (#269): Apache-2.0 §4 und MPL-2.0 §3.3 verlangen sie bei der
// Weitergabe der Binärfassung, und wer das Zip auspackt, hat das Repository
// nicht. Sie liegen neben bin/ und lib/.
distributions {
    main {
        contents {
            from("LICENSE", "NOTICE", "THIRD-PARTY.md")
        }
    }
}

ktlint {
    // Welche ktlint-CLI läuft, steht hier und nicht beim Plugin: 14.2.0 wählte
    // von sich aus 1.5.0. Die Fassung selbst kommt aus dem Versionskatalog, wie
    // jede andere auch (#159).
    version = libs.versions.ktlintCli.get()
}

// logback steht allein im Klassenpfad von ktlintCheck und ktlintFormat, nie im
// ausgelieferten Programm – ktlint bringt es selbst mit, in einer Fassung des
// 1.3-Zweigs. Der bekommt die Fassungen nicht mehr, die die Advisories aus #158
// nennen (1.5.25, 1.5.33, 1.5.34), deshalb eine gepflegte Reihe erzwungen.
// Welche das gerade ist, hält Dependabot nach: zuerst 1.5.38, mit #165 die
// 1.6-Reihe. Sie verlangt weiterhin nur JDK 11 und bringt SLF4J aus der
// 2.0-Reihe mit – derselben, die auch über ktlint hereinkommt.
configurations.named("ktlint") {
    resolutionStrategy {
        force("ch.qos.logback:logback-classic:1.6.4", "ch.qos.logback:logback-core:1.6.4")
    }
}

// Zwei Funktionen von Mustang, die der Generator nie aufruft: FOP samt Batik
// setzt eine Sichtfassung der E-Rechnung, en16931-cii2ubl wandelt nach UBL.
// Das Blatt setzt LuaLaTeX, und ZUGFeRD ist immer CII. Ausgeschlossen an
// implementation und nicht erst am runtimeClasspath, damit die Tests mit dem
// Klassenpfad laufen, der ausgeliefert wird: Fehlt doch eine Klasse, soll der
// Build rot werden. Am Prüfer selbst wird nichts gekürzt (Nachtrag zu ADR-0014).
configurations.implementation {
    exclude(group = "org.apache.xmlgraphics")
    exclude(group = "com.helger", module = "en16931-cii2ubl")
}

dependencies {
    implementation(libs.kaml)
    implementation(libs.clikt)
    implementation(libs.verapdf) // PDF/A-Prüfung in derselben JVM statt als Unterprozess (#226)
    implementation(libs.mustang.library) // ZUGFeRD: XML ins PDF/A-3u einbetten (ADR-0023)
    implementation(libs.mustang.validator) // ZUGFeRD: das XML gegen Schema und Schematron prüfen
    runtimeOnly(libs.slf4j.nop) // Mustangs Protokoll verschlucken, statt stderr zu füllen

    // veraPDF und Mustang bringen Jackson mit, veraPDF in einer Fassung mit
    // elf Advisories (#230). Die Plattform hebt alle Teile gemeinsam auf die
    // Fassung im Versionskatalog; warum es diese ist, steht dort. Anders als
    // logback oben steht Jackson im ausgelieferten Programm, nicht nur bei
    // ktlint.
    implementation(platform(libs.jackson.bom))

    testImplementation(libs.kotest.runner) // Kotest läuft als JUnit-Platform-Engine
    testImplementation(libs.kotest.assertions) // shouldBe & Co.
    testImplementation(libs.kotest.property) // Property-based Testing (checkAll, Arb)
    testImplementation(libs.konsist) // Architekturtests: liest die Quellen, prüft Paketgrenzen (#112)
    testRuntimeOnly(libs.junit.platform.launcher) // Gradle 9 verlangt den Launcher explizit
}

tasks.test {
    useJUnitPlatform() // JUnit 5/6 statt des alten JUnit-4-Runners
    jvmArgs(jaxbFinalFields) // wie beim ausgelieferten Programm, siehe oben

    // Mehrere Test-JVMs nebeneinander (#223, #434). Die Laufzeit liegt fast
    // vollständig in fünf Klassen, die PDFs mit den echten Werkzeugen setzen:
    // ToolchainIntegrationTest und die vier Klassen des Pflichtangaben-Netzes,
    // rund 5 s je PDF für zwei Läufe LuaLaTeX und die PDF/A-Prüfung. Die 794
    // übrigen Tests kosten zusammen rund 50 s.
    //
    // Gradle verteilt nicht nach Bedarf, sondern im Voraus und reihum: Jede
    // Klassendatei außer den anonymen – auch jeder Helfer, JUnit Platform hat
    // keinen Detektor – geht in alphabetischer Pfadreihenfolge an den nächsten
    // Fork, Klasse k an Fork k mod N, und ein freier Fork übernimmt nichts
    // (MaxNParallelTestDefinitionProcessor, Gradle 9.8). Ob zwei schwere
    // Klassen hintereinander auf einem Fork landen, hängt damit an N und an
    // den Namen der Klassen in generate.typesetting, wo alle fünf liegen –
    // nicht an ihrer Dauer. Eine neue Klasse dort kann die Zuteilung
    // verschieben.
    // Ebenso ein roter Lauf: Klassen, die zuletzt scheiterten, stellt Gradle
    // nach vorn (RunPreviousFailedFirstTestDefinitionProcessor) – gemessen
    // wird deshalb nach einem grünen Lauf, sonst gilt eine andere Zuteilung.
    //
    // 6, gemessen am 28.09.2026 als Dauer der Task test: 296 s mit 4 Forks,
    // 284 s mit 5, 249 s mit 6, 335 s mit 8. Kein N von 4 bis 8 trennt alle
    // fünf; bei 6 teilen sich nur die Arten und die Sonderfälle einen Fork.
    // Mehr Forks machen dafür jede Klasse langsamer, weil sie sich die Kerne
    // teilen – ToolchainIntegrationTest braucht mit 6 Forks 204 s statt 148 s.
    // Die Wanduhr ist der längste Fork, und der ist bei 6 am kürzesten; der
    // volle build braucht damit 3:34 statt 5:01 mit 4 Forks.
    maxParallelForks = 6

    // Damit ein Test nachhalten kann, dass die Fassung, die das Programm nennt,
    // die aus diesem Build ist (#264). Ohne Gradle – etwa im Editor – fehlt die
    // Eigenschaft, und der Test gilt als übersprungen statt als bestanden.
    systemProperty("projectVersion", version)

    // Benannte Abwahl der Tests, die LuaLaTeX, veraPDF und pdftotext brauchen
    // (Kotest-Tag `Toolchain`, #136). CI ruft `./gradlew build -PohneToolchain`
    // auf; lokal ohne den Schalter bleibt build das volle Netz – ohne gültiges
    // PDF/A kein grüner Build.
    if (providers.gradleProperty("ohneToolchain").isPresent) {
        systemProperty("kotest.tags", "!Toolchain")
    }

    // Drei Tests lesen unterhalb von .claude/skills: SkillExamplesTest prüft
    // die YAML-Beispiele des Skills gegen den echten Parser, die beiden
    // Skript-Tests rufen die Python-Skripte wirklich auf. Sie tun das über
    // File(…), also am Eingabesatz dieser Task vorbei – und dann galt sie nach
    // einer reinen Änderung am Skill als UP-TO-DATE und lief nicht, genau bei
    // der Änderung, für die es diese Tests gibt (#235). Lokal grün, in der CI
    // rot, wo der Cache leer ist.
    //
    // RELATIVE: Verglichen werden Pfad und Inhalt, nicht der absolute Ort –
    // sonst wäre jedes Arbeitsverzeichnis ein anderer Eingabesatz.
    inputs
        .files(fileTree(".claude/skills"))
        .withPropertyName("skills")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    // Ebenso THIRD-PARTY.md: ThirdPartyNoticesTest liest sie über File(…), und
    // ohne diese Zeile gälte die Task nach einer Änderung an ihr als
    // UP-TO-DATE – genau bei der Änderung, für die es den Test gibt.
    inputs
        .file("THIRD-PARTY.md")
        .withPropertyName("thirdParty")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
