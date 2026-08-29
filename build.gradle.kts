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
    mainClass = "de.fluchitconsulting.rechnung.MainKt"
}

dependencies {
    implementation(libs.kaml)
    implementation(libs.clikt)

    testImplementation(libs.kotest.runner) // Kotest läuft als JUnit-Platform-Engine
    testImplementation(libs.kotest.assertions) // shouldBe & Co.
    testImplementation(libs.kotest.property) // Property-based Testing (checkAll, Arb)
    testRuntimeOnly(libs.junit.platform.launcher) // Gradle 9 verlangt den Launcher explizit
}

tasks.test {
    useJUnitPlatform() // JUnit 5/6 statt des alten JUnit-4-Runners
}
