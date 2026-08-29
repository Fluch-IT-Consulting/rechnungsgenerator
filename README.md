# Rechnungsgenerator

Erzeugt aus einer YAML-Beschreibung eine deutsche Rechnung als valides
PDF/A-1b-Dokument – gesetzt mit LuaLaTeX, geprüft mit veraPDF.

Ein Kotlin/Gradle-Lernprojekt.

## Voraussetzungen

Beide Werkzeuge müssen im PATH liegen; das Programm prüft das beim Start und
sagt, welches fehlt.

- TeX Live mit `lualatex` – <https://tug.org/texlive/>
- [veraPDF](https://verapdf.org/) – `brew install verapdf`
- Zum Bauen ein JDK 17+; Gradle lädt das Build-JDK bei Bedarf selbst.

## Verwendung

```
rechnung [OPTIONEN] <rechnung.yaml>

  -s, --stammdaten=<pfad>    Stammdaten des Rechnungsstellers
                             (Vorgabe: stammdaten.yaml)
  -o, --ausgabe=<pfad>       Zieldatei
                             (Vorgabe: aus der Rechnungsnummer gebildet)
  --arbeitsdateien-behalten  Das .tex und die LaTeX-Hilfsdateien nicht löschen
```

Beispiel:

```
./gradlew installDist
./build/install/rechnungsgenerator/bin/rechnungsgenerator rechnung.yaml
```

Ausgabe:

```
Rechnung-R26-042.pdf
PDF/A-1b: gültig
```

Der Exitcode ist 0 nur bei Erfolg. Ein Tippfehler in der YAML, ein LaTeX-Fehler
oder ein PDF, das die PDF/A-Prüfung nicht besteht, führen zu Exitcode 1.

## Eingabedateien

Zwei YAML-Dateien: die Stammdaten (auf jeder Rechnung gleich) und die Rechnung
selbst. Beispiele liegen in [src/test/resources](src/test/resources).

## Entwicklung

```
./gradlew build          # kompiliert, testet, lintet
./gradlew test           # nur Tests
./gradlew ktlintFormat   # Formatierung korrigieren
```

Der Build ruft LuaLaTeX und veraPDF wirklich auf: Ein Integrationstest erzeugt
aus den Beispieldaten ein PDF und prüft es gegen PDF/A-1b. Ohne gültiges PDF/A
ist der Build rot.

## Stand

Layout und Toolchain stehen. Offen sind Reverse Charge (§13b UStG),
Entgeltminderungen und Abschlagsrechnungen sowie – später – ZUGFeRD.
