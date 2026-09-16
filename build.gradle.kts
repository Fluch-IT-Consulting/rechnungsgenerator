// Build-Skript in Kotlin-DSL: deklariert Plugins, Abhängigkeiten und Konfiguration.

plugins {
    alias(libs.plugins.kotlin.jvm) // Kotlin/JVM-Compiler
    alias(libs.plugins.kotlin.serialization) // Compiler-Plugin: erzeugt Serializer für @Serializable-Klassen
    alias(libs.plugins.ktlint) // Linting: ./gradlew ktlintCheck bzw. ktlintFormat
    application // macht das Projekt ausführbar (./gradlew run) und installierbar
}

kotlin {
    // Ziel-JDK; Foojay (siehe settings.gradle.kts) lädt es bei Bedarf herunter.
    // 21 ist das aktuelle "sichere" LTS-Ziel – unabhängig vom lokal installierten Java.
    jvmToolchain(21)
}

application {
    mainClass = "de.fluchitconsulting.rechnungsgenerator.MainKt"
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

    testImplementation(libs.kotest.runner) // Kotest läuft als JUnit-Platform-Engine
    testImplementation(libs.kotest.assertions) // shouldBe & Co.
    testImplementation(libs.kotest.property) // Property-based Testing (checkAll, Arb)
    testImplementation(libs.konsist) // Architekturtests: liest die Quellen, prüft Paketgrenzen (#112)
    testRuntimeOnly(libs.junit.platform.launcher) // Gradle 9 verlangt den Launcher explizit
}

tasks.test {
    useJUnitPlatform() // JUnit 5/6 statt des alten JUnit-4-Runners
    // Benannte Abwahl der Tests, die LuaLaTeX, veraPDF und pdftotext brauchen
    // (Kotest-Tag `Toolchain`, #136). CI ruft `./gradlew build -PohneToolchain`
    // auf; lokal ohne den Schalter bleibt build das volle Netz – ohne gültiges
    // PDF/A kein grüner Build.
    if (providers.gradleProperty("ohneToolchain").isPresent) {
        systemProperty("kotest.tags", "!Toolchain")
    }
}
