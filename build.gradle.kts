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
