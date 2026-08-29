# Rechnungsgenerator

Erzeugt aus einer YAML-Beschreibung eine deutsche Rechnung als valides
PDF/A-1b-Dokument – gesetzt mit LuaLaTeX, validiert mit veraPDF.

Ein Kotlin/Gradle-Lernprojekt.

## Voraussetzungen

- JDK 17+ (Gradle lädt das Build-JDK selbst)
- TeX Live mit `lualatex` im PATH
- [veraPDF](https://verapdf.org/) im PATH (z. B. `brew install verapdf`)

## Bauen und Ausführen

```
./gradlew build
./gradlew run
```

## Status

In Entwicklung – aktuell entsteht das Projektfundament.
