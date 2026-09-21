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
// sind nicht fertig – "Stand" im README nennt drei offene Punkte, und einmal ist
// ein Schlüssel schon umbenannt worden (bezahlt-am zu vereinnahmt-am). 1.0.0
// sagte zu, dass ein Schlüssel erst in 2.0.0 wieder verschwindet.
//
// Hier und nicht aus dem Git-Tag abgeleitet: Das machte den Build von der
// Repo-Historie abhängig und schlüge in einem Quelltext-Archiv fehl, gegen die
// Reproduzierbarkeit, auf die dieses Projekt sonst achtet. Dass Tag und Zeile
// zusammenpassen, ist damit eine Gewohnheit und kein Netz – dieselbe Lage wie
// bei den Bedingungen im Versionskatalog (#253). Bleibt sie stecken, ist der
// nächste Schritt ein Test, der diese Zeile gegen den Tag liest.
version = "0.1.0"

kotlin {
    // Ziel-JDK; Foojay (siehe settings.gradle.kts) lädt es bei Bedarf herunter.
    // 21 ist das aktuelle "sichere" LTS-Ziel – unabhängig vom lokal installierten Java.
    jvmToolchain(21)
}

// veraPDF schreibt sein Validierungsprofil per JAXB in ein final-Feld
// (ValidationProfileImpl.flavour). Ab JDK 24 meldet die JVM das in drei Zeilen
// auf stderr – dieselben drei, die heute schon der verapdf-Unterprozess
// schreibt und die PdfAValidator nur wegschneidet, weil sie vor dem
// XML-Prolog stehen. Die -jakarta-Artefakte helfen nicht: dort wechselt nur
// der Verursacher.
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
// 1.6-Reihe. Sie verlangt weiterhin nur JDK 11 und bringt SLF4J 2.0.18 mit –
// dieselbe Reihe, die auch über ktlint hereinkommt.
configurations.named("ktlint") {
    resolutionStrategy {
        force("ch.qos.logback:logback-classic:1.6.3", "ch.qos.logback:logback-core:1.6.3")
    }
}

dependencies {
    implementation(libs.kaml)
    implementation(libs.clikt)
    implementation(libs.verapdf) // PDF/A-Prüfung in derselben JVM statt als Unterprozess (#226)

    // veraPDF bringt Jackson 2.21.1 mit, und darauf liegen elf Advisories –
    // zwei davon verlangen 2.21.5. Angehoben wird auf die neueste Fassung
    // derselben Reihe, gegen die veraPDF gebaut ist; 2.22 wäre ein Minor-Sprung
    // in einer Bibliothek, die wir selbst nicht aufrufen. Anders als logback
    // unten steht Jackson im ausgelieferten Programm, nicht nur bei ktlint.
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

    // Mehrere Test-JVMs nebeneinander (#223). Die Laufzeit liegt fast
    // vollständig in zwei Klassen: PflichtangabenTest und
    // ToolchainIntegrationTest setzen zusammen rund 29 PDFs, und je PDF
    // braucht veraPDF allein 6,6 s – CPU-Arbeit, an der von außen nichts zu
    // holen ist. Die 501 übrigen Tests kosten zusammen 6 s.
    //
    // 4 und nicht 2: Bei 2 Forks teilen sich die beiden schweren Klassen die
    // Maschine noch mit den leichten und werden messbar langsamer (247 s
    // gesamt ohne Forks, 186 s mit zweien, 147 s mit vieren). Ab 4 sind die
    // leichten Klassen nach Sekunden durch, und die Wanduhr ist die längere
    // der beiden schweren – mehr Forks ändern daran nichts, weil Gradle
    // innerhalb einer Klasse nicht teilt.
    maxParallelForks = 4

    // Benannte Abwahl der Tests, die LuaLaTeX, veraPDF und pdftotext brauchen
    // (Kotest-Tag `Toolchain`, #136). CI ruft `./gradlew build -PohneToolchain`
    // auf; lokal ohne den Schalter bleibt build das volle Netz – ohne gültiges
    // PDF/A kein grüner Build.
    if (providers.gradleProperty("ohneToolchain").isPresent) {
        systemProperty("kotest.tags", "!Toolchain")
    }

    // Drei Tests lesen unterhalb von .claude/skills: SkillBeispieleTest prüft
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
}
