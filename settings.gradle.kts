// Einstiegspunkt für Gradle: definiert, welche Projekte zum Build gehören.
// Bei uns ein Einzelprojekt – Multi-Module käme hier per include(...) dazu.

plugins {
    // Lädt bei Bedarf automatisch ein passendes JDK herunter (siehe jvmToolchain in build.gradle.kts).
    // So baut das Projekt reproduzierbar, egal welches Java lokal installiert ist.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Zentrale Repository-Deklaration für alle (Sub-)Projekte – moderner Gradle-Stil,
// statt repositories {} in jedem build.gradle.kts zu wiederholen.
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "rechnungsgenerator"
